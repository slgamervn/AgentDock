# AgentDock product specification

**Status:** Design baseline for implementation. This document describes the intended product; it does not claim that planned features are implemented.

## 1. Product definition

AgentDock is an Android-first control plane for a durable, multi-provider Agentic AI service. A user describes an outcome in natural language. AgentDock understands the request, chooses the smallest sufficient workflow, uses authorized tools, verifies the result, and presents a clear outcome.

The Android app is the user's workspace and control surface. Backend services own identity, authorization, orchestration, durable task state, provider credentials, tool execution and recovery. A task accepted by the server must continue independently of the Android process.

## 2. Product promise

1. **Easy to start:** the user can submit a task without learning agent terminology, model names or tool configuration.
2. **Easy to understand:** the app shows what is happening, what needs approval and what has actually completed.
3. **Reliable:** important claims and completed actions are supported by inspectable evidence.
4. **Natural:** language follows the user's intent, context, language and preferred level of detail without sounding like a rigid template.
5. **Respectful:** no manipulative engagement loops, unnecessary notifications or hidden actions.
6. **Efficient:** simple requests use a simple path; complexity is introduced only when it improves the outcome.

## 3. Primary user journeys

### 3.1 Ask and complete

- User enters a natural-language goal.
- Client creates a server task and immediately shows its accepted state.
- Main Agent classifies intent, ambiguity, risk, freshness needs and complexity.
- The workflow chooses direct response, one-agent tool use, or bounded specialist delegation.
- Task events stream to the client; reconnect replays missed events.
- The verifier checks the Definition of Done and attaches evidence.
- The user receives a concise result, limitations and relevant artifacts.

### 3.2 Continue later

- Home shows active and recently completed work with meaningful labels.
- Opening a task restores its timeline, artifacts, approvals and context.
- A local draft is clearly distinguished from a server task.
- The user can pause, resume or cancel where the current state permits.
- Closing or restarting the app does not discard accepted server work.

### 3.3 Approve an action

- Before a consequential side effect, the server creates an approval request.
- The approval screen states the exact action, target, important arguments, risk and expected effect.
- Approval is bound to an immutable action/arguments/resource digest, policy version and expiry.
- If the action materially changes, the previous approval is invalid and must be requested again.
- The client cannot grant itself permission or bypass backend policy.

### 3.4 Observe a screen (later phase)

- Screen assistance is user-initiated and visibly active.
- Android obtains the required MediaProjection consent for each capture session.
- Observe mode explains/highlights content but does not control other apps.
- Any future action mode is limited to documented, user-approved Android intents or app APIs; no unrestricted autonomous tapping.
- Protected content, FLAG_SECURE surfaces and permission boundaries are respected.
- Screen content is not retained or uploaded beyond the declared purpose and retention policy.

## 4. Interaction and language principles

### 4.1 Natural conversation

- Interpret intent using the current message, relevant conversation history and project context.
- Handle typos, abbreviations, missing punctuation, mixed Vietnamese/English and common informal phrasing.
- Resolve short follow-ups such as "tiếp", "làm đi" and "cái trước" only when the referent is sufficiently clear.
- Ask one concise clarification when ambiguity changes the action or outcome.
- Adapt tone and detail to the user's task; do not force every answer into a fixed template.
- Do not imitate human identity, claim feelings/experiences, or pretend to have completed work.
- Separate confirmed facts, inferences, assumptions and unknowns when the distinction matters.

### 4.2 Vietnamese-first, multilingual-ready

- Vietnamese is the initial default UI and conversation language.
- Support natural Vietnamese, regional wording, common abbreviations, code-switching and domain terminology.
- Keep internal identifiers and API contracts language-neutral; localize user-facing strings.
- Do not silently rewrite user input or treat slang as a permanent preference.

### 4.3 Terminology and freshness

- Maintain a versioned terminology/knowledge catalog with term, language, definitions, context, variants, source, observed date, verified date, confidence and review status.
- Discover candidate new terms from approved sources and user-authorized material.
- Verify meaning against multiple reliable contexts before promoting a term to the trusted catalog.
- Distinguish emerging slang, domain jargon, proper nouns and established terminology.
- Record semantic changes over time; do not overwrite historical meanings.
- A model's training cutoff is not a live update mechanism. Use retrieval/search for current facts and show source dates where freshness matters.
- Never silently train or fine-tune on private conversations. Memory and feedback are governed by consent, privacy controls and provenance.

## 5. Simple and rewarding UX

### 5.1 Information architecture

Primary navigation should remain small:
- **Home:** ask, continue active work and revisit recent results.
- **Workspace:** task conversation, progress, approvals and artifacts.
- **Activity:** task history and event timeline.
- **Settings:** account, providers/connections, privacy, language, accessibility and performance.

Advanced configuration is progressively disclosed rather than shown on the first screen. Model/provider names are optional diagnostics, not required user decisions.

### 5.2 Interaction rules

- One obvious primary action per screen.
- Useful defaults; minimal setup before the first successful task.
- Keep task status, next action and approval requirement visible.
- Prefer inline explanations and contextual controls over nested settings.
- Provide undo/recovery for reversible local changes.
- Preserve drafts and task context.
- Empty, loading, offline, permission-denied and failure states must explain what the user can do next.
- Notifications are opt-in, relevant and rate-limited. No streaks, artificial urgency or manipulative engagement mechanics.

### 5.3 Visual direction

- Minimal, functional, human-crafted and native Android.
- Neutral dark/light surfaces, restrained emerald accent (#3dba7a) and amber accent (#e6b35a).
- Use cards only for meaningful grouping; avoid decorative gradients, excessive blur, glow, pills and badges.
- Clear typography, consistent icons, comfortable touch targets and deliberate whitespace.
- Motion communicates state changes; it must not delay or obstruct actions.
- The interface must remain clear with animations and decorative effects disabled.

### 5.4 Accessibility and adaptive layout

- Support TalkBack semantics, scalable text, sufficient contrast, keyboard navigation and reduced motion.
- Adapt by available window size, not device brand/model; support phone, tablet, foldable, landscape, split-screen and resizable windows.
- On expanded layouts, use list-detail or navigation-rail patterns where they improve navigation.
- Do not lock the app to portrait.

## 6. AI behavior contract

### 6.1 Adaptive execution

Choose the least complex path that meets the task's quality and safety requirements:

1. Direct deterministic code or retrieval for well-defined non-generative operations.
2. One model call for simple conversation or reasoning.
3. One agent with tools for bounded tool use.
4. A small, bounded specialist workflow for genuinely separable complex work.
5. Independent verification when the result or action warrants it.

Do not use multiple agents merely to appear sophisticated. Parallelize only independent work. One Main Agent owns the user-facing response and coordinates bounded specialists.

### 6.2 Model routing

A provider adapter normalizes request, streaming, tool-call, usage, timeout and error behavior. The router selects a deployment based on:
- required capability (text, vision, audio, structured output, tool calling, context size);
- task complexity and quality requirement;
- freshness and data-handling requirements;
- provider health, latency and availability;
- user/tenant policy, region and data controls;
- budget, quota and resource limits.

A model name is not a capability guarantee. Capabilities are verified against provider documentation and integration tests. Fallback is allowed only when policy and task semantics permit it; never blindly replay a non-idempotent side effect.

### 6.3 Accuracy and verification

Every task defines a Definition of Done and appropriate evidence before execution. Evidence may include source citations, tool receipts, file diffs, test results, database read-back or observed application state.

- No evidence means no claim that an external action completed.
- Model self-confidence and agreement between models are not proof.
- Detect conflicting sources and communicate unresolved uncertainty.
- If evidence is insufficient, ask, qualify or abstain.
- Verification depth scales with risk and consequence.
- Approval is a policy decision, not a confidence threshold.

### 6.4 Self-correction

- Retry only for classified transient failures and within bounded limits.
- Use exponential backoff with jitter for retryable infrastructure errors.
- Replan when evidence shows the current plan is invalid.
- Preserve failed attempts and diagnostics for audit.
- Avoid repeating completed side effects; use idempotency keys and execution receipts.
- Stop on budget, deadline, attempt, policy or safety limits.

## 7. Memory and context

Memory types:
- **Working:** current task state and relevant messages.
- **Episodic:** concise records of prior completed tasks with provenance.
- **Preference:** user-approved language, response and workflow preferences.
- **Project:** project-specific conventions, files, decisions and terminology.
- **Semantic:** verified reusable facts and knowledge.

Each memory item has scope, source, timestamp, confidence, sensitivity classification, retention and deletion behavior. Retrieval is relevance- and permission-filtered. Users can inspect, edit, export and delete memory and pause memory features. Never store passwords, OTPs, provider secrets or unnecessary sensitive content.

Context assembly is budgeted and layered: system/policy → task goal and acceptance criteria → relevant project context → selected memory → recent conversation → retrieved evidence. Summarize with provenance; do not silently drop constraints or treat retrieved text as instructions.

## 8. Trust, privacy and safety

- Authenticate users and authorize tenant membership on the server.
- The client-supplied tenant ID is only a selector; it never proves membership.
- Validate identity, begin transaction, set candidate tenant context, verify membership through a tenant-scoped query, then access tenant data; rollback and reject if membership is missing or inactive.
- Runtime database role is non-owner, NOSUPERUSER and NOBYPASSRLS.
- Enforce policy immediately before every tool/action execution.
- Treat prompts, repositories, web pages, tool output and retrieved documents as untrusted data.
- Store provider credentials server-side; use narrow, short-lived credential grants.
- Sandbox code execution with non-root identity and CPU, memory, disk, process, time and network limits.
- Redact credentials and unnecessary private content from logs/traces.
- Provide clear consent, data retention and deletion controls.

## 9. Performance and device strategy

- Backend owns durable tasks; Android owns presentation, local drafts and a cache.
- Room is introduced for structured task/event cache when remote sync is implemented; DataStore remains for small preferences.
- Use WorkManager for deferrable sync, not continuous agent execution.
- Use SSE for ordered task updates with replay and deduplication; defer WebSocket until a concrete bidirectional need exists.
- Render streaming text incrementally, batch deltas and avoid reparsing the full document for every token.
- Use stable keys, lazy lists, pagination, off-main parsing and bounded image/file caches.
- Adapt effects and background work to actual device memory, thermal state, battery and refresh capabilities.
- High refresh is a preference, not a guaranteed FPS promise. Measure frame time and jank on real devices.
- Keep animations purposeful and lifecycle-aware; respect system reduced-motion settings.

## 10. Success measures

Measure outcomes, not feature count:
- task completion rate and verified completion rate;
- factual accuracy, groundedness and unsupported-claim rate;
- correct intent and tool selection;
- median and tail latency by task class;
- cost per verified successful task;
- retry, recovery and duplicate-side-effect rates;
- approval comprehension and cancellation/error recovery;
- crash-free sessions, startup time, frame jank and battery/resource use;
- user-reported ease of starting, understanding and resuming work.

Targets are set after a representative baseline is collected. Do not invent universal accuracy or FPS percentages before measurement.

## 11. Explicit non-goals

- No requirement to use multiple agents for every request.
- No provider API keys or agent runtime embedded in the APK.
- No unrestricted autonomous control of other Android apps.
- No silent training on user conversations.
- No claim that a model's self-evaluation proves correctness.
- No mandatory Redis, vector database, WebSocket or microservice split before measured need.
- No dark patterns designed to increase app opening frequency.
