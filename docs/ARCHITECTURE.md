# AgentDock architecture

## Product boundary

The Android application is a control plane, not the execution host. It owns user interaction, local drafts, tab navigation, connection configuration, task views and approval UX. A backend API and independent workers own authentication, task state, authorization, orchestration, tools and durable execution. Closing Android must not stop an accepted task.

## Android foundation

- Compose UI renders immutable state and sends intent-like callbacks to the ViewModel.
- `AgentDockViewModel` owns screen state and debounced persistence, but does not know Android application context or storage implementation.
- `WorkspaceRepository` is the data boundary used by the ViewModel.
- `DefaultWorkspaceRepository` currently wraps `WorkspaceStore`, the DataStore-backed local data source.
- The local store persists tabs, active selection, drafts, recently closed tabs and the server address. It is not the authoritative task database.
- The current `Workspace` page clearly distinguishes an on-device draft from a server-executed task.
- No provider API key, GitHub token, MCP credential or signing key is embedded in the application.

## Backend foundation

- FastAPI exposes GET /api/v1/health/live as a process liveness check only; it is not a database or worker readiness guarantee.
- Runtime settings use the AGENTDOCK_ prefix. Database URLs are represented as secret values in application settings.
- SQLAlchemy async sessions use the asyncpg PostgreSQL dialect. Each concurrent request or worker operation must use its own AsyncSession; it is not safe to share one session across concurrent tasks.
- Alembic owns versioned schema changes. The first migration creates tenants, users, tenant memberships, tasks, ordered task events and idempotency records, including the indexes and constraints needed by the initial model.
- RLS is enabled and forced on every tenant-owned table. The tenant policy compares tenant_id with the transaction-local app.current_tenant_id setting and defaults to deny when no tenant context exists.
- Migrations and runtime use separate database roles. The runtime role must be NOSUPERUSER, NOBYPASSRLS and not own tenant tables. A superuser or BYPASSRLS role can bypass PostgreSQL row policies.
- Before setting tenant context, server-side authorization must validate the signed-in user's membership. RLS protects database access boundaries but does not replace authentication, authorization or protection against arbitrary SQL execution.
- CI applies migrations and verifies read/write isolation against a real PostgreSQL service, including a restricted runtime role.

## Backend direction (planned; not implemented yet)

- Authentication, refresh-session rotation/revocation and tenant membership authorization APIs.
- Task creation/control endpoints, a consistent API error envelope and server-side idempotency handling.
- LangGraph owns the explicit workflow graph, checkpoints and recovery transitions.
- OpenHands runs delegated coding/research work inside an isolated workspace. It does not own the task's global lifecycle.
- Independent worker processes use database-backed leases/heartbeats and idempotent transitions; a queue is a delivery mechanism, not the source of truth.
- Approval records, action-bound policy evaluation, audit trail and artifact storage are future schema/API slices.
- Object storage stores generated files and reports; Redis may be introduced for ephemeral queue/event fan-out after workload needs are measured.

## Task lifecycle target

`QUEUED → PLANNING → RUNNING → WAITING_APPROVAL → RUNNING → VERIFYING → SUCCEEDED`

Additional terminal/recovery states include `FAILED`, `CANCELLED`, `PAUSED`, `RETRYING`, `RECOVERING` and `EXPIRED`. A task is not successful merely because an Agent stopped; the server must evaluate its Definition of Done and attach verification evidence.

## Security model

- Every action is checked server-side against identity, task, agent, tool, resource, permission and risk immediately before execution.
- An approval is bound to the exact action, arguments and resource. Material changes require a new policy evaluation and approval.
- External web pages, repositories, issue text and tool output are untrusted data, never authority-bearing instructions.
- Sandbox workers run non-root with bounded CPU, memory, disk, process count, wall time and network access. No Docker socket is mounted in an agent sandbox.
- Secrets are stored server-side and passed only to the narrow operation that needs them.
- Audit/logging uses correlation IDs and redacts credentials and unnecessary private content.

## API direction

REST handles task creation, pause/resume/cancel and approvals. SSE carries ordered task events with event IDs, replay via `Last-Event-ID`, heartbeat, reconnect and client-side deduplication. WebSocket is deferred until there is a concrete bidirectional streaming requirement.
