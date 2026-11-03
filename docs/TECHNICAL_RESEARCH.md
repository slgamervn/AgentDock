# AgentDock — Technical research and engineering decisions

**Research baseline:** 2026-11-03  
**Scope:** Current first-party documentation, security standards and the project's saved engineering references. This is a curated review, not a claim that every website on the Internet was crawled. Re-check version-sensitive documentation before each implementation.

## 1. Product boundary

AgentDock is an Android-first **control plane** for durable AI work. Android captures intent, displays state, manages user choices and collects approvals. Backend APIs and independent workers own identity, authorization, provider credentials, orchestration, tools, persistent task state and recovery. An accepted server task must continue if Android is closed or restarted.

This avoids using a phone as a long-running execution server and keeps provider secrets out of the APK.

## 2. Keep the first useful path small

The first production-capable milestone is one end-to-end task path:

1. Authenticate a user and establish a server-verified workspace membership.
2. Create a task with an idempotency key and a clear Definition of Done.
3. Persist ordered task events and claim work through a bounded worker lease.
4. Run one Main Agent through one configured provider and a narrow read-only tool.
5. Stream progress with replay/reconnect support.
6. Verify the result against evidence and persist the final state.
7. Reopen the same task on Android after process death or device restart.

Do not create a multi-agent swarm, a general plugin marketplace or arbitrary device-control layer before this path works reliably.

## 3. Authentication and tenancy

- Android is a public OAuth client: use Authorization Code + PKCE through the system browser or a browser-backed authentication tab. Never embed an OAuth client secret in the APK.
- The backend validates signature, issuer, audience, expiry and the allowed algorithm set. Extracting a bearer token is not token validation.
- Map identity by (issuer, subject), not email alone. Do not silently link an existing account just because an identity provider returns a matching email; use a verified account-linking flow.
- A workspace/tenant ID sent by Android is an untrusted candidate scope, never proof of access. Inside a transaction, set it only as a transaction-local candidate, then query the authenticated user's active membership under RLS before returning or touching tenant data. If membership is absent, roll back and deny. The tenant GUC does not grant membership; it only scopes which membership row can be checked. RLS is defense in depth, not protection against arbitrary SQL execution under a compromised application role.
- The current runtime role is deliberately not allowed to insert users or tenants. Do not enable self-service account/workspace provisioning until a controlled identity-onboarding mechanism and narrow database privileges have been designed and tested. Never solve this by granting broad write access to the runtime role.
- Keep the restricted runtime database role separate from the migration owner and retain forced RLS on tenant-owned records.

## 4. Task lifecycle and durable execution

PostgreSQL remains the authoritative source for task state, ordered events, idempotency, approvals and audit records.

- Use REST for commands and Server-Sent Events for ordered task updates; support event replay, heartbeat, deduplication and reconnect.
- Make state changes and their event records atomic. Use idempotency keys bound to a canonical request hash.
- Use short, renewable worker leases, heartbeats and bounded retries; recover expired leases without repeating completed side effects.
- Use an outbox or equivalent transactional publication boundary when event delivery must not be lost between database commit and stream notification.
- Do not add Redis, Kafka, WebSockets or a separate queue solely because they are common in agent architectures. Introduce them only after measured concurrency or operational requirements justify them.

## 5. Agent orchestration

Use one user-facing Main Agent. It chooses the smallest sufficient execution strategy:

- deterministic operation or retrieval;
- one model call;
- one agent with a narrow set of tools;
- a small specialist workflow only when the work can genuinely be separated;
- independent verification when consequence or uncertainty warrants it.

Keep the task's externally visible lifecycle in the database. Use LangGraph for the agent's stateful reasoning workflow, checkpointing and human-interrupt points where those capabilities are useful; do not let a graph checkpoint become a substitute for task authorization, event history or the task database.

Add Planner/Researcher/Developer/Tester/Reviewer specialists only after evaluation demonstrates a measurable improvement over a single-agent baseline in verified completion, latency or cost without reducing safety.

## 6. Multi-provider model gateway

Keep a provider-neutral application interface between orchestration and model vendors. It should normalize:

- model identity and capability declarations;
- text/vision/audio input support;
- structured output and tool-call support;
- streaming, cancellation and timeout;
- token/usage metadata and cost accounting;
- retryable versus permanent errors;
- provider health and data-handling constraints.

**Candidate for the existing Python backend:** evaluate the LiteLLM Python SDK behind AgentDock's own ModelGateway interface. The SDK documents a common API across many providers, streaming, retries and routing. Do not expose a public LiteLLM proxy or add a second gateway in the first slice. Keep the interface replaceable and run contract tests for each exact provider/model/capability that AgentDock exposes.

Important constraints:

- Discover model IDs and capabilities from current provider/gateway metadata; never invent model slugs from naming patterns.
- A model's advertised tool or vision capability is not sufficient: run provider contract tests for tool-call arguments, streaming, structured output, token accounting and error normalization.
- Fallback only to a model that satisfies the same task requirements and data policy. A tool/action request with uncertain completion must not be blindly replayed.
- Route by capability, quality needs, latency, provider health, region/data policy and user budget, not by model brand alone.
- Record model/provider, usage, latency and normalized outcome. Do not log raw secrets or unnecessary conversation content.
- Consider Vercel AI Gateway only if the deployment architecture later makes it a clear operational fit. Do not operate multiple gateways without a concrete reason.

## 7. Provider credentials and cost controls

Support centrally configured models first. Add Bring Your Own Key only with a server-side secret-vault design:

- Encrypt provider credentials at rest using a managed KMS or an envelope-encryption key isolated from the database.
- Keep encryption keys outside the database and rotate them with a documented procedure.
- Only return masked connection status and metadata to Android; never provide a read endpoint for the original secret.
- Scope credentials by tenant/provider and restrict which deployments can use them.
- Add per-user/tenant concurrency, token and spend limits, request timeouts, daily/monthly budgets and clear usage display.
- Do not persist a provider key in DataStore, logs, analytics, crash reports or APK resources.

A user-provided key may be used for requests only after explicit save/consent; the app must explain that the key is processed by the AgentDock server and sent to the selected provider.

## 8. Tools, MCP and approvals

A model can propose a tool call; only AgentDock can authorize and execute it.

For every invocation:
1. Resolve the tool from a server-controlled registry.
2. Validate arguments against the current schema and size/time limits.
3. Authenticate the requesting user and check tenant/tool/resource permission.
4. Apply the policy decision immediately before execution.
5. Require an approval bound to the exact action, arguments, target resource, policy version and expiry when risk demands it.
6. Execute with least privilege and an idempotency/receipt strategy where possible.
7. Validate and treat tool output as untrusted data.
8. Persist a redacted audit event and evidence reference.

Use the official MCP specification/SDK version selected for implementation. The 2026-07-28 MCP release describes a stateless protocol core and updated authorization hardening; do not implement an older session assumption from a stale tutorial. MCP discovery must not imply permission to call a tool. Each server and tool receives an explicit policy, credential scope, timeout and audit boundary.

## 9. Coding and untrusted execution

Do not execute AI-generated code inside the FastAPI process or an ordinary worker host.

For coding tasks, evaluate OpenHands or another isolated execution runtime as an optional, separate workspace. Require non-root execution, isolated filesystems, CPU/memory/disk/time/process limits, bounded concurrency and a deny-by-default egress policy with a short allowlist. Keep credentials outside untrusted sandboxes; use a credential broker when a task must access an approved remote service. Stop the task when policy or budget limits are reached.

This capability belongs after the single-agent path and tool policy have passed security tests.

## 10. Android application design

- Keep the existing Kotlin/Jetpack Compose foundation and repository boundary; follow Android's layered architecture and unidirectional data flow.
- Use lifecycle-aware state collection, screen-level ViewModels, immutable UI state and repositories. Keep business state out of Activity instances.
- Use a small primary navigation: Home, Workspace, Activity and Settings. Advanced model/tool configuration is progressive disclosure.
- Make the task composer the clearest primary action. A task timeline should answer: what is happening, what changed, what needs approval, and what is verified.
- Use neutral surfaces with the existing emerald #3dba7a and amber #e6b35a accents. No decorative gradients, blur, glows or identical card grids.
- Use real task copy, specific empty/error states, visible retry/recovery actions and stable naming for the same action across dialogs, buttons and confirmations.
- Support system font scaling, TalkBack, large touch targets, reduced motion, landscape, split-screen, tablet and foldable layouts.
- Animation should explain user-triggered state change; keep it interruptible and light. Do not animate every card or every stream token.

## 11. Performance and resilience

Measure before optimizing. Track cold/warm startup, first frame, scroll jank, task-list rendering, memory, CPU, network traffic, battery, thermal pressure, reconnect behavior and crash/ANR rates.

Adapt prefetch, image loading, caching, visible history and non-essential effects to current resource conditions. Do not fingerprint devices or promise a universal FPS target. Preserve core security and privacy behavior in every performance mode.

## 12. Privacy, improvement and legal readiness

- Conversation contribution for AI improvement is OFF by default and separate from service processing, feedback, evaluation and model training.
- Request purpose-specific consent and record notice version, scope, timestamp and withdrawal. Allow per-conversation contribution as a separate choice.
- Do not train/fine-tune on conversations without a separate explicit choice and a documented processor/retention/deletion path.
- Offer access/export/correction/deletion and consent withdrawal flows where legally applicable; verify propagation through processors and backups.
- Do not collect API keys, OTPs or unrelated sensitive material for improvement.
- The current ToS and Privacy Policy drafts contain unresolved operator and data-flow placeholders. They must not be published as final until actual processing, providers, retention, jurisdiction and contact details are verified and reviewed.

## 13. Updates and releases

For direct APK distribution, use official GitHub Releases plus a signed, versioned manifest, pinned public verification key, SHA-256 validation, allowlisted asset URLs and Android Package Installer confirmation. Validate package ID and signing-certificate continuity. Never silently install an APK or execute arbitrary update scripts. Keep Stable and Preview distinct. Do not describe the existing release workflow as an in-app updater.

## 14. Delivery gates

| Gate | Completion evidence |
|---|---|
| Android/backend baseline | Clean build, lint, unit and connected UI tests; backend tests against PostgreSQL |
| Identity | PKCE login, signature/issuer/audience/expiry tests, session revocation and cross-tenant denial |
| Durable tasks | Idempotency conflict/replay, atomic event ordering, SSE replay and worker lease recovery |
| Single-agent vertical slice | Android → API → worker → one provider → stream → verified result; survives app restart |
| Policy and approvals | Denied tool calls cannot execute; approval digest mismatch rejected; auditable decision |
| Multi-agent | Evaluations show benefit over single-agent baseline under bounded cost/time/tool limits |
| Production | Threat model, backups/restore drill, data deletion verification, update/install recovery and measured device matrix |

## Sources reviewed

- Android architecture: https://developer.android.com/topic/architecture and https://developer.android.com/topic/architecture/recommendations
- Compose state and lifecycle: https://developer.android.com/develop/ui/compose/state
- Android Keystore and network security: https://developer.android.com/privacy-and-security/keystore and https://developer.android.com/privacy-and-security/security-config
- OAuth for native applications: https://www.rfc-editor.org/rfc/rfc8252.html
- FastAPI lifespan: https://fastapi.tiangolo.com/advanced/events/
- FastAPI security review: https://cheatsheetseries.owasp.org/cheatsheets/FastAPI_Security_Cheat_Sheet.html
- OWASP MASVS: https://mas.owasp.org/MASVS/
- LiteLLM Python SDK/gateway: https://docs.litellm.ai/docs/ and https://docs.litellm.ai/docs/proxy/architecture
- OpenAI Responses and tool calling: https://developers.openai.com/api/reference/responses/overview and https://developers.openai.com/api/docs/guides/function-calling
- MCP 2026-07-28 release: https://blog.modelcontextprotocol.io/posts/2026-07-28/
- GitHub App least-privilege guidance: https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/best-practices-for-creating-a-github-app
- Anthropic Skills: https://github.com/anthropics/skills
- Emil Kowalski Design Engineering skills: https://github.com/emilkowalski/skills
