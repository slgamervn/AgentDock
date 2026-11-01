from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app


def test_liveness_returns_service_metadata() -> None:
    app = create_app(
        Settings(
            service_name="AgentDock Test API",
            app_version="9.8.7",
            environment="test",
        )
    )

    response = TestClient(app).get("/api/v1/health/live")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "AgentDock Test API",
        "version": "9.8.7",
    }


def test_liveness_does_not_expose_environment_or_secrets() -> None:
    app = create_app(
        Settings(
            service_name="AgentDock API",
            app_version="0.1.0",
            environment="test",
        )
    )

    payload = TestClient(app).get("/api/v1/health/live").json()

    assert "environment" not in payload
    assert "database_url" not in payload
    assert "secret" not in payload


def test_api_docs_are_disabled_in_production() -> None:
    app = create_app(Settings(environment="production"))
    client = TestClient(app)

    assert client.get("/docs").status_code == 404
    assert client.get("/redoc").status_code == 404
    assert client.get("/openapi.json").status_code == 404
    assert client.get("/api/v1/health/live").status_code == 200
