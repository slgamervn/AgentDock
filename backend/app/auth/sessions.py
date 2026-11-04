from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from hashlib import sha256
import secrets
from uuid import UUID, uuid4

from sqlalchemy import select, text, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.principal import VerifiedIdentity
from app.core.config import Settings
from app.db.models import (
    AuthIdentity,
    SessionToken,
    Tenant,
    TenantMembership,
    User,
    UserSession,
)
from app.db.session import set_tenant_context


@dataclass(frozen=True, slots=True)
class SessionTokenPair:
    access_token: str
    refresh_token: str
    expires_in: int
    session_expires_at: datetime
    user_id: UUID
    tenant_id: UUID
    session_id: UUID


@dataclass(frozen=True, slots=True)
class SessionPrincipal:
    user_id: UUID
    tenant_id: UUID
    session_id: UUID


class AccountUnavailable(Exception):
    """The verified identity maps to an account that cannot be used."""


def _token_hash(token: str) -> str:
    return sha256(token.encode("ascii")).hexdigest()


async def _resolve_or_create_account(
    db: AsyncSession,
    identity: VerifiedIdentity,
) -> tuple[User, UUID]:
    # Serialize first-login provisioning for this issuer/subject pair.
    # The unique constraint remains the final integrity boundary.
    lock_key = f"{identity.issuer}\x1f{identity.subject}"
    await db.execute(
        text("SELECT pg_advisory_xact_lock(hashtextextended(:identity_key, 0))"),
        {"identity_key": lock_key},
    )

    mapped_identity = await db.scalar(
        select(AuthIdentity).where(
            AuthIdentity.issuer == identity.issuer,
            AuthIdentity.subject == identity.subject,
        )
    )
    now = datetime.now(timezone.utc)

    if mapped_identity is not None:
        user = await db.get(User, mapped_identity.user_id)
        if user is None or not user.is_active:
            raise AccountUnavailable
        mapped_identity.last_authenticated_at = now
        return user, mapped_identity.default_tenant_id

    user = User(email=None)
    db.add(user)
    await db.flush()

    tenant_id = uuid4()
    tenant = Tenant(
        id=tenant_id,
        slug=f"personal-{tenant_id.hex[:24]}",
        name="Personal workspace",
    )
    db.add(tenant)
    await db.flush()

    # RLS on tenant_memberships fails closed unless this trusted transaction
    # establishes the new workspace scope before creating its owner record.
    await set_tenant_context(db, tenant_id)
    db.add(
        TenantMembership(
            tenant_id=tenant_id,
            user_id=user.id,
            role="owner",
        )
    )
    db.add(
        AuthIdentity(
            issuer=identity.issuer,
            subject=identity.subject,
            user_id=user.id,
            default_tenant_id=tenant_id,
            last_authenticated_at=now,
        )
    )
    await db.flush()
    return user, tenant_id


async def _issue_token_pair(
    db: AsyncSession,
    session_record: UserSession,
    settings: Settings,
    now: datetime,
) -> SessionTokenPair:
    access_token = secrets.token_urlsafe(32)
    refresh_token = secrets.token_urlsafe(48)
    access_expires_at = min(
        now + timedelta(minutes=settings.session_access_token_minutes),
        session_record.expires_at,
    )

    db.add_all(
        [
            SessionToken(
                session_id=session_record.id,
                token_hash=_token_hash(access_token),
                token_type="ACCESS",
                expires_at=access_expires_at,
            ),
            SessionToken(
                session_id=session_record.id,
                token_hash=_token_hash(refresh_token),
                token_type="REFRESH",
                expires_at=session_record.expires_at,
            ),
        ]
    )
    await db.flush()

    return SessionTokenPair(
        access_token=access_token,
        refresh_token=refresh_token,
        expires_in=max(0, int((access_expires_at - now).total_seconds())),
        session_expires_at=session_record.expires_at,
        user_id=session_record.user_id,
        tenant_id=session_record.tenant_id,
        session_id=session_record.id,
    )


async def create_auth_session(
    db: AsyncSession,
    identity: VerifiedIdentity,
    settings: Settings,
) -> SessionTokenPair:
    now = datetime.now(timezone.utc)
    async with db.begin():
        user, tenant_id = await _resolve_or_create_account(db, identity)
        session_record = UserSession(
            id=uuid4(),
            user_id=user.id,
            tenant_id=tenant_id,
            expires_at=now + timedelta(days=settings.session_max_lifetime_days),
        )
        db.add(session_record)
        await db.flush()
        pair = await _issue_token_pair(db, session_record, settings, now)
    return pair


async def _revoke_session(
    db: AsyncSession,
    session_record: UserSession,
    now: datetime,
) -> None:
    if session_record.revoked_at is None:
        session_record.revoked_at = now
    await db.execute(
        update(SessionToken)
        .where(
            SessionToken.session_id == session_record.id,
            SessionToken.revoked_at.is_(None),
        )
        .values(revoked_at=now)
    )


async def rotate_refresh_token(
    db: AsyncSession,
    refresh_token: str,
    settings: Settings,
) -> SessionTokenPair | None:
    now = datetime.now(timezone.utc)
    async with db.begin():
        token_record = await db.scalar(
            select(SessionToken)
            .where(SessionToken.token_hash == _token_hash(refresh_token))
            .with_for_update()
        )
        if token_record is None or token_record.token_type != "REFRESH":
            return None

        session_record = await db.scalar(
            select(UserSession)
            .where(UserSession.id == token_record.session_id)
            .with_for_update()
        )
        if session_record is None:
            return None

        # A previously rotated refresh token is evidence of replay. Revoke the
        # entire session family, including the latest access and refresh tokens.
        if token_record.used_at is not None:
            await _revoke_session(db, session_record, now)
            return None

        if (
            token_record.revoked_at is not None
            or token_record.expires_at <= now
            or session_record.revoked_at is not None
            or session_record.expires_at <= now
        ):
            await _revoke_session(db, session_record, now)
            return None

        user = await db.get(User, session_record.user_id)
        if user is None or not user.is_active:
            await _revoke_session(db, session_record, now)
            return None

        token_record.used_at = now
        token_record.revoked_at = now
        await db.execute(
            update(SessionToken)
            .where(
                SessionToken.session_id == session_record.id,
                SessionToken.token_type == "ACCESS",
                SessionToken.revoked_at.is_(None),
            )
            .values(revoked_at=now)
        )
        pair = await _issue_token_pair(db, session_record, settings, now)
    return pair


async def authenticate_access_token(
    db: AsyncSession,
    access_token: str,
) -> SessionPrincipal | None:
    now = datetime.now(timezone.utc)
    session_record = await db.scalar(
        select(UserSession)
        .join(SessionToken, SessionToken.session_id == UserSession.id)
        .join(User, User.id == UserSession.user_id)
        .where(
            SessionToken.token_hash == _token_hash(access_token),
            SessionToken.token_type == "ACCESS",
            SessionToken.expires_at > now,
            SessionToken.revoked_at.is_(None),
            UserSession.expires_at > now,
            UserSession.revoked_at.is_(None),
            User.is_active.is_(True),
        )
    )
    if session_record is None:
        return None
    return SessionPrincipal(
        user_id=session_record.user_id,
        tenant_id=session_record.tenant_id,
        session_id=session_record.id,
    )


async def revoke_auth_session(
    db: AsyncSession,
    principal: SessionPrincipal,
) -> None:
    now = datetime.now(timezone.utc)
    async with db.begin():
        session_record = await db.scalar(
            select(UserSession)
            .where(
                UserSession.id == principal.session_id,
                UserSession.user_id == principal.user_id,
            )
            .with_for_update()
        )
        if session_record is not None:
            await _revoke_session(db, session_record, now)
