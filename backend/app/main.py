from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.v1.auth import router as auth_router
from app.api.v1.health import router as health_router
from app.auth.oidc import create_jwks_client
from app.core.config import Settings, get_settings
from app.db.session import create_database_engine, create_session_factory


def create_app(settings: Settings | None = None) -> FastAPI:
    configuration = settings or get_settings()
    is_production = configuration.environment == "production"

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        engine = None
        if configuration.database_url is not None:
            engine = create_database_engine(configuration.database_url.get_secret_value())
        application.state.database_engine = engine
        application.state.database_session_factory = (
            create_session_factory(engine) if engine is not None else None
        )
        try:
            yield
        finally:
            if engine is not None:
                await engine.dispose()

    application = FastAPI(
        title=configuration.service_name,
        version=configuration.app_version,
        docs_url=None if is_production else "/docs",
        redoc_url=None if is_production else "/redoc",
        openapi_url=None if is_production else "/openapi.json",
        lifespan=lifespan,
    )
    application.state.settings = configuration
    application.state.database_engine = None
    application.state.database_session_factory = None
    application.state.oidc_jwks_client = create_jwks_client(configuration)
    application.include_router(health_router, prefix="/api/v1")
    application.include_router(auth_router, prefix="/api/v1")
    return application


app = create_app()
