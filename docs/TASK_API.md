# AgentDock Task API contract

**Status:** Task persistence and pre-execution lifecycle commands are integrated into `test` after PR #12. This API persists and controls queued tasks; it does not execute them. Promotion to `main` is a separate, validated PR.

## Authentication and tenant boundary

Every endpoint requires an AgentDock access token in the Bearer authorization header. The API validates the session and checks that the user still has an active membership in the session's workspace. Authentication, transaction-local RLS context and membership verification run in one request transaction.

The workspace is derived from the authenticated session. Requests must not send a tenant ID to select or override the workspace. The database remains the isolation boundary: tenant context is set before membership lookup, and every task, event and idempotency query executes under forced RLS.

Task responses use `Cache-Control: no-store` and `Pragma: no-cache` because prompts and task metadata may be private.

## Endpoints

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/v1/tasks` | Create and persist a task |
| GET | `/api/v1/tasks` | List tasks in the current workspace |
| GET | `/api/v1/tasks/{task_id}` | Read a task in the current workspace |
| GET | `/api/v1/tasks/{task_id}/events` | Read ordered events after a sequence |
| POST | `/api/v1/tasks/{task_id}/pause` | Move a queued task to `PAUSED` |
| POST | `/api/v1/tasks/{task_id}/resume` | Move a paused task to `QUEUED` |
| POST | `/api/v1/tasks/{task_id}/cancel` | Cancel a queued or paused task |

### Create

Send `Idempotency-Key` as a header (8–128 printable ASCII characters) with a JSON body:

```json
{
  "title": "Review a release",
  "prompt": "Check the release checklist and report missing gates."
}
```

- Title is trimmed and limited to 200 characters.
- Prompt is limited to 32,000 characters and cannot be blank.
- Unknown fields are rejected. In particular, a client-supplied `tenant_id` is not accepted.
- Task, initial `TASK_CREATED` event and idempotency response are written in one PostgreSQL transaction.
- A successful task begins in `QUEUED`; this means it is persisted, not that a worker or AI model is running.
- The idempotency window is 24 hours. Repeating the same key and canonical request returns the same task. Reusing the key with a different request returns 409. A key can be reused after its record expires.

### List

`GET /api/v1/tasks?limit=25&cursor=<opaque-cursor>`

- `limit` is 1–100; default 25.
- Pages use keyset pagination ordered by `created_at DESC, id DESC`; the cursor contains both sort values.
- The response returns `items` and `next_cursor`. A null cursor means there are no more rows.

### Task events

`GET /api/v1/tasks/{task_id}/events?after_sequence=0&limit=100`

- Sequence numbers are task-local and strictly ordered.
- The initial task event has sequence 1 and type `TASK_CREATED`.
- `next_after_sequence` is returned when another page exists. Pass that value as the next `after_sequence`.
- Event reads do not expose tasks outside the authenticated workspace.

## Pre-execution lifecycle commands

| Command | Allowed source | Result | Event |
|---|---|---|---|
| Pause | `QUEUED` | `PAUSED` | `TASK_PAUSED` |
| Resume | `PAUSED` | `QUEUED` | `TASK_RESUMED` |
| Cancel | `QUEUED`, `PAUSED` | `CANCELLED` | `TASK_CANCELLED` |

Each command locks the task row, validates the current status, updates the status and appends the next task-local event sequence in one transaction. Repeating a command returns the current task without appending another event only when the latest event proves that the same command already completed. For example, `resume` on a newly created `QUEUED` task is rejected because no prior `TASK_RESUMED` event exists. Concurrent commands serialize on the row lock; only the first actual transition appends an event. Commands from unsupported states return 409.

A task in `RUNNING` cannot yet be paused or cancelled. That requires a worker command channel and cooperative execution handling; the API does not pretend a database status update can stop active work.

## Not implemented by this slice

- Worker lease, heartbeat, execution, retries or recovery.
- Cooperative pause/cancel for a task already in `RUNNING` (until a worker command channel exists).
- SSE streaming, heartbeat and live reconnect (the event read endpoint is the replay foundation only).
- Usage quotas, per-user rate limits and retention cleanup jobs.
- Android remote task UI and local task/event cache.

Do not present a `QUEUED` task as running or completed until a worker and verified execution path have been deployed.
