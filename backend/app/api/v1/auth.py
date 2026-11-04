from datetime import datetime
from typing import Annotated, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from pydantic import BaseModel, Field, SecretStr
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.dependencies import (
    get_current_identity,
    get_current_session,
    get_database_session,
)
from app.auth.principal import VerifiedIdentity
from app.auth.sessions import (
    AccountUnavailable,
    SessionPrincipal,
    SessionTokenPair,
    authenticate_access_token,
    create_auth_session,
    revoke_auth_session,
    rotate_refresh_token,
)

router = APIRouter(prefix="/auth", tags=["authentication"])


class IdentityResponse(BaseModel):
    issuer: str
    subject: str
    expires_at: int


class SessionResponse(BaseModel):
    access_token: str
    token_type: Literal["Bearer"] = "Bearer"
    expires_in: int
    refresh_token: str
    session_expires_at: datetime
    user_id: UUID
    tenant_id: UUID


class RefreshRequest(BaseModel):
    refresh_token: SecretStr = Field(min_length=32, max_length=128)


class CurrentAccountResponse(BaseModel):
    user_id: UUID
    tenant_id: UUID
    session_id: UUID


def _session_response(pair: SessionTokenPair) -> SessionResponse:
    return SessionResponse(
        access_token=pair.access_token,
        expires_in=pair.expires_in,
        refresh_token=pair.refresh_token,
        session_expires_at=pair.session_expires_at,
        user_id=pair.user_id,
        tenant_id=pair.tenant_id,
    )


@router.get("/identity", response_model=IdentityResponse, summary="Verify external API identity")
async def read_identity(
    identity: Annotated[VerifiedIdentity, Depends(get_current_identity)],
) -> IdentityResponse:
    """Return only the minimum identity fields after external token verification."""
    return IdentityResponse(
        issuer=identity.issuer,
        subject=identity.subject,
        expires_at=identity.expires_at,
    )


@router.post(
    "/session",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Exchange a verified OIDC identity for an AgentDock session",
)
async def create_session(
    identity: Annotated[VerifiedIdentity, Depends(get_current_identity)],
    db: Annotated[AsyncSession, Depends(get_database_session)],
    request: Request,
) -> SessionResponse:
    try:
        pair = await create_auth_session(db, identity, request.app.state.settings)
    except AccountUnavailable:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This AgentDock account is unavailable.",
        ) from None
    return _session_response(pair)


@router.post(
    "/session/refresh",
    response_model=SessionResponse,
    summary="Rotate an AgentDock refresh token",
)
async def refresh_session(
    payload: RefreshRequest,
    db: Annotated[AsyncSession, Depends(get_database_session)],
    request: Request,
) -> SessionResponse:
    pair = await rotate_refresh_token(
        db,
        payload.refresh_token.get_secret_value(),
        request.app.state.settings,
    )
    if pair is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return _session_response(pair)


@router.get("/me", response_model=CurrentAccountResponse, summary="Read current AgentDock session")
async def read_current_account(
    principal: Annotated[SessionPrincipal, Depends(get_current_session)],
) -> CurrentAccountResponse:
    return CurrentAccountResponse(
        user_id=principal.user_id,
        tenant_id=principal.tenant_id,
        session_id=principal.session_id,
    )


@router.post(
    "/session/logout",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Revoke the current AgentDock session",
)
async def logout_session(
    principal: Annotated[SessionPrincipal, Depends(get_current_session)],
    db: Annotated[AsyncSession, Depends(get_database_session)],
) -> Response:
    # Authentication performs a read transaction. End it before starting the
    # independent revocation transaction on the shared request-scoped session.
    await db.rollback()
    await revoke_auth_session(db, principal)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
