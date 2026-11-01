from typing import Literal

from fastapi import APIRouter, Request
from pydantic import BaseModel

router = APIRouter(prefix="/health", tags=["health"])


class LivenessResponse(BaseModel):
    status: Literal["ok"]
    service: str
    version: str


@router.get("/live", response_model=LivenessResponse, summary="Process liveness probe")
async def liveness(request: Request) -> LivenessResponse:
    settings = request.app.state.settings
    return LivenessResponse(
        status="ok",
        service=settings.service_name,
        version=settings.app_version,
    )
