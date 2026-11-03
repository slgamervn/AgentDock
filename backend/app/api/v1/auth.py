from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.auth.dependencies import get_current_identity
from app.auth.principal import VerifiedIdentity

router = APIRouter(prefix="/auth", tags=["authentication"])


class IdentityResponse(BaseModel):
    issuer: str
    subject: str
    expires_at: int


@router.get("/identity", response_model=IdentityResponse, summary="Verify current API identity")
async def read_identity(
    identity: Annotated[VerifiedIdentity, Depends(get_current_identity)],
) -> IdentityResponse:
    """Return only the minimum identity fields after token verification."""
    return IdentityResponse(
        issuer=identity.issuer,
        subject=identity.subject,
        expires_at=identity.expires_at,
    )
