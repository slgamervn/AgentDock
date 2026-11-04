from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.sessions import SessionPrincipal, authenticate_access_token
from app.db.models import TenantMembership
from app.db.session import set_tenant_context

_bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True, slots=True)
class TenantRequestContext:
    db: AsyncSession
    principal: SessionPrincipal


async def get_current_tenant_context(
    request: Request,
    credentials: Annotated[
        HTTPAuthorizationCredentials | None,
        Depends(_bearer_scheme),
    ],
) -> AsyncIterator[TenantRequestContext]:
    factory = request.app.state.database_session_factory
    if factory is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database is not configured.",
        )
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing bearer token.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    async with factory() as auth_db:
        principal = await authenticate_access_token(auth_db, credentials.credentials)
    if principal is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing bearer token.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    async with factory() as db:
        async with db.begin():
            # The session tenant is only a candidate scope. RLS is activated
            # before membership lookup; tenant data is not queried until the
            # authenticated user is confirmed as an active member.
            await set_tenant_context(db, principal.tenant_id)
            membership_id = await db.scalar(
                select(TenantMembership.id).where(
                    TenantMembership.tenant_id == principal.tenant_id,
                    TenantMembership.user_id == principal.user_id,
                )
            )
            if membership_id is None:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Active workspace membership is required.",
                )
            yield TenantRequestContext(db=db, principal=principal)
