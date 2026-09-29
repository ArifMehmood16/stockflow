# Ordered backlog

PLAN.md is authoritative. Do not run later phases concurrently with an incomplete earlier gate.

For item-by-item Cursor implementation use [CURSOR](CURSOR.md), the [task index](docs/implementation/README.md) and [STATE](docs/implementation/STATE.md). Next task: A01; next Codex review: V0. This backlog is an overview, not a second completion ledger.

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
