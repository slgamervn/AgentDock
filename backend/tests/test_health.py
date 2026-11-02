from fastapi.testclient import TestClient
from sqlalchemy.exc import SQLAlchemyError

from app.core.config import Settings
from app.main import create_app


class _FakeConnection:
    def __init__(self, error: Exception | None = None) -> None:
        self.error = error

    async def __aenter__(self) -> "_FakeConnection":
        return self

    async def __aexit__(self, exc_type, exc, traceback) -> None:
        return None

    async def execute(self, statement) -> None:
        if self.error is not None:
            raise self.error


class _FakeEngine:
    def __init__(self, error: Exception | None = None) -> None:
        self.error = error

    def connect(self) -> _FakeConnection:
        return _FakeConnection(self.error)


def test_liveness_returns_service_metadata() -> None:
    app = create_app(
        Settings(
            service_name="AgentDock Test API",
            app_version="9.8.7",
            environment="test",
            database_url=None,
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
            database_url=None,
        )
    )

    payload = TestClient(app).get("/api/v1/health/live").json()

    assert "environment" not in payload
    assert "database_url" not in payload
    assert "secret" not in payload


def test_readiness_returns_503_when_database_is_not_configured() -> None:
    app = create_app(Settings(environment="test", database_url=None))

    with TestClient(app) as client:
        response = client.get("/api/v1/health/ready")

    assert response.status_code == 503
    assert response.json() == {"detail": "Database is not configured."}


def test_readiness_returns_ready_when_database_query_succeeds() -> None:
    app = create_app(Settings(environment="test", database_url=None))

    with TestClient(app) as client:
        app.state.database_engine = _FakeEngine()
        response = client.get("/api/v1/health/ready")

    assert response.status_code == 200
    assert response.json() == {"status": "ready", "database": "reachable"}


def test_readiness_does_not_expose_database_errors() -> None:
    app = create_app(Settings(environment="test", database_url=None))

    with TestClient(app) as client:
        app.state.database_engine = _FakeEngine(
            SQLAlchemyError("connection failed: private-database-password")
        )
        response = client.get("/api/v1/health/ready")

    assert response.status_code == 503
    assert response.json() == {"detail": "Database is unavailable."}
    assert "private-database-password" not in response.text


def test_api_docs_are_disabled_in_production() -> None:
    app = create_app(Settings(environment="production"))
    client = TestClient(app)

    assert client.get("/docs").status_code == 404
    assert client.get("/redoc").status_code == 404
    assert client.get("/openapi.json").status_code == 404
    assert client.get("/api/v1/health/live").status_code == 200


def test_readiness_returns_503_for_connection_timeout_and_network_errors() -> None:
    for error in (
        TimeoutError("connection timed out"),
        OSError("network is unavailable"),
    ):
        app = create_app(Settings(environment="test", database_url=None))

        with TestClient(app) as client:
            app.state.database_engine = _FakeEngine(error)
            response = client.get("/api/v1/health/ready")

        assert response.status_code == 503
        assert response.json() == {"detail": "Database is unavailable."}
