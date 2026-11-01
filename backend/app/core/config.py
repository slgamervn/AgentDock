from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AGENTDOCK_",
        env_ignore_empty=True,
        extra="ignore",
    )

    service_name: str = "AgentDock API"
    app_version: str = "0.1.0"
    environment: Literal["development", "test", "staging", "production"] = "development"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
