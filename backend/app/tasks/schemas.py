from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class TaskCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=200)
    prompt: str = Field(min_length=1, max_length=32_000)

    @field_validator("title")
    @classmethod
    def normalize_title(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("Title must not be blank.")
        return normalized

    @field_validator("prompt")
    @classmethod
    def validate_prompt(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Prompt must not be blank.")
        return value


class TaskSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    title: str
    status: str
    created_at: datetime
    updated_at: datetime


class TaskRead(TaskSummary):
    prompt: str
    attributes: dict[str, Any]


class TaskListResponse(BaseModel):
    items: list[TaskSummary]
    next_cursor: str | None = None


class TaskEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    sequence: int
    event_type: str
    payload: dict[str, Any]
    occurred_at: datetime


class TaskEventsPage(BaseModel):
    items: list[TaskEventRead]
    next_after_sequence: int | None = None
