from functools import lru_cache
from typing import Literal
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


OIDCAlgorithm = Literal["RS256", "ES256", "EdDSA"]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AGENTDOCK_",
        env_ignore_empty=True,
        extra="ignore",
    )

    service_name: str = "AgentDock API"
    app_version: str = "0.2.0"
    environment: Literal["development", "test", "staging", "production"] = "development"
    database_url: SecretStr | None = None

    # Configure all three values together. The audience must identify this API,
    # not merely the Android OAuth client ID.
    oidc_issuer: str | None = None
    oidc_audience: str | None = None
    oidc_jwks_url: str | None = None
    oidc_authorized_party: str | None = None
    oidc_allowed_algorithms: list[OIDCAlgorithm] = Field(default_factory=lambda: ["RS256"])
    oidc_clock_skew_seconds: int = Field(default=30, ge=0, le=120)
    oidc_jwks_timeout_seconds: int = Field(default=5, ge=1, le=10)
    session_access_token_minutes: int = Field(default=15, ge=5, le=60)
    session_max_lifetime_days: int = Field(default=30, ge=1, le=90)

    @property
    def oidc_enabled(self) -> bool:
        return all((self.oidc_issuer, self.oidc_audience, self.oidc_jwks_url))

    @model_validator(mode="after")
    def validate_oidc_configuration(self) -> "Settings":
        configured = (self.oidc_issuer, self.oidc_audience, self.oidc_jwks_url)
        if any(configured) and not all(configured):
            raise ValueError("OIDC_ISSUER, OIDC_AUDIENCE and OIDC_JWKS_URL must be configured together.")
        if self.oidc_authorized_party and not self.oidc_enabled:
            raise ValueError("OIDC_AUTHORIZED_PARTY requires complete OIDC configuration.")
        if self.oidc_enabled:
            for label, value in (("issuer", self.oidc_issuer), ("JWKS URL", self.oidc_jwks_url)):
                parsed = urlsplit(value or "")
                if (
                    parsed.scheme != "https"
                    or not parsed.hostname
                    or parsed.username is not None
                    or parsed.password is not None
                    or parsed.query
                    or parsed.fragment
                ):
                    raise ValueError(f"OIDC {label} must be an absolute HTTPS URL without credentials, query strings or fragments.")
            if not (self.oidc_audience or "").strip():
                raise ValueError("OIDC_AUDIENCE must not be empty.")
            if not self.oidc_allowed_algorithms:
                raise ValueError("At least one supported OIDC signing algorithm must be configured.")
        return self


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
