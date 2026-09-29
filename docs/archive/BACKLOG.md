# Current scope: Distributed Systems Simulator

The owner explicitly replaced the live backend with a browser-only educational simulation. The old plan below is historical and must not drive implementation.

Current priorities: preserve the six interactive lessons, make limitations and effects understandable, keep startup static-only, and use short focused tests with regular commits. No services, data imports, load generators, observability or long live tests. A Medium walkthrough is future work. See [README](README.md) and [simulation design](docs/SIMULATION.md).

---

# Ordered backlog

PLAN.md is authoritative. Do not run later phases concurrently with an incomplete earlier gate.

For item-by-item Cursor implementation use [CURSOR](CURSOR.md), the [task index](docs/implementation/README.md) and [STATE](docs/implementation/STATE.md). A01 is implemented and waiting for Codex review V0. Do not start A02 from this overview. This backlog is not a second completion ledger.

- Phase 0: design/prototype delivered; owner authorized the Java baseline continuation. Licence/hosting remain open.
- Phase 1: read-only Java catalog API delivered; atomic reservations, tenant/run isolation, migrations and remaining quality gates are next.
- Phase 2: Java simulation/control plane, React lab shell, guided baseline journey.
- Phase 3: bounded real traffic and telemetry; scaling/connection-pool lessons.
- Phase 4: Redis and cache correctness lessons.
- Phase 5: PostgreSQL read replicas, read routing and recovery.
- Phase 6: tenant sharding, skew and migration.
- Phase 7: blue/green deployment, timeouts and backpressure.
- Phase 8: recovery evidence, polish, public simulation release and case study.
- Optional after core: asynchronous reservation expiry worker/outbox, broker comparison, autoscaling, real synchronous replication, multi-host consensus/partition lab, PITR automation. None are core prerequisites.
