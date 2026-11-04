# AgentDock documentation

This directory separates **verified implementation state**, **approved product design**, and **future work**. The root [README](../README.md) and repository code remain the source of truth for what is actually implemented.

## Start here

| Document | Purpose | Status |
|---|---|---|
| [Product specification](PRODUCT_SPEC.md) | Product promise, user journeys, interaction rules and AI behavior | Design baseline |
| [Architecture](ARCHITECTURE.md) | Android/backend boundaries, persistence and execution responsibilities | Design baseline; reconcile with current code |
| [Implementation plan](IMPLEMENTATION_PLAN.md) | Phased, testable delivery sequence | Plan only |
| [Roadmap](ROADMAP.md) | Completed foundations and remaining delivery gates | Evidence-based status |
| [Technical research and decisions](TECHNICAL_RESEARCH.md) | Current external references and engineering decisions | Research baseline |
| [Design reference guide](DESIGN_REFERENCE_GUIDE.md) | How to use the saved design and engineering references | Guidance |

## Operational and trust documents

- [Authentication and session contract](AUTHENTICATION.md) — provider-neutral backend identity mapping and revocable session design; check active branch and CI state for implementation validation.

- [Device performance](DEVICE_PERFORMANCE.md) — adaptive behavior and benchmark acceptance.
- [Privacy policy](PRIVACY_POLICY.md) — **draft, not approved for publication**.
- [Terms of service](TERMS_OF_SERVICE.md) — **draft, not approved for publication**.
- [Updates and releases](UPDATES_AND_RELEASES.md) — update protocol and safety requirements.
- [Release process](RELEASE_PROCESS.md) — current GitHub Actions signing/release procedure.

## Reading the status correctly

- A design or plan document does not mean the feature is implemented.
- Check the current branch, source files, migrations, automated checks and release records before making an implementation claim.
- Privacy/Terms documents contain unresolved operator, processor, retention and legal placeholders. They must not be presented as final legal notices until the actual data flow and operator details are verified and legal review is complete.
- The update specification is not an in-app updater. A GitHub Release is not proof that in-app update verification and installation work.
- Do not mark a roadmap item complete without code and recorded test evidence.
