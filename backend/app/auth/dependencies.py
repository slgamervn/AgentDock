from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.oidc import InvalidOIDCToken, OIDCProviderUnavailable, verify_access_token
from app.auth.principal import VerifiedIdentity
from app.auth.sessions import SessionPrincipal, authenticate_access_token

_bearer_scheme = HTTPBearer(auto_error=False)


def _unauthorized() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or missing bearer token.",
        headers={"WWW-Authenticate": "Bearer"},
    )


async def get_database_session(request: Request) -> AsyncIterator[AsyncSession]:
    factory = request.app.state.database_session_factory
    if factory is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database is not configured.",
        )
    async with factory() as db:
        yield db


async def get_current_identity(
    request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)],
) -> VerifiedIdentity:
    settings = request.app.state.settings
    if not settings.oidc_enabled:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication is not configured.",
        )
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise _unauthorized()

    try:
        return await verify_access_token(
            credentials.credentials,
            settings,
            request.app.state.oidc_jwks_client,
        )
    except InvalidOIDCToken:
        raise _unauthorized() from None
    except OIDCProviderUnavailable:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Identity provider is temporarily unavailable.",
        ) from None


async def get_current_session(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)],
    db: Annotated[AsyncSession, Depends(get_database_session)],
) -> SessionPrincipal:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise _unauthorized()
    principal = await authenticate_access_token(db, credentials.credentials)
    if principal is None:
        raise _unauthorized()
    return principal
