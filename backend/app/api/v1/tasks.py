from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.sessions import SessionPrincipal
from app.auth.dependencies import get_current_session
from app.tasks.schemas import TaskCreate, TaskEventsPage, TaskListResponse, TaskRead
from app.tasks.service import (
    IdempotencyConflict,
    InvalidTaskCursorError,
    TaskNotFound,
    create_task_idempotently,
    get_task,
    list_task_events,
    list_tasks,
)
from app.tenancy.dependencies import get_current_tenant_database_session

router = APIRouter(prefix="/tasks", tags=["tasks"])
TenantDatabase = Annotated[
    AsyncSession,
    Depends(get_current_tenant_database_session, scope="function"),
]
CurrentPrincipal = Annotated[SessionPrincipal, Depends(get_current_session)]


def _prevent_caching(response: Response) -> None:
    response.headers["Cache-Control"] = "no-store"
    response.headers["Pragma"] = "no-cache"


@router.post(
    "",
    response_model=TaskRead,
    status_code=status.HTTP_201_CREATED,
    summary="Create a durable task",
)
async def create_task(
    payload: TaskCreate,
    db: TenantDatabase,
    principal: CurrentPrincipal,
    response: Response,
    idempotency_key: Annotated[
        str,
        Header(alias="Idempotency-Key", min_length=8, max_length=128),
    ],
) -> TaskRead:
    _prevent_caching(response)
    try:
        task, _was_replayed = await create_task_idempotently(
            db, principal, payload, idempotency_key
        )
    except IdempotencyConflict:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="The idempotency key is invalid, expired or was used for a different request.",
        ) from None
    return task


@router.get(
    "",
    response_model=TaskListResponse,
    summary="List tasks in the current workspace",
)
async def read_tasks(
    db: TenantDatabase,
    principal: CurrentPrincipal,
    response: Response,
    limit: Annotated[int, Query(ge=1, le=100)] = 25,
    cursor: Annotated[str | None, Query(max_length=256)] = None,
) -> TaskListResponse:
    _prevent_caching(response)
    try:
        return await list_tasks(db, principal.tenant_id, limit, cursor)
    except InvalidTaskCursorError:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Invalid task pagination cursor.",
        ) from None


@router.get(
    "/{task_id}",
    response_model=TaskRead,
    summary="Read one task in the current workspace",
)
async def read_task(
    task_id: UUID,
    db: TenantDatabase,
    principal: CurrentPrincipal,
    response: Response,
) -> TaskRead:
    _prevent_caching(response)
    try:
        return await get_task(db, principal.tenant_id, task_id)
    except TaskNotFound:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Task not found.",
        ) from None


@router.get(
    "/{task_id}/events",
    response_model=TaskEventsPage,
    summary="Read ordered task events after a sequence",
)
async def read_task_events(
    task_id: UUID,
    db: TenantDatabase,
    principal: CurrentPrincipal,
    response: Response,
    after_sequence: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=200)] = 100,
) -> TaskEventsPage:
    _prevent_caching(response)
    try:
        return await list_task_events(
            db,
            principal.tenant_id,
            task_id,
            after_sequence,
            limit,
        )
    except TaskNotFound:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Task not found.",
        ) from None
