from __future__ import annotations

from types import SimpleNamespace
from typing import Any

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient
from jwt.exceptions import PyJWKClientConnectionError
from pydantic import ValidationError

from app.core.config import Settings
from app.main import create_app

ISSUER = "https://identity.example.test/"
AUDIENCE = "https://api.agentdock.example"


class FakeJwksClient:
    def __init__(self, public_key: Any, error: Exception | None = None) -> None:
        self.public_key = public_key
        self.error = error

    def get_signing_key_from_jwt(self, token: str):
        if self.error is not None:
            raise self.error
        return SimpleNamespace(key=self.public_key, algorithm_name="RS256")


@pytest.fixture(scope="module")
def signing_key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


@pytest.fixture
def oidc_settings() -> Settings:
    return Settings(
        environment="test",
        oidc_issuer=ISSUER,
        oidc_audience=AUDIENCE,
        oidc_jwks_url="https://identity.example.test/.well-known/jwks.json",
        oidc_allowed_algorithms=["RS256"],
        oidc_clock_skew_seconds=0,
    )


def make_token(signing_key, **overrides: Any) -> str:
    claims = {
        "iss": ISSUER,
        "sub": "provider-user-123",
        "aud": AUDIENCE,
        "iat": 1_700_000_000,
        "exp": 1_900_000_600,
        "email": "private@example.test",
        "roles": ["should-not-be-returned"],
    }
    claims.update(overrides)
    return jwt.encode(
        claims,
        signing_key,
        algorithm="RS256",
        headers={"kid": "test-key-1"},
    )


def build_client(settings: Settings, signing_key, jwks_error: Exception | None = None) -> TestClient:
    app = create_app(settings)
    app.state.oidc_jwks_client = FakeJwksClient(signing_key.public_key(), jwks_error)
    return TestClient(app)


def test_valid_access_token_returns_minimal_identity(signing_key, oidc_settings) -> None:
    token = make_token(signing_key)
    with build_client(oidc_settings, signing_key) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 200
    assert response.json() == {
        "issuer": ISSUER,
        "subject": "provider-user-123",
        "expires_at": 1_900_000_600,
    }
    assert "private@example.test" not in response.text
    assert "should-not-be-returned" not in response.text


def test_missing_bearer_token_is_rejected(oidc_settings, signing_key) -> None:
    with build_client(oidc_settings, signing_key) as client:
        response = client.get("/api/v1/auth/identity")

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


@pytest.mark.parametrize(
    "overrides",
    [
        {"iss": "https://attacker.example/"},
        {"aud": "another-api"},
        {"sub": ""},
        {"exp": 1_700_000_000},
    ],
)
def test_invalid_claims_are_rejected(signing_key, oidc_settings, overrides) -> None:
    token = make_token(signing_key, **overrides)
    with build_client(oidc_settings, signing_key) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid or missing bearer token."}


def test_oversized_bearer_token_is_rejected(signing_key, oidc_settings) -> None:
    with build_client(oidc_settings, signing_key) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": "Bearer " + ("a" * 16_385)},
        )
    assert response.status_code == 401


def test_unsupported_algorithm_is_rejected_before_key_lookup(oidc_settings) -> None:
    token = jwt.encode(
        {
            "iss": ISSUER,
            "sub": "provider-user-123",
            "aud": AUDIENCE,
            "iat": 1_900_000_000,
            "exp": 1_900_000_600,
        },
        "not-a-real-provider-secret",
        algorithm="HS256",
        headers={"kid": "test-key-1"},
    )
    client = TestClient(create_app(oidc_settings))
    response = client.get(
        "/api/v1/auth/identity",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401


def test_jwks_network_failure_returns_retryable_503(signing_key, oidc_settings) -> None:
    with build_client(
        oidc_settings,
        signing_key,
        PyJWKClientConnectionError("private jwks network detail"),
    ) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": f"Bearer {make_token(signing_key)}"},
        )

    assert response.status_code == 503
    assert response.json() == {"detail": "Identity provider is temporarily unavailable."}
    assert "private jwks network detail" not in response.text


def test_missing_oidc_configuration_fails_closed() -> None:
    with TestClient(create_app(Settings(environment="test"))) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": "Bearer arbitrary-token"},
        )

    assert response.status_code == 503
    assert response.json() == {"detail": "Authentication is not configured."}


def test_partial_oidc_configuration_is_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(environment="test", oidc_issuer=ISSUER)


def test_oidc_urls_require_https() -> None:
    with pytest.raises(ValidationError):
        Settings(
            environment="test",
            oidc_issuer="http://identity.example.test/",
            oidc_audience=AUDIENCE,
            oidc_jwks_url="https://identity.example.test/jwks.json",
        )


def test_authorized_party_is_checked_when_configured(signing_key, oidc_settings) -> None:
    configured = oidc_settings.model_copy(update={"oidc_authorized_party": "agentdock-android"})
    token = make_token(signing_key, azp="unexpected-client")
    with build_client(configured, signing_key) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": f"Bearer {token}"},
        )

    assert response.status_code == 401


def test_signing_key_algorithm_must_match_token_header(signing_key, oidc_settings) -> None:
    class MismatchedAlgorithmClient:
        def get_signing_key_from_jwt(self, token: str):
            return SimpleNamespace(
                key=signing_key.public_key(),
                algorithm_name="ES256",
            )

    app = create_app(oidc_settings)
    app.state.oidc_jwks_client = MismatchedAlgorithmClient()
    with TestClient(app) as client:
        response = client.get(
            "/api/v1/auth/identity",
            headers={"Authorization": f"Bearer {make_token(signing_key)}"},
        )
    assert response.status_code == 401
