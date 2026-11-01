from fastapi import FastAPI

from app.api.v1.health import router as health_router
from app.core.config import Settings, get_settings


def create_app(settings: Settings | None = None) -> FastAPI:
    configuration = settings or get_settings()
    is_production = configuration.environment == "production"
    application = FastAPI(
        title=configuration.service_name,
        version=configuration.app_version,
        docs_url=None if is_production else "/docs",
        redoc_url=None if is_production else "/redoc",
        openapi_url=None if is_production else "/openapi.json",
    )
    application.state.settings = configuration
    application.include_router(health_router, prefix="/api/v1")
    return application


app = create_app()
