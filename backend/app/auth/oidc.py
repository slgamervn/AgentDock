from __future__ import annotations

from typing import Protocol

import jwt
from fastapi.concurrency import run_in_threadpool
from jwt.exceptions import (
    PyJWKClientConnectionError,
    PyJWTError,
)

from app.auth.principal import VerifiedIdentity
from app.core.config import Settings


class SigningKeyClient(Protocol):
    def get_signing_key_from_jwt(self, token: str): ...


class InvalidOIDCToken(Exception):
    """The token is malformed, untrusted, expired or otherwise invalid."""


class OIDCProviderUnavailable(Exception):
    """The trusted JWKS endpoint could not be reached in time."""


def create_jwks_client(settings: Settings) -> jwt.PyJWKClient | None:
    if not settings.oidc_enabled:
        return None

    assert settings.oidc_jwks_url is not None
    return jwt.PyJWKClient(
        settings.oidc_jwks_url,
        cache_jwk_set=True,
        lifespan=300,
        timeout=settings.oidc_jwks_timeout_seconds,
        cooldown_duration=30,
    )


def _verify_access_token_sync(
    token: str,
    settings: Settings,
    jwks_client: SigningKeyClient,
) -> VerifiedIdentity:
    if not settings.oidc_enabled:
        raise InvalidOIDCToken("OIDC is not configured")
    if not token or len(token) > 16_384:
        raise InvalidOIDCToken("Bearer token size is invalid")

    try:
        header = jwt.get_unverified_header(token)
        token_type = header.get("typ")
        if not isinstance(token_type, str) or token_type.lower() not in {
            "at+jwt",
            "application/at+jwt",
        }:
            raise InvalidOIDCToken("JWT is not a supported OAuth access-token type")

        algorithm = header.get("alg")
        key_id = header.get("kid")
        if not isinstance(algorithm, str) or algorithm not in settings.oidc_allowed_algorithms:
            raise InvalidOIDCToken("Unsupported signing algorithm")
        if not isinstance(key_id, str) or not key_id or len(key_id) > 256:
            raise InvalidOIDCToken("Missing or invalid key identifier")

        signing_key = jwks_client.get_signing_key_from_jwt(token)
        key_algorithm = getattr(signing_key, "algorithm_name", None)
        if key_algorithm is not None and key_algorithm != algorithm:
            raise InvalidOIDCToken("Signing key algorithm mismatch")

        claims = jwt.decode(
            token,
            key=signing_key.key,
            algorithms=list(settings.oidc_allowed_algorithms),
            audience=settings.oidc_audience,
            issuer=settings.oidc_issuer,
            leeway=settings.oidc_clock_skew_seconds,
            options={"require": ["exp", "iat", "iss", "sub", "aud", "client_id", "jti"]},
        )
    except PyJWKClientConnectionError as error:
        raise OIDCProviderUnavailable from error
    except (PyJWTError, TypeError, ValueError) as error:
        raise InvalidOIDCToken from error

    subject = claims.get("sub")
    issuer = claims.get("iss")
    expires_at = claims.get("exp")
    if not isinstance(subject, str) or not subject or len(subject) > 255:
        raise InvalidOIDCToken("Invalid subject")
    if not isinstance(issuer, str) or issuer != settings.oidc_issuer:
        raise InvalidOIDCToken("Invalid issuer")
    if isinstance(expires_at, bool) or not isinstance(expires_at, (int, float)):
        raise InvalidOIDCToken("Invalid expiration")

    client_id = claims.get("client_id")
    if (
        not isinstance(client_id, str)
        or not client_id
        or len(client_id) > 255
        or client_id not in settings.oidc_allowed_client_ids
    ):
        raise InvalidOIDCToken("Invalid client ID")
    token_id = claims.get("jti")
    if not isinstance(token_id, str) or not token_id or len(token_id) > 255:
        raise InvalidOIDCToken("Invalid token identifier")

    authorized_party = settings.oidc_authorized_party
    token_authorized_party = claims.get("azp")
    if authorized_party is not None and token_authorized_party != authorized_party:
        raise InvalidOIDCToken("Invalid authorized party")

    return VerifiedIdentity(
        issuer=issuer,
        subject=subject,
        expires_at=int(expires_at),
    )


async def verify_access_token(
    token: str,
    settings: Settings,
    jwks_client: SigningKeyClient | None,
) -> VerifiedIdentity:
    if not settings.oidc_enabled or jwks_client is None:
        raise OIDCProviderUnavailable

    try:
        return await run_in_threadpool(
            _verify_access_token_sync,
            token,
            settings,
            jwks_client,
        )
    except OIDCProviderUnavailable:
        raise
    except InvalidOIDCToken:
        raise
    except (TimeoutError, OSError) as error:
        raise OIDCProviderUnavailable from error
