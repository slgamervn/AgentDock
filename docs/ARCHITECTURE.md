# AgentDock architecture

## Product boundary

The Android application is a control plane, not the execution host. It owns user interaction, local drafts, tab navigation, connection configuration, task views and approval UX. A backend API and independent workers own authentication, task state, authorization, orchestration, tools and durable execution. Closing Android must not stop an accepted task.

## Android foundation

- Compose UI renders immutable state and sends intent-like callbacks to the ViewModel.
- \`AgentDockViewModel\` owns screen state and debounced persistence, but does not know Android application context or storage implementation.
- \`WorkspaceRepository\` is the data boundary used by the ViewModel.
- \`DefaultWorkspaceRepository\` currently wraps \`WorkspaceStore\`, the DataStore-backed local data source.
- The local store persists tabs, active selection, drafts, recently closed tabs and the server address. It is not the authoritative task database.
- The current \`Workspace\` page clearly distinguishes an on-device draft from a server-executed task.
- No provider API key, GitHub token, MCP credential or signing key is embedded in the application.

## Backend foundation

- A minimal FastAPI process now exposes GET /api/v1/health/live; this is a process liveness check only, not a database or worker readiness guarantee.
- Runtime settings are loaded from AGENTDOCK_ environment variables through Pydantic Settings. Secrets must not be committed or returned by health routes.
- The container runs as a non-root user and excludes local environment files.

## Backend direction (planned; not implemented yet)

- FastAPI for versioned REST APIs and SSE event delivery.
- PostgreSQL as the authoritative store for task, approval, policy and audit records; no database connection or schema is implemented yet.
- LangGraph owns the explicit workflow graph, checkpoints and recovery transitions.
- OpenHands runs delegated coding/research work inside an isolated workspace. It does not own the task's global lifecycle.
- Independent worker processes use database-backed leases/heartbeats and idempotent transitions; a queue is a delivery mechanism, not the source of truth.
- Object storage stores generated files and reports; Redis may be introduced for ephemeral queue/event fan-out after workload needs are measured.

## Task lifecycle target

\`QUEUED → PLANNING → RUNNING → WAITING_APPROVAL → RUNNING → VERIFYING → SUCCEEDED\`

Additional terminal/recovery states include \`FAILED\`, \`CANCELLED\`, \`PAUSED\`, \`RETRYING\`, \`RECOVERING\` and \`EXPIRED\`. A task is not successful merely because an Agent stopped; the server must evaluate its Definition of Done and attach verification evidence.

## Security model

- Every action is checked server-side against identity, task, agent, tool, resource, permission and risk immediately before execution.
- An approval is bound to the exact action, arguments and resource. Material changes require a new policy evaluation and approval.
- External web pages, repositories, issue text and tool output are untrusted data, never authority-bearing instructions.
- Sandbox workers run non-root with bounded CPU, memory, disk, process count, wall time and network access. No Docker socket is mounted in an agent sandbox.
- Secrets are stored server-side and passed only to the narrow operation that needs them.
- Audit/logging uses correlation IDs and redacts credentials and unnecessary private content.

## API direction

REST handles task creation, pause/resume/cancel and approvals. SSE carries ordered task events with event IDs, replay via \`Last-Event-ID\`, heartbeat, reconnect and client-side deduplication. WebSocket is deferred until there is a concrete bidirectional streaming requirement.
