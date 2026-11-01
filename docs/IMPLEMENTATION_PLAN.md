# AgentDock implementation plan

**Status:** Prepared plan only. No application code, database migration, provider integration or benchmark is implemented by this document.

## 1. Baseline inspected

Current `main` has:
- Android Kotlin/Compose foundation, DataStore-backed local workspace and draft tabs.
- FastAPI service with process liveness endpoint.
- SQLAlchemy async, asyncpg, Alembic and initial tenant/user/membership/task/task-event/idempotency schema.
- PostgreSQL RLS foundation and CI coverage for tenant isolation.
- Android minSdk 23, target/compile SDK 36, JDK 17, Gradle Wrapper 9.6.1.
- No Android network permission, sign-in flow, remote task API, model provider adapter, orchestration runtime or agent worker yet.

Existing docs: `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`. See those documents for the current foundation and existing completed checks.

## 2. Architectural decisions to hold constant

1. Android is a control plane; backend/worker own durable execution.
2. PostgreSQL is authoritative for identity, tenant access, tasks, ordered events, approvals, idempotency and audit.
3. LangGraph is the workflow/state-machine orchestrator. Do not add a second general-purpose orchestrator by default.
4. OpenHands is an optional isolated execution runtime for coding/workspace tasks, not the global task lifecycle manager.
5. Start with one Main Agent and one provider integration. Add specialist agents only after evaluations show a benefit.
6. A server-side Model Gateway owns provider adapters, capability registry, routing, quotas and usage accounting.
7. Policy Engine and approval service remain outside the model. The model cannot grant permissions.
8. Use database-backed worker leases and recovery first. Add a broker/cache only after measured workload justifies it.
9. Use REST for commands and SSE for ordered task events. Defer WebSocket.
10. Keep provider credentials and tool secrets out of Android.

## 3. Target code organization

### Backend

```text
backend/app/
  api/v1/                 versioned HTTP routes and request/response schemas
  core/                   configuration, errors, logging, correlation IDs
  auth/                   OIDC/JWT validation, identity mapping, sessions
  tenancy/                membership verification and tenant context
  db/                     models, session factory, repositories
  tasks/                  task commands, lifecycle, idempotency, event store
  orchestration/           LangGraph graph, nodes, state and checkpoint adapter
  agents/                 Main Agent and bounded specialist definitions
  providers/               provider adapters, model registry and router
  tools/                   tool registry, schemas and execution gateway
  policy/                  PDP, PEP, risk classification and approvals
  workers/                 leases, heartbeat, polling, retry and recovery
  memory/                  memory policy, retrieval and context assembly
  knowledge/               source connectors, terminology and freshness jobs
  artifacts/               metadata, storage interface and retention
  observability/            traces, metrics, audit and redaction
backend/migrations/         Alembic migrations
backend/tests/
  unit/
  integration/
  security/
  evaluation/
```

Do not create empty packages solely to match this diagram. Add each package with the first vertical slice that needs it.

### Android

```text
app/src/main/java/com/slg/agentdock/
  core/
    network/               API client, auth interceptor and connectivity
    database/              Room cache when remote task sync begins
    datastore/             small local preferences and draft persistence
    device/                window, memory, thermal and refresh capability
    design/                tokens and reusable native components
  data/
    local/
    remote/
    repository/
  domain/
    model/
    usecase/
    validation/
  feature/
    home/
    workspace/
    activity/
    approvals/
    settings/
    screenassist/           later, isolated feature
  navigation/
  worker/                   WorkManager for deferred sync only
```

Keep the current repository boundary and migrate incrementally; do not replace the working local draft flow in one large rewrite.

## 4. Delivery sequence and acceptance gates

### Phase 0 — Design and repository baseline

- Freeze Product Spec, Architecture, API contracts, threat model and implementation slices.
- Confirm the intended branch and reconcile existing feature branches without force-pushing or discarding work.
- Record baseline build/test status from CI; do not infer it from source files.
- Define environment separation and secrets handling.

**Gate:** approved design docs, clean branch strategy, reproducible Android/backend baseline.

### Phase 1 — Identity, tenancy and API readiness

- Implement OIDC Authorization Code + PKCE for Android; no embedded client secret.
- Validate issuer, audience, signature, expiry and allowed algorithms server-side.
- Map identity by issuer + subject; support session revocation.
- Implement membership-checked tenant selection with the required transaction/RLS bootstrap order.
- Add readiness endpoint that checks required dependencies separately from liveness.
- Add consistent error envelope, request IDs and API version contract.

**Gate:** auth integration tests, expired/revoked token tests, cross-tenant denial tests, readiness failure tests. Runtime role remains NOBYPASSRLS and not table owner.

### Phase 2 — Durable Task API and event stream

- Implement create/list/detail/pause/resume/cancel task APIs.
- Enforce idempotency using tenant + actor + key + canonical request hash.
- Same key and same payload replays the recorded response; same key with different payload returns conflict.
- Append events with an atomically incremented per-task sequence in the same transaction as state changes.
- Implement SSE replay via Last-Event-ID, heartbeat, reconnect and client deduplication.
- Add task ownership, retention and pagination rules.

**Gate:** concurrent idempotency tests, event ordering/replay tests, cancellation race tests, tenant isolation tests.

### Phase 3 — Single-agent vertical slice

- Implement one server-side provider adapter and one model deployment configuration.
- Implement Model Gateway interface, capability declaration, timeout, usage capture and safe error normalization.
- Implement one Main Agent with a narrow tool set.
- Add prompt/context builder with explicit source separation and token budget.
- Persist task checkpoints and worker lease/heartbeat; closing Android must not stop accepted work.
- Return streamed progress and final response through task events.

**Gate:** end-to-end task from Android → API → worker → model → event stream → Android; provider secrets never reach client; timeout and worker restart recover safely.

### Phase 4 — Policy, tools and verification

- Implement typed tool manifests and strict argument validation.
- Add server-side policy evaluation immediately before execution.
- Add action-bound approval records and approval UX.
- Add audit events and secret/private-data redaction.
- Define Definition of Done and independent verifiers per task class.
- Store evidence references, test reports and tool receipts.

**Gate:** denied actions cannot execute; approval digest mismatch is rejected; external side effects are not duplicated on retry; task cannot become SUCCEEDED without required evidence.

### Phase 5 — Adaptive routing and bounded multi-agent

- Add capability-based model registry and routing policy.
- Add fallback only for compatible, safe and retryable operations.
- Add Planner, Executor and Verifier roles only where benchmarked gains justify them.
- Main Agent retains user-facing response ownership.
- Bound depth, parallelism, iterations, tokens, cost, wall time and tool calls.
- Use separate specialist context with least privilege and explicit output schema.

**Gate:** evaluation shows measurable improvement over single-agent baseline in verified success or latency/cost tradeoff; no regression in safety or task completion.

### Phase 6 — Memory, knowledge freshness and personalization

- Implement scoped, provenance-bearing memory with user controls.
- Add project memory and terminology catalog.
- Add approved source retrieval and scheduled freshness checks.
- Separate candidate discovery from verified knowledge promotion.
- Add deletion/retention/export flows and tests.

**Gate:** memory permission isolation, deletion propagation, stale-source handling, citation/provenance checks and Vietnamese terminology evaluation.

### Phase 7 — Artifacts, coding sandbox and integrations

- Add artifact metadata, storage interface, checksums, size limits and retention.
- Integrate OpenHands only for tasks requiring a coding workspace.
- Isolate each workspace; non-root, bounded resources and restricted egress.
- Add GitHub App with least-privilege installation permissions.
- Add MCP registry with per-tool schema, policy, timeout, output validation and audit.

**Gate:** sandbox escape/security test plan, artifact access control, repository permission tests, MCP malicious-output tests.

### Phase 8 — Device intelligence and production readiness

- Add Room-backed task/event cache and conflict-aware sync.
- Add WorkManager for deferred sync and notification delivery.
- Add adaptive layouts and accessibility checks.
- Add screen observation only after consent, foreground-service and platform-policy review.
- Add performance monitors, Macrobenchmark and real-device test matrix.
- Complete backup/restore, incident response, migration rollback, staged rollout and release-signing continuity.

**Gate:** offline/reconnect tests, data restore drill, security review, release install/update test, device matrix and performance baseline.

## 5. First coding slice (recommended)

Do not start with a multi-agent swarm. The first code slice should be a complete, testable single-agent path:

1. Backend auth and tenant-scoped task create/list/detail.
2. Atomic idempotency and ordered task events.
3. One worker with lease/heartbeat and durable state.
4. One provider adapter behind a Model Gateway interface.
5. One Main Agent with one read-only tool.
6. Evidence-bearing final result and task timeline.
7. Android sign-in, create task, observe SSE events, reopen task and show result.

This vertical slice proves the end-to-end architecture before specialist agents, memory, screen sharing or complex UI are added.

## 6. Data model additions (introduce only with their phase)

Potential later tables:
- `auth_identities`, `user_sessions`, `tenant_invitations`
- `worker_leases`, `task_checkpoints`, `task_artifacts`
- `provider_configs`, `model_deployments`, `model_usage_records`
- `tool_definitions`, `tool_credentials`, `policy_rules`, `approval_requests`, `audit_events`
- `memory_items`, `knowledge_sources`, `terminology_entries`
- `user_devices`, `device_sync_cursors`, `sync_operations`

All tenant-owned records require tenant scoping, appropriate foreign keys, indexes, RLS and deletion/retention policy. Never create all tables in one speculative migration.

## 7. Test strategy

- Unit: domain transitions, router decisions, canonical hashing, context assembly, policy evaluation.
- Integration: PostgreSQL transactions, RLS, idempotency, SSE replay, worker leases, provider adapters.
- Security: tenant escape, token validation, prompt injection, malicious tool output, approval tampering, secret leakage, sandbox boundaries.
- Evaluation: Vietnamese intent/typo/slang, ambiguity, grounded answers, tool selection, completion verification, abstention and terminology freshness.
- E2E: sign-in → create task → progress → approval → completion → reopen task.
- Android: navigation, process recreation, offline state, accessibility, adaptive windows, Macrobenchmark.
- Reliability: worker crash/reclaim, provider timeout, duplicate delivery, cancellation race, DB outage and backup restore.

Every reported pass must be tied to an actually executed test run or CI result.

## 8. Definition of implementation-ready

The design is ready to code when:
- architecture and ownership boundaries are documented;
- API request/response and event schemas are specified;
- task states and transition rules are explicit;
- tenant/RLS bootstrap and approval model are reviewed;
- provider adapter contract and routing policy are specified;
- first vertical slice and acceptance tests are defined;
- Android navigation and empty/loading/offline/error/approval states are specified;
- secrets, observability, retention and rollback are covered.

## 9. Branch and change discipline

- Build new work on a feature branch from the current intended base.
- Do not merge old feature branches based only on their names; inspect ancestry and diffs first.
- Make small, reviewable commits.
- After each code change, re-read affected files and surrounding code, run targeted tests, then full relevant CI.
- Do not change applicationId or release signing identity as part of feature work.
