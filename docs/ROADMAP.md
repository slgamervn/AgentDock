# Delivery roadmap

The roadmap separates the working foundation from planned capabilities. A checked item means it is present in the repository and has passed the relevant recorded checks; it does not imply production readiness.

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

- [x] FastAPI service and versioned API contract with process liveness.
- [ ] Authentication/session revocation and tenant/user authorization endpoints.
- [x] Initial PostgreSQL schema, Alembic migration, constraints and indexes for identity, tenant membership, tasks, ordered task events and idempotency.
- [x] PostgreSQL RLS with FORCE ROW LEVEL SECURITY and CI tests for transaction-scoped tenant isolation using a restricted non-owner runtime role.
- [ ] Backup/restore plan and operational database readiness monitoring.
- [ ] Task creation/control API, idempotency enforcement and consistent error model.
- [ ] Separate API and worker processes with health checks.

## Stage 3 — Durable task lifecycle

- [ ] Task create/list/detail/pause/resume/cancel endpoints.
- [ ] Canonical request hashing and idempotent response replay.
- [ ] Atomic per-task event sequence and append.
- [ ] SSE replay, reconnect, heartbeat and client deduplication.
- [ ] Worker lease, heartbeat, retry/backoff, reclaim and recovery.
- [ ] Explicit Definition of Done and evidence-backed terminal state.

## Stage 4 — Single-agent vertical slice

- [ ] Server-side provider/model registry and first provider adapter.
- [ ] Model Gateway contract for capabilities, streaming, usage, timeout and normalized errors.
- [ ] One Main Agent with a narrow, read-only tool set.
- [ ] Context builder with provenance and bounded token budget.
- [ ] End-to-end Android → API → worker → model → SSE → Android flow.

## Stage 5 — Policy, approvals and tools

- [ ] Policy Engine with server-side PEP checks immediately before execution.
- [ ] Typed tool manifests, schema validation, timeout and output validation.
- [ ] Action-bound approval records and approval UX.
- [ ] Audit trail, correlation IDs and secret/private-data redaction.
- [ ] Independent verification and evidence references for each task class.

## Stage 6 — Adaptive intelligence

- [ ] Capability-based model routing and compatible fallback.
- [ ] Evaluation baseline comparing single-agent and bounded multi-agent execution.
- [ ] Add Planner/Executor/Verifier only where measured benefit justifies them.
- [ ] Bounded parallelism, depth, iterations, token, tool, cost and time budgets.
- [ ] Vietnamese intent, typo, slang, ambiguity and naturalness evaluation.

## Stage 7 — Memory, freshness and artifacts

- [ ] Scoped, provenance-bearing memory with inspect/edit/export/delete controls.
- [ ] Project memory and context compaction with preserved constraints.
- [ ] Approved knowledge sources and terminology freshness pipeline.
- [ ] Artifact metadata, access control, storage, checksums and retention.
- [ ] GitHub App with least-privilege installation permissions.
- [ ] MCP registry with per-tool policy, schema, timeout and audit.

## Stage 8 — Isolated execution and device intelligence

- [ ] OpenHands runtime in isolated workspace for coding tasks only.
- [ ] Sandbox resource/network/filesystem boundaries and security tests.
- [ ] Room-backed task/event cache and conflict-aware sync.
- [ ] WorkManager for deferred sync and notification delivery.
- [ ] Adaptive layouts, accessibility, localization and reduced motion.
- [ ] Screen observation with explicit consent and platform-policy review.
- [ ] Proactive suggestions with opt-in, relevance and anti-spam controls.

## Stage 9 — Production readiness

- [ ] Agent Evaluation Lab and regression suite.
- [ ] Threat modeling, sandbox/security testing and secret scanning.
- [ ] Load, latency, recovery, offline and device performance tests.
- [ ] Backup/restore drill, retention/deletion verification and incident procedures.
- [ ] Signed release, certificate continuity, update-over-install test and rollback plan.
- [ ] Staged rollout and backend/worker/client compatibility policy.

## Implementation order

The first coding milestone is a complete **single-agent vertical slice**, not a multi-agent swarm: authenticated task creation, durable events, worker lease, one provider adapter, one Main Agent with a read-only tool, evidence-backed verification and Android task resumption.

See [PRODUCT_SPEC.md](PRODUCT_SPEC.md), [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) and [ARCHITECTURE.md](ARCHITECTURE.md) for product behavior, design decisions and acceptance gates.


## Cross-cutting P0 — User trust, privacy and update safety

- [ ] Complete and legally review versioned Terms of Service and Privacy Policy; resolve operator/contact placeholders before publication.
- [ ] Build data inventory, processor/provider register, retention schedule and cross-border data-flow map.
- [ ] Implement purpose-specific consent ledger and withdrawal; AI conversation contribution remains OFF by default.
- [ ] Separate per-conversation feedback, evaluation dataset contribution and model training consent.
- [ ] Implement user data access/export/correction/deletion request handling and deletion propagation.
- [ ] Add age-appropriate privacy notices and representative-consent handling where required.
- [ ] Publish official release notes and signed update metadata; do not use temporary CI artifacts as the production update channel.
- [ ] Implement in-app update check, changelog, integrity/compatibility checks and Android-confirmed installation.
- [ ] Preserve APK signing identity and test update-over-install, cancellation, tampering and recovery.

## Cross-cutting P1 — Adaptive performance for all supported devices

- [ ] Implement runtime capability profiles without hardware fingerprinting.
- [ ] Add Auto, Resource Saver, Experience Priority and Reduce Motion modes.
- [ ] Bound cache, image loading, prefetch, streaming batches and background concurrency.
- [ ] Add Macrobenchmark and Baseline Profiles/Startup Profiles where supported.
- [ ] Test low-memory, mid-range, high-capability, tablet, foldable, split-screen, slow-network, low-battery and thermal-stress devices.
- [ ] Include only benchmark-verified performance improvements in changelogs.

See [TERMS_OF_SERVICE.md](TERMS_OF_SERVICE.md), [PRIVACY_POLICY.md](PRIVACY_POLICY.md), [UPDATES_AND_RELEASES.md](UPDATES_AND_RELEASES.md) and [DEVICE_PERFORMANCE.md](DEVICE_PERFORMANCE.md).
