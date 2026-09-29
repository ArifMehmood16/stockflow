# AI development log

## 001 — Design StockFlow and deliver an interactive prototype

Date: 2026-09-29. Tool: Codex. Phase: 0. Status: draft for human review.

### User request and human-owned decisions

Create a portfolio project plan showcasing Java and distributed systems, database scaling, replication, sharding, caching/Redis, failure recovery and blue/green deployment. Deliver architecture, UI/UX, wireframes, web view, flows and a complete developer handoff. Create a GitHub repository and a directory under the portfolio workspace. Use Make locally and provide Docker as an alternative; respect machine/service limits. User explicitly requested a more specific project name, public visibility and commits as work proceeds.

The agent proposed StockFlow, a tenant inventory teaching lab, plus exact framework/domain/runtime choices recorded as proposed in ADR 001. These proposals are not represented as already approved by the human. Licence and future hosting remain human decisions. No Java implementation phase began.

### AI contribution

Created product specification, ordered phase plan, 13 experiments, architecture/sequence diagrams, API/event/data contracts, Make/Docker operations design, resource budgets, testing/evaluation strategy, threat model, UX wireframes/flows, handoff and project governance. Built a six-chapter browser prototype with explicit illustrative-model labels, animated routes, inspection, guide actions, failure/fencing/promotion and reduced-motion control. Added Node standard-library preview server, test runner usage and documentation checks; no npm dependencies.

### Alternatives rejected or changed

- Generic Systems Lab name/private visibility changed to StockFlow/public following explicit human feedback.
- All services running at once rejected in favour of separate resource profiles.
- Full commerce service fleet, Kubernetes, broker and service mesh deferred: they obscure the first lessons and increase laptop cost.
- Fake latency/throughput presented as real telemetry rejected. Delivered arithmetic model is visibly illustrative; future simulation and measured real lab have distinct provenance.
- Automatic promotion on network failure rejected; explicit fencing/lag review/data-loss reporting required.
- A shared frontend/Java simulation algorithm rejected; browser prototype is disposable, authoritative simulation will be Java.

### Evidence

Tests: initial module-not-found scaffold failure, then four observed behavioural failures and one pass; final five model tests passed. Browser verified overload, unchanged DB bottleneck after API scale, cache demand reduction, inspector, blocked unfenced promotion, recovery and reduced motion. Exact commands, later verification and limitations in docs/engineering-journal.md. No real database benchmark was run.

### Publication and resume

Initial checkpoint commit `c1e964b` was pushed to `codex/phase-0-stockflow-design-and-prototype`. Subsequent verified checkpoint commits are discoverable with `git log`; do not invent hashes before committing. RESUME.md records the latest handoff state. Source RAG project remains unchanged. Public repository contains synthetic material and no generated credentials, local data stores or secrets.

### Follow-up

Human reviews this log and ADR 001. Run optional Docker smoke when daemon is available. Begin Phase 1 only after design review. No destructive or paid infrastructure actions were performed.
