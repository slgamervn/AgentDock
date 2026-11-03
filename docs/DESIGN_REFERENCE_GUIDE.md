# AgentDock — Design and engineering reference guide

This guide records how to apply the project's saved reference collection. References are research tools, not requirements to copy a layout, add a dependency or reproduce an effect.

## Primary references

### Design intent and quality

- [Anthropic Skills](https://github.com/anthropics/skills) — use **frontend-design** to plan a visual direction before implementation; **webapp-testing** for repeatable UI behavior checks; **skill-creator** for maintainable task instructions; **mcp-builder** when adding an MCP server or integration.
- [Emil Kowalski Skills](https://github.com/emilkowalski/skills) — use **emil-design-eng** for implementation detail, **animate** for motion decisions, and **review** / **improve** / **remove-animations** for critique and reduction. Motion must clarify user intent, remain interruptible and respect reduced-motion settings.

### Mobile interaction research

- [Mobbin](https://mobbin.com/) and [BeUI](https://www.beui.com/) — inspect real mobile information architecture, task flows, settings, empty states and interaction details.
- [21st.dev](https://21st.dev/) — useful for component patterns and layout exploration; translate the underlying interaction into native Compose rather than importing web components.
- [Land-book](https://land-book.com/), [Awwwards](https://www.awwwards.com/) and [Recent.design](https://recent.design/) — use selectively for visual hierarchy and typography references. They are primarily web references, not a mandate to make AgentDock resemble a landing page.
- [60fps](https://60fps.design/) and [Transitions.dev](https://transitions.dev/) — inspect motion and transition examples, then retain only motion that improves comprehension or feedback.

### Engineering and prototyping

- [Penpot](https://penpot.app/) for design-system exploration and handoff.
- [Excalidraw](https://excalidraw.com/) for task flows, state machines and architecture sketches.
- [Lucide](https://lucide.dev/) for consistent icon metaphors; use a small, coherent icon set and preserve accessible labels.
- [Anthropic Skills](https://github.com/anthropics/skills) and [Android Developers](https://developer.android.com/topic/architecture) for implementation and testing discipline.

## AgentDock's visual rules

1. **Native before novelty.** Use Material 3 and Compose primitives where they fit. Create a custom component only when it improves a repeated interaction or information hierarchy.
2. **One memorable decision, not many effects.** The task timeline and live execution state are the distinctive product surface. Keep the rest of the UI quiet and legible.
3. **No generic AI chrome.** Avoid decorative gradients, blur, glowing panels, fake telemetry, endless rounded cards, meaningless metrics, animated particles and excessive badges.
4. **Make state visible.** Distinguish local draft, queued task, running task, waiting for approval, verifying, completed, failed and cancelled. Never make a visual state look more successful than the underlying evidence.
5. **Use ordinary language.** Buttons describe the action they perform. A success message describes the persisted result, not an optimistic request.
6. **Progressive disclosure.** Most people should be able to submit and monitor a task without understanding providers, MCP, models or tokens. Put advanced configuration under Settings.
7. **Accessible by default.** Support scalable text, TalkBack, sufficient contrast, touch targets, keyboard navigation and reduced motion.
8. **Motion is functional.** Use subtle pressed/expanded/loading transitions only where they show a state change. Avoid looping animation and motion that delays a response.

## Review checklist

- Is the primary action immediately obvious?
- Can a new user tell a draft from a server task?
- Can they see who/what is acting, which tools are involved, and what requires approval?
- Does a completed task expose its result and evidence?
- Are failure, offline, retry, pause and cancellation states understandable?
- Does the screen work on a narrow phone, a large phone, landscape and a wide window?
- Does the screen remain clear when animations are disabled and system font size is enlarged?
- Can every visible control be reached with TalkBack and keyboard navigation?
- Did an effect, card, metric or setting get added without solving a user problem? Remove it.

## Do not blindly port web design libraries

React Bits, Magic UI, Aceternity, Uiverse, GSAP and Three.js are primarily web/animation/graphics ecosystems. They may provide an idea, but they are not reasons to add a web runtime or heavy effect libraries to a Kotlin Android app. Keep AgentDock small, native and responsive.
