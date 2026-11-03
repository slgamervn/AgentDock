from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class VerifiedIdentity:
    """Minimal identity data accepted after external access-token verification."""

    issuer: str
    subject: str
    expires_at: int
