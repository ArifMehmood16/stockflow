> **Historical backend design — superseded.** The app is now a browser-only educational simulator. Use [the current simulation design](SIMULATION.md) and [README](../README.md). The content below is retained for context, not as an implementation backlog.

# Product brief

This is the full product vision. The [current delivery scope](implementation/SCOPE_REVIEW.md) first connects the existing workbench to real Java/PostgreSQL behavior. Detailed observability, complete simulator parity and portfolio packaging are deferred; requested cache, replica, shard and deployment lessons remain in scope as subsequent working features.

## Promise

StockFlow lets someone answer “what changed, why did it help, and what got worse?” by manipulating a real-looking architecture and following individual inventory requests. Its distinctive portfolio value is an honest bridge from visual intuition to Java code, SQL constraints and measured infrastructure behaviour.

Primary users: hiring engineers evaluating design judgement; developers learning distributed systems; the author explaining a failure live. A beginner needs a guided path; an engineer needs inspectable assumptions and evidence. Avoid a wall of unrelated toggles.

## Domain and invariants

Fictional warehouses hold real public Open Food Facts products with synthetic tenant assignments and quantities. Java bootstrap imports up to DATASET_ROWS valid unique products, capped by actual availability; see [dataset contract](dataset-and-scale.md). Workload mix defaults to 90% stock reads and 10% reservation/release writes. A write-heavy preset uses 40% reads / 60% writes; hot tenant preset sends 80% of traffic to one tenant; flash-sale preset targets one SKU. All quantities are bounded positive integers. No checkout, money or personal data.

Available stock never becomes negative. Reserve and release apply at most once per idempotency key and request hash. A reservation belongs to one tenant, warehouse and SKU; there are no cross-shard reservations. Reads declare eventual or session-consistent semantics. Stale display stock must never be used to approve a reservation. A request with an unknown write outcome is resolved through its idempotency record, not blindly repeated.

## Three honest modes

1. **Design prototype (delivered now):** browser-only aggregate capacity sketch, illustrative staged provisioning/readiness transitions, fixed 90/10 traffic mix, selected visible lesson controls. No network load. No latency measurement. All values labelled illustrative.
2. **Simulation (to build):** seeded Java discrete-event model, virtual time, bounded queues and explicit cache/replication/failure events. Runs offline with in-memory simulated state. Can teach topology larger than a laptop can host. Outputs labelled modelled, with engine version and seed.
3. **Real lab (to build):** bounded load against owned local Java/PostgreSQL/Redis processes. Real histograms, request traces, resource metrics and database versions. Controls enabled only when host prerequisites exist. Time follows wall clock; pausing stops new requests but cannot freeze WAL, TTL or in-flight transactions.

Users never mix simulation and real measurements in a comparison. Changing modes resets the active run after confirmation, preserving a report. “Live” means updating observation of the selected mode, not necessarily real infrastructure.

## Primary journey

Choose a lesson → see the starting architecture and hypothesis → select bounded workload → run baseline → locate bottleneck → predict result → apply one change → inspect request/data flow → compare equal-duration runs → answer a short reflection → move to next lesson. A right guide panel stays beside the architecture. Every lesson has a preconfigured safe reset.

## Screens

- Welcome: mode explanation, machine budget card, continue lesson and curriculum.
- Lab: lesson rail left; run controls/topology/metrics centre; guide or inspector right; request timeline bottom.
- Compare: before/after snapshots with provenance, configuration diff and integrity verdict. Same seed helps simulation; real runs need repeats and noise disclosure.
- Report: shareable sanitized JSON and printable summary; configuration, events, invariants, measured/modelled labels, trade-offs.
- Setup: available dependencies and profile limits; no automatic installations or download surprises.

## Success criteria

A first-time user can identify the overloaded component, apply cache, explain stale data and recover a primary within a 10-minute guided route. E2E verifies completing tasks; real usability timing is collected with humans, never invented. Every visible metric has a source, unit and window. Every fault has a scope, duration and recovery path. A keyboard-only user can perform the same operations. A developer can reproduce a run from exported configuration and fixture version.

## Release slices

Slice A: inventory correctness + overload visualization. Slice B: cache/replica/failure teaching. Slice C: sharding/deployment and comparison evidence. Complete core is an educational local lab, not a managed database platform or production chaos service.
