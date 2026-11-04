from __future__ import annotations

import asyncio
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

ISSUER = "https://identity.tasks.agentdock.test/"
AUDIENCE = "https://api.agentdock.tasks.test"
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


async def _cleanup_identity(subject: str) -> None:
    engine = create_async_engine(ADMIN_DATABASE_URL, pool_pre_ping=True)
    try:
        async with engine.begin() as connection:
            mapped = (
                await connection.execute(
                    text(
                        "SELECT user_id, default_tenant_id FROM auth_identities "
                        "WHERE issuer = :issuer AND subject = :subject"
                    ),
                    {"issuer": ISSUER, "subject": subject},
                )
            ).first()
            if mapped is not None:
                await connection.execute(
                    text("DELETE FROM tenants WHERE id = :tenant_id"),
                    {"tenant_id": mapped.default_tenant_id},
                )
                await connection.execute(
                    text("DELETE FROM users WHERE id = :user_id"),
                    {"user_id": mapped.user_id},
                )
    finally:
        await engine.dispose()


@pytest.fixture
def signed_in_client():
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = f"task-test-{uuid4().hex}"
    external_token = _external_access_token(private_key, subject)
    settings = Settings(
        environment="test",
        database_url=RUNTIME_DATABASE_URL,
        oidc_issuer=ISSUER,
        oidc_audience=AUDIENCE,
        oidc_jwks_url="https://identity.tasks.agentdock.test/.well-known/jwks.json",
        oidc_allowed_algorithms=["RS256"],
        oidc_allowed_client_ids=["agentdock-test-client"],
        oidc_clock_skew_seconds=0,
    )
    app = create_app(settings)
    app.state.oidc_jwks_client = FakeJwksClient(private_key.public_key())

    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/auth/session",
                headers={"Authorization": f"Bearer {external_token}"},
            )
            assert response.status_code == 201, response.text
            yield {
                "client": client,
                "headers": {
                    "Authorization": f"Bearer {response.json()['access_token']}"
                },
                "user_id": response.json()["user_id"],
                "tenant_id": response.json()["tenant_id"],
                "private_key": private_key,
                "subject": subject,
            }
    finally:
        asyncio.run(_cleanup_identity(subject))


def test_create_task_is_idempotent_and_persists_ordered_creation_event(signed_in_client) -> None:
    client = signed_in_client["client"]
    headers = {
        **signed_in_client["headers"],
        "Idempotency-Key": f"task-create-{uuid4().hex}",
    }
    body = {"title": "Prepare release checklist", "prompt": "Review the release gates."}

    created = client.post("/api/v1/tasks", headers=headers, json=body)
    assert created.status_code == 201, created.text
    assert created.headers["cache-control"] == "no-store"
    assert created.headers["pragma"] == "no-cache"
    task = created.json()
    assert task["title"] == body["title"]
    assert task["prompt"] == body["prompt"]
    assert task["status"] == "QUEUED"

    replay = client.post("/api/v1/tasks", headers=headers, json=body)
    assert replay.status_code == 201, replay.text
    assert replay.json()["id"] == task["id"]

    conflict = client.post(
        "/api/v1/tasks",
        headers=headers,
        json={"title": "Different task", "prompt": "This is not the same request."},
    )
    assert conflict.status_code == 409

    detail = client.get(
        f"/api/v1/tasks/{task['id']}",
        headers=signed_in_client["headers"],
    )
    assert detail.status_code == 200
    assert detail.json()["prompt"] == body["prompt"]
    assert detail.headers["cache-control"] == "no-store"

    events = client.get(
        f"/api/v1/tasks/{task['id']}/events",
        headers=signed_in_client["headers"],
    )
    assert events.status_code == 200
    assert events.json()["items"] == [
        {
            "id": events.json()["items"][0]["id"],
            "sequence": 1,
            "event_type": "TASK_CREATED",
            "payload": {"status": "QUEUED"},
            "occurred_at": events.json()["items"][0]["occurred_at"],
        }
    ]


def test_task_list_uses_stable_cursor_pagination(signed_in_client) -> None:
    client = signed_in_client["client"]
    for index in range(3):
        response = client.post(
            "/api/v1/tasks",
            headers={
                **signed_in_client["headers"],
                "Idempotency-Key": f"page-{index}-{uuid4().hex}",
            },
            json={
                "title": f"Task {index}",
                "prompt": f"Prompt {index}",
            },
        )
        assert response.status_code == 201, response.text

    first_page = client.get(
        "/api/v1/tasks?limit=2",
        headers=signed_in_client["headers"],
    )
    assert first_page.status_code == 200
    first = first_page.json()
    assert len(first["items"]) == 2
    assert first["next_cursor"]

    second_page = client.get(
        "/api/v1/tasks",
        params={"limit": 2, "cursor": first["next_cursor"]},
        headers=signed_in_client["headers"],
    )
    assert second_page.status_code == 200
    second = second_page.json()
    assert len(second["items"]) == 1
    assert second["next_cursor"] is None

    first_ids = {item["id"] for item in first["items"]}
    second_ids = {item["id"] for item in second["items"]}
    assert first_ids.isdisjoint(second_ids)


def test_tasks_are_invisible_to_another_workspace(signed_in_client) -> None:
    client = signed_in_client["client"]
    created = client.post(
        "/api/v1/tasks",
        headers={
            **signed_in_client["headers"],
            "Idempotency-Key": f"private-task-{uuid4().hex}",
        },
        json={"title": "Private task", "prompt": "This belongs to account one."},
    )
    assert created.status_code == 201
    task_id = created.json()["id"]

    second_subject = f"other-account-{uuid4().hex}"
    second_token = _external_access_token(
        signed_in_client["private_key"],
        second_subject,
    )
    try:
        second_session = client.post(
            "/api/v1/auth/session",
            headers={"Authorization": f"Bearer {second_token}"},
        )
        assert second_session.status_code == 201, second_session.text
        second_headers = {
            "Authorization": f"Bearer {second_session.json()['access_token']}"
        }

        hidden = client.get(f"/api/v1/tasks/{task_id}", headers=second_headers)
        assert hidden.status_code == 404
        second_list = client.get("/api/v1/tasks", headers=second_headers)
        assert second_list.status_code == 200
        assert second_list.json()["items"] == []
    finally:
        asyncio.run(_cleanup_identity(second_subject))


def test_revoked_workspace_membership_blocks_task_access(signed_in_client) -> None:
    client = signed_in_client["client"]

    async def revoke_membership() -> None:
        engine = create_async_engine(ADMIN_DATABASE_URL, pool_pre_ping=True)
        try:
            async with engine.begin() as connection:
                await connection.execute(
                    text(
                        "DELETE FROM tenant_memberships "
                        "WHERE tenant_id = :tenant_id AND user_id = :user_id"
                    ),
                    {
                        "tenant_id": signed_in_client["tenant_id"],
                        "user_id": signed_in_client["user_id"],
                    },
                )
        finally:
            await engine.dispose()

    asyncio.run(revoke_membership())

    response = client.get(
        "/api/v1/tasks",
        headers=signed_in_client["headers"],
    )
    assert response.status_code == 403
