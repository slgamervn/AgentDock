from __future__ import annotations

from datetime import datetime, timedelta, timezone
import hashlib
import json
from uuid import UUID

from sqlalchemy import select, tuple_
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.sessions import SessionPrincipal
from app.db.models import IdempotencyRecord, Task, TaskEvent
from app.tasks.cursor import decode_task_cursor, encode_task_cursor
from app.tasks.schemas import (
    TaskCreate,
    TaskEventRead,
    TaskEventsPage,
    TaskListResponse,
    TaskRead,
    TaskSummary,
)


class IdempotencyConflict(Exception):
    """A previously used idempotency key cannot be reused for this request."""


class TaskNotFound(Exception):
    """The task does not exist in the authenticated workspace."""


def _request_hash(payload: TaskCreate) -> str:
    canonical = json.dumps(
        payload.model_dump(mode="json"),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


async def _insert_idempotency_record(
    db: AsyncSession,
    principal: SessionPrincipal,
    key: str,
    request_hash: str,
    now: datetime,
) -> UUID | None:
    statement = (
        pg_insert(IdempotencyRecord)
        .values(
            tenant_id=principal.tenant_id,
            actor_user_id=principal.user_id,
            idempotency_key=key,
            request_hash=request_hash,
            state="IN_PROGRESS",
            expires_at=now + timedelta(hours=24),
        )
        .on_conflict_do_nothing(constraint="uq_idempotency_tenant_actor_key")
        .returning(IdempotencyRecord.id)
    )
    return await db.scalar(statement)


async def create_task_idempotently(
    db: AsyncSession,
    principal: SessionPrincipal,
    payload: TaskCreate,
    idempotency_key: str,
) -> tuple[TaskRead, bool]:
    key = idempotency_key.strip()
    if len(key) < 8 or len(key) > 128 or any(ord(char) < 0x21 or ord(char) > 0x7E for char in key):
        raise IdempotencyConflict

    now = datetime.now(timezone.utc)
    request_hash = _request_hash(payload)
    record_id = await _insert_idempotency_record(
        db, principal, key, request_hash, now
    )

    if record_id is None:
        record = await db.scalar(
            select(IdempotencyRecord)
            .where(
                IdempotencyRecord.tenant_id == principal.tenant_id,
                IdempotencyRecord.actor_user_id == principal.user_id,
                IdempotencyRecord.idempotency_key == key,
            )
            .with_for_update()
        )
        if record is None:
            # This should only occur during an unexpected concurrent cleanup.
            raise IdempotencyConflict

        if record.expires_at <= now:
            await db.delete(record)
            await db.flush()
            record_id = await _insert_idempotency_record(
                db, principal, key, request_hash, now
            )
            if record_id is None:
                raise IdempotencyConflict
        else:
            if record.request_hash != request_hash:
                raise IdempotencyConflict
            if record.state != "COMPLETED" or record.response_payload is None:
                raise IdempotencyConflict
            return TaskRead.model_validate(record.response_payload), True

    record = await db.get(IdempotencyRecord, record_id)
    if record is None:
        raise RuntimeError("The idempotency record disappeared inside its transaction.")

    task = Task(
        tenant_id=principal.tenant_id,
        created_by_user_id=principal.user_id,
        title=payload.title,
        prompt=payload.prompt,
    )
    db.add(task)
    await db.flush()

    db.add(
        TaskEvent(
            tenant_id=principal.tenant_id,
            task_id=task.id,
            sequence=1,
            event_type="TASK_CREATED",
            payload={"status": "QUEUED"},
        )
    )
    await db.flush()

    response = TaskRead.model_validate(task)
    record.state = "COMPLETED"
    record.response_status = 201
    record.response_payload = response.model_dump(mode="json")
    record.completed_at = now
    return response, False


async def list_tasks(
    db: AsyncSession,
    tenant_id: UUID,
    limit: int,
    cursor: str | None,
) -> TaskListResponse:
    statement = select(Task).where(Task.tenant_id == tenant_id)
    if cursor is not None:
        try:
            cursor_created_at, cursor_id = decode_task_cursor(cursor)
        except ValueError:
            raise InvalidTaskCursorError from None
        statement = statement.where(
            tuple_(Task.created_at, Task.id) < tuple_(cursor_created_at, cursor_id)
        )

    statement = statement.order_by(Task.created_at.desc(), Task.id.desc()).limit(limit + 1)
    rows = list((await db.scalars(statement)).all())
    has_more = len(rows) > limit
    items = rows[:limit]
    next_cursor = (
        encode_task_cursor(items[-1].created_at, items[-1].id)
        if has_more and items
        else None
    )
    return TaskListResponse(
        items=[TaskSummary.model_validate(task) for task in items],
        next_cursor=next_cursor,
    )


class InvalidTaskCursorError(Exception):
    """A task-list cursor is invalid."""


async def get_task(
    db: AsyncSession,
    tenant_id: UUID,
    task_id: UUID,
) -> TaskRead:
    task = await db.scalar(
        select(Task).where(
            Task.id == task_id,
            Task.tenant_id == tenant_id,
        )
    )
    if task is None:
        raise TaskNotFound
    return TaskRead.model_validate(task)


async def list_task_events(
    db: AsyncSession,
    tenant_id: UUID,
    task_id: UUID,
    after_sequence: int,
    limit: int,
) -> TaskEventsPage:
    task_exists = await db.scalar(
        select(Task.id).where(
            Task.id == task_id,
            Task.tenant_id == tenant_id,
        )
    )
    if task_exists is None:
        raise TaskNotFound

    rows = list(
        (
            await db.scalars(
                select(TaskEvent)
                .where(
                    TaskEvent.tenant_id == tenant_id,
                    TaskEvent.task_id == task_id,
                    TaskEvent.sequence > after_sequence,
                )
                .order_by(TaskEvent.sequence.asc())
                .limit(limit + 1)
            )
        ).all()
    )
    has_more = len(rows) > limit
    items = rows[:limit]
    return TaskEventsPage(
        items=[TaskEventRead.model_validate(event) for event in items],
        next_after_sequence=items[-1].sequence if has_more and items else None,
    )
