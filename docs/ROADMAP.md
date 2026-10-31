
# Delivery roadmap

## Stage 1 — Android foundation

- [x] Native Compose project and design tokens.
- [x] Home / Tabs / Settings navigation.
- [x] Independent task tabs, rename, pin/close/restore and local draft persistence.
- [x] Repository boundary between the ViewModel and DataStore.
- [x] Split the app shell, reusable components and four screen files.
- [x] Clarify local draft and backend-not-ready states in Workspace.
- [x] Add Compose navigation and draft-to-workspace UI tests.
- [x] Add six deterministic ViewModel tests for workspace restoration, tab lifecycle, debounced persistence and server address validation.
- [x] CI debug build, lint and unit test workflow.
- [x] Pin Ubuntu runner image to 24.04 during GitHub's ubuntu-latest migration.
- [x] Verify connected UI tests on the Android 36 emulator (navigation and draft flow).
- [x] Add the official Gradle 9.6.1 Wrapper scripts/JAR and pin the distribution SHA-256 checksum.
- [x] Use the verified Gradle Wrapper for both Android CI and the signed-release workflow.
- [x] Add a preflight check that verifies the selected alias can unlock its private key without exposing secrets.
- [ ] Verify the actual uploaded JKS alias, certificate and passwords with a signed release run.

## Stage 2 — Backend foundation

- [ ] FastAPI service and versioned API contract.
- [ ] Authentication/session revocation and tenant/user authorization.
- [ ] PostgreSQL schema, migrations, indexes and backup plan.
- [ ] Task records, idempotency keys and consistent error model.
- [ ] Separate API and worker processes with health checks.

## Stage 3 — Durable agent execution

- [ ] LangGraph orchestration and explicit bounded agent roles.
- [ ] OpenHands runtime in an isolated workspace.
- [ ] Worker lease, heartbeat, retry/backoff, reclaim and recovery.
- [ ] Definition of Done and evidence-based verification.

## Stage 4 — Control and observability

- [ ] SSE event stream with resume and deduplication.
- [ ] Policy Engine, action-bound approvals and audit trail.
- [ ] Execution timeline with safe log redaction.
- [ ] Pause, resume, cancel and failure recovery UX.

## Stage 5 — Integrations

- [ ] Server-side provider/model registry and routing.
- [ ] GitHub App with least-privilege installation permissions.
- [ ] MCP registry with per-tool policy, validation and timeout.
- [ ] Artifacts, diffs, project memory and versioned configuration.

## Stage 6 — Production readiness

- [ ] Agent Evaluation Lab and regression suite.
- [ ] Threat modeling, sandbox/security testing and secret scanning.
- [ ] Load, latency, recovery, offline and device performance tests.
- [ ] Signed release, certificate continuity, update-over-install test and rollback plan.
