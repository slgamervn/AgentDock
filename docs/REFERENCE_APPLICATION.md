# AgentDock — application of saved engineering references

**Reviewed:** 2026-11-05  
**Target:** current `main` architecture and Android-first product  
**Status:** reference-to-design decisions; not evidence that the listed capabilities are implemented.

This document turns the saved skills and reference collection into concrete AgentDock engineering rules. References are not a mandate to install dependencies, copy UI, or replace the established stack.

## 1. Non-negotiable architecture decisions

Keep the existing architecture documented in `ARCHITECTURE.md`:

- Native Kotlin + Jetpack Compose Android app is the control plane.
- FastAPI backend, PostgreSQL and independent workers own identity, authorization, durable tasks, orchestration, tool execution and recovery.
- PostgreSQL remains authoritative for task state, ordered events, idempotency, approvals and audit records.
- LangGraph remains the planned workflow/state-machine orchestrator.
- OpenHands remains an optional isolated coding workspace, not the global task lifecycle owner.
- The ModelGateway abstraction remains provider-neutral. Evaluate LiteLLM behind it; use Vercel AI Gateway only if deployment and operational constraints justify it.
- One Main Agent owns the user-facing response. Specialist agents are bounded and enabled only when evaluations prove a benefit.
- Never embed provider secrets or long-lived GitHub/MCP credentials in the APK.

**Why:** the repository already has a deliberate Python/Android architecture. Vercel eve, GitHub Copilot SDK and other runtimes are comparison references, not reasons to migrate or introduce a second orchestrator.

## 2. Agent runtime and orchestration references

### Vercel eve

Reference: https://github.com/vercel/eve and https://eve.dev/docs

Study its durable sessions, checkpointed work, human-in-the-loop pauses, typed tools, subagents, skills, sandbox boundaries and evaluations. Apply the concepts selectively:

- Preserve durable task execution and resumable event streams.
- Model approvals as persisted, action-bound interruptions.
- Keep specialist instructions and tools isolated by role.
- Add evaluation suites for task quality and safety.
- Treat sandboxing and least-privilege connections as first-class runtime boundaries.

Do **not** replace LangGraph or the current backend with eve without a separate measured architecture decision. The existing design already assigns global task lifecycle to PostgreSQL and workflow state to LangGraph.

### GitHub Copilot SDK

Reference: https://github.com/github/copilot-sdk

Evaluate only for a future, bounded coding-assistant adapter or as a comparison of agent-runtime ergonomics. Before adoption, verify authentication model, licensing/terms, deployment model, subprocess/runtime requirements, tool permissions, telemetry, cost, cancellation and compatibility with the backend. It must not own AgentDock's global task state or bypass the Policy Engine.

### GitHub MCP Server

Reference: https://github.com/github/github-mcp-server

Use as a reference for GitHub integration design: selectable toolsets, individual tool allowlists, strict read-only mode, scope filtering and lockdown configuration. AgentDock's GitHub connector should begin with the minimum read-only scopes needed for repository inspection. Add write operations individually, with exact resource scoping, approval, idempotency and audit. Read-only mode is a hard capability boundary, not a prompt instruction.

### Agent Skills open standard

References: https://github.com/agentskills/agentskills and https://agentskills.io/specification

Adopt the portable folder format for AgentDock's internal role procedures where useful:

```text
skills/
  research/
    SKILL.md
    references/
  github-read/
    SKILL.md
    references/
  code-review/
    SKILL.md
    references/
  android-qa/
    SKILL.md
    scripts/
    references/
```

Use the specification's progressive-disclosure lifecycle: discovery (name and description), activation (load the full SKILL.md only when relevant), then execution (load referenced resources or run bundled scripts only when needed). Keep each skill focused; move bulky references into separate files. Validate required frontmatter, skill name/description, compatibility, license, path traversal, symlinks, file size and resource integrity. Run bundled scripts in a constrained environment with only the declared capabilities. A skill is guidance, not an authorization grant; backend policy still gates every tool call. Retrieved skill content and resources are untrusted input and cannot override system policy.

## 3. Security and reliability skills

### Trail of Bits

Reference: https://github.com/trailofbits/skills

Prioritize relevant audit methods rather than installing the entire collection:

- `audit-context-building`: build an architectural map before making vulnerability claims.
- `differential-review`: review security-sensitive diffs and their blast radius.
- `insecure-defaults`: find fail-open settings, hardcoded credentials and unsafe defaults.
- `agentic-actions-auditor`: use when a workflow invokes AI coding agents or accepts attacker-controlled issue/PR input into an agent run; not needed for workflows that do not invoke an agent.
- `static-analysis`: use CodeQL/Semgrep and interpret SARIF findings where supported by the repository.
- `supply-chain-risk-auditor`: review dependency advisories, upstream maintenance, publisher concentration and install scripts.
- `testing-handbook-skills`: select fuzzing, sanitizers, coverage and static-analysis methods for parsers, policy and state-machine code.
- `fp-check`: independently verify security findings before treating them as confirmed.
- `sharp-edges`: inspect error-prone APIs and dangerous configuration boundaries.

Do not install every Trail of Bits skill blindly. Inspect each skill's scripts, network access, shell commands, permissions and license before admitting it.

### OWASP GenAI / LLM risks

Reference: https://owasp.org/www-project-top-10-for-large-language-model-applications/ and https://owasp.github.io/www-project-top-10-for-large-language-model-applications/

Translate the relevant risks into explicit controls:

- Prompt injection: isolate instructions from repository, web, issue and tool-output data; never let retrieved text grant authority.
- Insecure output handling: schema-validate tool arguments and generated structured output; encode content before rendering.
- Sensitive information disclosure: redact logs, scope memory and prevent secrets entering prompts unnecessarily.
- Excessive agency: server-side policy, least privilege, action-bound approvals, budgets and tool allowlists.
- Insecure plugin/tool design: per-tool auth, schemas, timeouts, output limits and audit.
- Model denial of service: token, concurrency, time, retry and spend limits.
- Supply-chain risk: pinned dependencies, SBOM, provenance and vulnerability review.
- Overreliance: Definition of Done and independent evidence, not model confidence or model-to-model agreement.

## 4. Android engineering skills

References: https://github.com/android/skills and https://developer.android.com/tools/agents/android-skills/browse

Use the official Android skills as task-specific implementation/review playbooks. The Android skills project explicitly targets workflows where agents underperform; it is not a reason to reinstall generic Compose guidance or migrate navigation without need. Select from the currently published skills relevant to the touched code:

- Optimize Compose performance.
- Make app adaptive.
- Use Android profilers and analyze Perfetto traces.
- Audit and fix ANRs.
- Audit and fix permission security; secure Android Intents.
- Set up testing strategy.
- Audit R8 configuration and make app edge-to-edge.
- Review AppFunctions / on-device agent skills only if AgentDock introduces a concrete on-device integration.
- Set up Navigation 3 only if a separate navigation evaluation justifies migration.

For AgentDock's current Compose stack, prioritize stable lazy-list keys, lifecycle-aware collection, immutable UI state, deferred state reads, bounded streaming batches, off-main work, adaptive window sizing and accessibility. Profile release-like builds and use Macrobenchmark/Baseline Profiles for real user journeys. Do not chase a universal FPS number or infer device capability from OEM model names.

## 5. UI/UX and motion reference map

The native app remains the target. Web libraries are research material; do not add React, CSS, WebGL or a web runtime to Android to copy a visual effect.

| Reference | Apply to AgentDock | Boundary |
|---|---|---|
| Mobbin — https://mobbin.com/ | Study real mobile task flows, navigation, settings, empty/error states and information hierarchy. | Observe patterns; do not clone screens. |
| BeUI — https://www.beui.com/ | Mobile component and interaction inspiration. | Translate to native Compose/Material 3. |
| 21st.dev — https://21st.dev/ | Search real component patterns and inspect implementation details. | Use as design research, not a dependency requirement. |
| ReactDrops — https://reactdrops.dev/ | Explore agent-state indicators and distinctive interaction ideas. | Avoid glass/orb effects by default; adapt only useful state communication. |
| Astryx — https://astryx.atmeta.com/ | Reference for chat composer, tool-call display, status, command palette and data-dense app patterns. | Select only patterns that reduce interaction cost. |
| EvilCharts — https://evilcharts.com/ | Dashboard visualization patterns for task throughput, latency, reliability and usage. | No fake telemetry; charts only when data supports a decision. |
| Transitions.dev — https://transitions.dev/ | Reference motion recipes and shared duration/easing vocabulary. | Motion must remain purposeful, interruptible and reduced-motion compatible. |
| Kinetics — https://kinetics.colorion.co/ | Explore motion behavior and interaction timing. | Do not introduce motion for decoration. |
| React Bits — https://reactbits.dev/ | Visual/interaction inspiration for web companion surfaces. | Not a native Android component source. |
| Magic UI — https://magicui.design/ | Selective web visual research. | Avoid gradient-heavy/marketing effects in the mobile control plane. |
| Aceternity UI — https://ui.aceternity.com/ | Reference for interaction composition and web patterns. | Do not import effects that hurt clarity or performance. |
| Uiverse — https://uiverse.io/ | Small interaction ideas. | Review accessibility, code quality and licensing before reuse. |
| 60fps — https://60fps.design/ | Motion/performance inspiration. | Verify behavior on actual Android devices. |
| Land-book / Awwwards / Recent.design | Typography, hierarchy and web visual research. | Avoid turning the app into a marketing landing page. |

### Emil Kowalski Design Engineering

Reference: https://github.com/emilkowalski/skills

Use the saved skills as a quality gate:

- `emil-design-eng`: primary design-engineering and motion judgment reference.
- `animate`: create a motion treatment with considered curve, duration and properties.
- `review-animations`: strict review of existing motion.
- `improve-animations`: whole-codebase animation audit and prioritized repair plans.
- `find-animation-opportunities`: identify the few interactions that benefit from motion, and explicitly reject unnecessary animation.
- `animation-vocabulary`: describe motion precisely when a design decision requires it.
- `pick-ui-library` and `prototype`: useful for web companion/UI exploration, not reasons to add a dependency to native Android.
- `apple-design`: web-oriented interpretation of Apple design principles; use as general critique only, not as an Android platform specification.
- `animate-expo`: React Native/Expo only; not applicable to the current Kotlin/Compose app.

AgentDock rule: no animation for frequent actions; use short, interruptible feedback for occasional state changes; remove motion that delays task control. Prefer native Compose animation APIs and system motion preferences over adding a web animation stack.

## 6. MCP and integration design

Use Anthropic's `mcp-builder` and official MCP specification/SDK documentation when implementing MCP servers or clients. Use GitHub's official MCP server as a concrete integration reference. Every server/tool must have:

- a declared owner, purpose and versioned schema;
- explicit authentication and credential scope;
- allowlisted operations and resource boundaries;
- argument validation, timeout, output-size limit and cancellation;
- policy evaluation immediately before execution;
- approval for consequential side effects;
- idempotency/receipt strategy where possible;
- redacted audit event and inspectable evidence.

MCP discovery is not authorization. Tool descriptions and returned content are untrusted data. Do not expose unrestricted shell, arbitrary network, filesystem or repository write access to the Main Agent.

## 7. Anthropic skills to apply

Reference: https://github.com/anthropics/skills

- `frontend-design`: use for web companion UI or design exploration, then translate patterns to Compose.
- `webapp-testing`: use Playwright for any web console/companion UI and end-to-end browser tests.
- `mcp-builder`: use for MCP server/client design and validation.
- `skill-creator`: use to author, validate and maintain AgentDock's own skills.
- `docx`, `xlsx`, `pptx`, `pdf`: use only when AgentDock later needs document artifact generation or analysis.
- `algorithmic-art`: not a core AgentDock requirement; do not add art-generation features merely because the skill exists.

## 8. Graphics and other saved engineering references

These are optional and mostly not part of the Android core:

- Penpot (https://penpot.app/) for design-system exploration and handoff.
- Excalidraw (https://excalidraw.com/) for task flows, state machines and architecture diagrams.
- Lucide (https://lucide.dev/) for icon reference; retain a consistent native icon implementation.
- GSAP (https://gsap.com/), Three.js (https://threejs.org/), Remotion (https://www.remotion.dev/), FFmpeg (https://ffmpeg.org/), Blender (https://www.blender.org/), Rapier (https://rapier.rs/), MediaPipe (https://mediapipe.google.com/), Vanta (https://www.vantajs.com/), Zdog (https://zzz.dog/), glfx.js (https://github.com/evanw/glfx.js), PixiJS filters (https://github.com/pixijs/filters) and VFX-JS (https://github.com/fand/vfx-js) are not default dependencies for AgentDock. Consider them only for a concrete artifact or companion-web requirement with a performance and maintenance case.
- Android-specific Liquid Glass experiments are not the visual direction for AgentDock; keep the app native, restrained and efficient.


## 9. Application order

1. **Now — baseline discipline:** preserve current architecture; add security and reference review to implementation checklists; maintain the current single-agent-first roadmap.
2. **Before first remote task:** finish authentication/session revocation, task API, idempotency, ordered events, worker leases and recovery.
3. **First agent slice:** one provider adapter, one Main Agent, one read-only tool, bounded context and evidence-backed verification.
4. **Before side-effect tools:** Policy Engine, exact-action approvals, audit, credential broker and tool-specific tests.
5. **Before specialist agents:** establish single-agent evaluation baseline; add Researcher/Developer/Tester/Reviewer only when measurable.
6. **Before coding execution:** isolate workspace, deny-by-default egress, resource limits, secret isolation and sandbox tests.
7. **Before visual expansion:** validate task timeline and approval UX with real mobile references; keep the Android UI quiet and native.
8. **Before production:** threat model, supply-chain review, accessibility, device matrix, backup/restore, privacy/consent review and signed update-over-install tests.


## 10. GitHub Actions supply-chain baseline

GitHub recommends pinning third-party actions to a full-length commit SHA because version tags can be moved. GitHub Actions workflow dependency lockfiles reached general availability on 2026-10-28 for GitHub Free, Team and Enterprise Cloud. A lockfile at `.github/workflows/actions.lock` can pin direct and transitive action dependencies and is stronger than reviewing only the direct `uses:` entries. It is generated by the official `gh actions-lock` extension and must never be hand-edited.

Adoption procedure for a connected checkout:
1. Confirm every workflow's direct action references are the intended upstream releases; review tags, source repositories and full commit SHAs.
2. Install the official extension: `gh extension install github/gh-actions-lock`.
3. Generate the lockfile: `gh actions-lock`.
4. Review every workflow rewrite and every lockfile owner/repository identity and commit. Do not accept moved tags or repository identity changes automatically.
5. Verify the checked-in result without changing files: `gh actions-lock --verify`.
6. Keep Dependabot enabled for GitHub Actions. For already-onboarded workflows, Dependabot regenerates corresponding lockfile entries when it updates action versions.
7. Add a CI verification step for `gh actions-lock --verify` after the initial lockfile is committed; only then consider a repository-level Require lockfile policy.

Current AgentDock status: workflows use verified full-SHA direct pins, but `.github/workflows/actions.lock` has not been generated or committed. Do not claim transitive dependency locking or policy enforcement until the generated file and CI verification exist. The current editing environment did not have GitHub CLI or a working checkout/network, so generation is a follow-up operation for a connected development environment.

Keep a human-readable upstream release comment beside each immutable SHA until lockfile adoption is complete. Review all updates rather than automatically approving dependency changes. Do not copy an abbreviated SHA into a workflow.

The following pins were verified against upstream release commits on 2026-11-05:

| Action | Release | Commit SHA |
|---|---|---|
| `actions/checkout` | `v7.0.1` | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | `v6.1.0` | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` |
| `gradle/actions` | `v6.4.1` | `8da46b539cd8fe6ab27ca75128919a18d757604c` |
| `android-actions/setup-android` | `v4.0.4` | `be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd` |
| `actions/setup-python` | `v6.3.0` | `ece7cb06caefa5fff74198d8649806c4678c61a1` |
| `actions/upload-artifact` | `v7.0.2` | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |

The Android emulator runner in the existing Android workflow was already pinned to a full commit SHA and remains unchanged. The selected `gradle/actions` release includes the upstream fix for GHSA-chpw-2mqm-95cc.

Workflow permission boundaries remain explicit: Android/backend CI use `contents: read`; APK publishing uses `contents: write` because it creates a GitHub Release. All three workflows also set `persist-credentials: false` on checkout; the release job provides `GH_TOKEN` only to the release-creation step, rather than retaining checkout credentials in Git configuration. The repository currently has no AI coding-agent Actions step, so Trail of Bits' `agentic-actions-auditor` is not applicable to the current workflows; run it if an AI agent is later introduced into CI. Apply general GitHub Actions security review to all workflows regardless.

Before changing a pin, verify the exact upstream release, full commit SHA, action compatibility and Node runner requirements. Re-run all affected workflows after updates. A pin table is a dated record, not a promise that the selected version remains latest.

Dependabot is configured in `.github/dependabot.yml` for GitHub Actions, Gradle across `/`, `/app` and `/macrobenchmark`, and pip under `/backend`, on a weekly Asia/Ho_Chi_Minh schedule. Shared Gradle dependency updates are grouped by dependency name across modules; version-update PR limits are bounded per ecosystem. The configuration only requests update PRs; it does not approve or merge them. Every PR still requires compatibility review and the appropriate CI checks. Security-update enablement is a separate repository setting and must be verified in GitHub rather than assumed from this file.


## 11. Acceptance gates for every future change

- Research current official documentation and inspect the exact repository files before editing.
- Identify compatibility, licensing, security, privacy, accessibility, performance and migration risks.
- Do not add a dependency when a small native implementation is sufficient.
- Make changes on a feature branch; inspect ancestry and diffs before merge.
- Re-read changed files and surrounding code after editing.
- Run the narrowest relevant tests, then the relevant full CI checks.
- Report only checks actually executed and distinguish code present from planned behavior.
