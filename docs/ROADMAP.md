# Delivery roadmap

## Stage 1 — Android foundation

- [x] Native Compose project and design tokens.
- [x] Home / Tabs / Settings navigation.
- [x] Independent task tabs, rename, pin/close/restore and local draft persistence.
- [x] Repository boundary between the ViewModel and DataStore.
- [x] CI debug build and unit test workflow.
- [x] Isolated manual release workflow using repository secrets.
- [x] Verify Android CI: lint, unit tests, debug APK build and artifact upload passed.
- [x] Pin Ubuntu runner image to 24.04 during GitHub's ubuntu-latest migration.
- [ ] Generate and commit the official Gradle Wrapper.
- [ ] Verify the uploaded JKS alias, certificate and passwords without exposing them.
- [ ] Split the current Compose screen source into focused screen/component files and add UI navigation tests.

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
