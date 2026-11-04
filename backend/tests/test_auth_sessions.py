from __future__ import annotations

import asyncio
from hashlib import sha256
import os
import time
from types import SimpleNamespace
from uuid import UUID, uuid4

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from app.core.config import Settings
from app.main import create_app

ISSUER = "https://identity.agentdock.test/"
AUDIENCE = "https://api.agentdock.test"
ADMIN_DATABASE_URL = os.getenv("AGENTDOCK_TEST_ADMIN_DATABASE_URL")
RUNTIME_DATABASE_URL = os.getenv("AGENTDOCK_TEST_DATABASE_URL")

pytestmark = pytest.mark.skipif(
    not (ADMIN_DATABASE_URL and RUNTIME_DATABASE_URL),
    reason="PostgreSQL integration URLs are not configured; CI always configures them.",
)


class FakeJwksClient:
    def __init__(self, public_key) -> None:
        self.public_key = public_key

    def get_signing_key_from_jwt(self, token: str):
        return SimpleNamespace(key=self.public_key, algorithm_name="RS256")


def _external_access_token(private_key, subject: str) -> str:
    now = int(time.time())
    return jwt.encode(
        {
            "iss": ISSUER,
            "sub": subject,
            "aud": AUDIENCE,
            "iat": now,
            "exp": now + 600,
            "client_id": "agentdock-test-client",
            "jti": uuid4().hex,
        },
        private_key,
        algorithm="RS256",
        headers={"kid": "test-key-1", "typ": "at+jwt"},
    )


def test_identity_provisioning_rotating_refresh_and_logout() -> None:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = f"session-test-{uuid4().hex}"
    external_token = _external_access_token(private_key, subject)
    settings = Settings(
        environment="test",
        database_url=RUNTIME_DATABASE_URL,
        oidc_issuer=ISSUER,
        oidc_audience=AUDIENCE,
        oidc_jwks_url="https://identity.agentdock.test/.well-known/jwks.json",
        oidc_allowed_algorithms=["RS256"],
        oidc_clock_skew_seconds=0,
    )
    app = create_app(settings)
    app.state.oidc_jwks_client = FakeJwksClient(private_key.public_key())

    user_id: UUID | None = None
    tenant_id: UUID | None = None
    admin_engine = create_async_engine(ADMIN_DATABASE_URL, pool_pre_ping=True)

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/api/v1/auth/session",
                headers={"Authorization": f"Bearer {external_token}"},
            )
            assert first_response.status_code == 201, first_response.text
            first = first_response.json()
            user_id = UUID(first["user_id"])
            tenant_id = UUID(first["tenant_id"])

            second_response = client.post(
                "/api/v1/auth/session",
                headers={"Authorization": f"Bearer {external_token}"},
            )
            assert second_response.status_code == 201, second_response.text
            second = second_response.json()
            assert second["user_id"] == first["user_id"]
            assert second["tenant_id"] == first["tenant_id"]
            assert second["session_expires_at"] == first["session_expires_at"]

            account_response = client.get(
                "/api/v1/auth/me",
                headers={"Authorization": f"Bearer {first['access_token']}"},
            )
            assert account_response.status_code == 200
            assert account_response.json() == {
                "user_id": first["user_id"],
                "tenant_id": first["tenant_id"],
                "session_id": first["session_id"],
            }

            async def inspect_first_session() -> None:
                async with admin_engine.connect() as connection:
                    hashes = (
                        await connection.execute(
                            text(
                                "SELECT token_hash FROM session_tokens "
                                "WHERE session_id = :session_id"
                            ),
                            {"session_id": first["session_id"]},
                        )
                    ).scalars().all()
                    identity_count = (
                        await connection.execute(
                            text(
                                "SELECT count(*) FROM auth_identities "
                                "WHERE issuer = :issuer AND subject = :subject"
                            ),
                            {"issuer": ISSUER, "subject": subject},
                        )
                    ).scalar_one()
                    membership_count = (
                        await connection.execute(
                            text(
                                "SELECT count(*) FROM tenant_memberships "
                                "WHERE tenant_id = :tenant_id AND user_id = :user_id"
                            ),
                            {"tenant_id": tenant_id, "user_id": user_id},
                        )
                    ).scalar_one()
                    email = (
                        await connection.execute(
                            text("SELECT email FROM users WHERE id = :user_id"),
                            {"user_id": user_id},
                        )
                    ).scalar_one()
                assert len(hashes) == 2
                assert sha256(first["access_token"].encode("utf-8")).hexdigest() in hashes
                assert sha256(first["refresh_token"].encode("utf-8")).hexdigest() in hashes
                assert first["access_token"] not in hashes
                assert first["refresh_token"] not in hashes
                assert identity_count == 1
                assert membership_count == 1
                assert email is None

            asyncio.run(inspect_first_session())

            rotated_response = client.post(
                "/api/v1/auth/session/refresh",
                json={"refresh_token": first["refresh_token"]},
            )
            assert rotated_response.status_code == 200, rotated_response.text
            rotated = rotated_response.json()
            assert rotated["session_id"] == first["session_id"]
            assert rotated["refresh_token"] != first["refresh_token"]

            # Refresh rotation invalidates the previous access token immediately.
            old_access = client.get(
                "/api/v1/auth/me",
                headers={"Authorization": f"Bearer {first['access_token']}"},
            )
            assert old_access.status_code == 401

            # Reuse of a rotated refresh token revokes the whole session family.
            replay = client.post(
                "/api/v1/auth/session/refresh",
                json={"refresh_token": first["refresh_token"]},
            )
            assert replay.status_code == 401
            revoked_access = client.get(
                "/api/v1/auth/me",
                headers={"Authorization": f"Bearer {rotated['access_token']}"},
            )
            assert revoked_access.status_code == 401

            logout_session = client.post(
                "/api/v1/auth/session",
                headers={"Authorization": f"Bearer {external_token}"},
            )
            assert logout_session.status_code == 201, logout_session.text
            logout_tokens = logout_session.json()
            logout_response = client.post(
                "/api/v1/auth/session/logout",
                headers={"Authorization": f"Bearer {logout_tokens['access_token']}"},
            )
            assert logout_response.status_code == 204
            after_logout = client.get(
                "/api/v1/auth/me",
                headers={"Authorization": f"Bearer {logout_tokens['access_token']}"},
            )
            assert after_logout.status_code == 401
    finally:
        if tenant_id is not None and user_id is not None:
            async def cleanup() -> None:
                async with admin_engine.begin() as connection:
                    await connection.execute(
                        text("DELETE FROM tenants WHERE id = :tenant_id"),
                        {"tenant_id": tenant_id},
                    )
                    await connection.execute(
                        text("DELETE FROM users WHERE id = :user_id"),
                        {"user_id": user_id},
                    )
            asyncio.run(cleanup())
        asyncio.run(admin_engine.dispose())
