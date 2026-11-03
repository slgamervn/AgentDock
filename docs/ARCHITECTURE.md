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
- The server begins a transaction, sets the client-supplied tenant ID only as a transaction-local candidate scope, and queries the authenticated user's active membership under RLS before accessing tenant data. Missing membership causes rollback and denial. The candidate GUC alone does not grant permission; RLS is defense in depth, not protection against arbitrary SQL execution under a compromised application role.
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


## Product and interaction contract

AgentDock is an outcome-oriented assistant, not a model picker. The user describes a goal in natural language; the system chooses the smallest sufficient execution path and keeps one Main Agent responsible for the user-facing response. Vietnamese-first interaction supports informal wording, abbreviations, code-switching and contextual follow-ups. Ask for clarification only when ambiguity materially affects the outcome or action.

The UX is deliberately simple: Home, Workspace, Activity and Settings. Advanced model/provider details remain optional. Use progressive disclosure, clear task status, persistent drafts, resumable tasks, purposeful motion, accessible controls and adaptive layouts. Avoid manipulative engagement patterns, decorative effects that reduce clarity and unnecessary configuration.

## Runtime ownership

- **Android control plane:** user interaction, local drafts, auth session presentation, task/event cache, approvals and artifact viewing. It is not an agent execution host.
- **API:** authentication, authorization, tenant context, task commands, event delivery, approval endpoints and artifact metadata.
- **PostgreSQL:** authoritative identity mapping, memberships, tasks, ordered events, idempotency, approvals, audit references and durable metadata.
- **Worker:** leases, heartbeats, workflow execution, provider calls, tool dispatch and recovery. Each concurrent operation owns its own database session.
- **Workflow orchestrator:** LangGraph owns explicit graph state, checkpoints, interrupts and recovery. PostgreSQL task/event state remains globally authoritative.
- **Coding runtime:** OpenHands is a delegated isolated workspace for coding tasks only; it does not own global task lifecycle.
- **Model Gateway:** server-side provider adapters, capability registry, routing, quotas, usage and normalized streaming/errors.
- **Policy/Tool Gateway:** validates permissions and exact action arguments immediately before tool execution; approval is external to the model.

Do not introduce a second general-purpose orchestrator by default. OpenAI Agents SDK or other agent runtimes may be evaluated behind an adapter for bounded use cases, but must not duplicate task lifecycle, policy or durable workflow ownership.

## Adaptive agent execution

Default to a single Main Agent. The execution selector chooses among deterministic code/retrieval, one model call, one agent with tools, or a small specialist workflow based on task complexity, separability, risk, capability requirements, budget and latency. Planner/Executor/Verifier are optional bounded roles, not permanent agents that run on every request. Main Agent retains the user-facing response.

Parallelize only independent subtasks. Bound agent depth, parallelism, iterations, model tokens, tool calls, wall time and spend. Model fallback is allowed only when capabilities and task semantics match; never blindly replay side effects. A second model's agreement is not independent proof.

## Context, memory and knowledge freshness

Context is assembled from policy, task goal/Definition of Done, relevant project context, selected memory, recent conversation and retrieved evidence. Keep provenance and budget. Retrieved content and tool outputs are untrusted data, not instructions.

Memory is scoped and user-controllable: working, episodic, preference, project and semantic. Each item carries provenance, confidence, sensitivity, retention and deletion metadata. No passwords, OTPs, provider secrets or unnecessary sensitive data.

Terminology freshness is a retrieval/verification pipeline, not an assumed model-training update. Discover candidate terms from approved sources, verify context and meaning, version definitions and record source/observed/verified dates. Distinguish new slang from established domain terms. Private conversations are not silently used for model training.

## Accuracy and completion contract

Each task defines a Definition of Done and verification method. Evidence may include source citations, tool receipts, file diffs, test results, database read-back or observed state. A task cannot be marked SUCCEEDED merely because an agent says it is done. Insufficient evidence leads to clarification, qualification, retry within limits or abstention.

Use confidence only as a routing signal, never as a substitute for evidence or approval. Risk-based policy and explicit approval govern consequential actions. Record verification status and evidence references in task results.

## API and event contract

REST owns commands and resource reads. SSE carries ordered task events with stable IDs, replay via Last-Event-ID, heartbeat, reconnect and client deduplication. Every task transition and event append is committed atomically with an incremented per-task sequence. Idempotency is scoped to tenant + actor + key and compares a canonical request hash.

The client-supplied tenant identifier is only a selector. Authentication validates the identity first; the server starts a transaction, sets the candidate tenant context, verifies active membership through a tenant-scoped query, then accesses tenant data. Missing membership rolls back and returns denial. Runtime role remains NOSUPERUSER, NOBYPASSRLS and not owner of tenant tables.

## Device, screen and performance boundaries

Use adaptive Android layouts based on window size, not OEM model-name heuristics. Room is for structured remote task/event cache when sync is introduced; DataStore remains for small preferences. WorkManager handles deferrable sync, not durable agent execution.

Screen observation is a later, explicitly consented feature. MediaProjection requires user consent for each capture session and the correct foreground-service type/permissions on supported Android versions. Do not bypass FLAG_SECURE or use AccessibilityService as unrestricted autonomous control. Prefer documented intents, deep links and app-owned APIs; require approval for consequential actions.

Compose performance is measured, not assumed: stable lazy-list keys, deferred state reads, off-main parsing, batched streaming updates, bounded caches, lifecycle-aware animation, reduced-motion support, Baseline Profiles and Macrobenchmark. High refresh rate is opportunistic; the OS/device controls the actual display mode.

## Delivery rule

Build a single end-to-end vertical slice before expanding the system: authenticated task creation → durable event store → worker lease → one provider adapter → one Main Agent with a read-only tool → evidence-backed verification → Android SSE timeline and task resumption. Add multi-agent routing, memory, coding sandbox, MCP, screen assist and proactive features only after their prerequisites and evaluation gates pass.


## Privacy, consent and legal-document architecture

- Keep Terms of Service, Privacy Policy and consent UI versioned and available from onboarding and Settings.
- Model consent as purpose-specific records, not a single user-wide boolean. Separate required service processing, optional analytics, memory, per-conversation feedback, evaluation contribution and model training.
- Store consent purpose, scope, notice version, actor, timestamp, source and withdrawal; enforce the consent decision server-side at data-ingestion and downstream export boundaries.
- Build a data inventory and processor registry before enabling external model providers. Each provider route must satisfy tenant policy, region, purpose, retention and user choice.
- A withdrawal blocks future contribution immediately; deletion workflows propagate to controlled stores, derived datasets and processors according to retention obligations. Do not claim deletion from trained model weights without a verifiable unlearning/retraining process.
- Minimize telemetry and redact prompt/content/secrets from logs. User data is never used for model training by default.
- Treat minors' data with age-appropriate UX and applicable representative-consent requirements.
- Legal documents remain drafts until operator identity, actual processing map, contact details and legal review are complete.

## In-app update architecture (planned)

- Add a dedicated update domain/repository under the Android data boundary; UI observes immutable update state through ViewModel/StateFlow.
- Release metadata is a signed manifest from a pinned official source. Verify manifest signature, channel, expiry, monotonic versionCode, minSdk, ABI, asset URL allowlist, size and SHA-256 before install.
- For Google Play, use Play In-App Updates. For direct APK distribution, hand off to Android Package Installer after integrity checks and explicit user confirmation.
- Android package signature continuity remains mandatory; the updater cannot bypass Package Installer or silently grant unknown-source install permission.
- CI must publish official GitHub Releases and versioned changelog/manifest; current workflow only uploads a temporary Actions artifact and is not sufficient for production updates.
- Release pipeline protects signing keys, signs metadata separately, verifies APK signatures, records provenance and supports stable/preview channels.
- Failed/cancelled update leaves current installation and user data intact. Rollback uses a higher versionCode or a tested compatible recovery mechanism, not arbitrary APK downgrade.

## Adaptive device performance architecture (planned)

- DeviceCapabilityProvider gathers only necessary runtime capability signals (API level, memory pressure, thermal/battery state, window size, supported refresh and connectivity); do not fingerprint devices or collect hardware identifiers.
- PerformancePolicy converts those signals plus user settings into bounded UI effects, cache sizes, prefetch and concurrency.
- Persist explicit user preferences in DataStore; runtime capability profile is ephemeral unless a coarse non-identifying value is operationally necessary.
- Preserve core functionality and safety. Degrade decorative effects and speculative work before reducing essential functionality.
- Use Macrobenchmark, Baseline Profiles/Startup Profiles where supported, trace-based profiling and a representative physical-device matrix. Emulator results do not establish battery/thermal performance.
