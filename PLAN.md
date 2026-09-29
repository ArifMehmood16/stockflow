# StockFlow delivery plan

## Execution policy

**Current checkpoint: Phase 0 delivery. Next: human review of ADR 001, then Phase 1.** Each phase adds one independently explainable capability. Build the whole core curriculum across phases; do not attempt the final topology first. Checkboxes record observed work, not intent. Time ranges below are planning estimates for one developer, not commitments; core build approximately 8–12 focused weeks, depending on infrastructure experience.

Each implementation task: inspect relevant code → smallest failing behaviour test → observe red → minimal green → refactor → focused and phase checks → full diff review → docs + AI log. Tests default offline; integration suites opt into local services. Stop at the gate.

## Phase 0 — Product and design package (this assignment)

- [x] 0.1 Create a dedicated directory and public GitHub repository named StockFlow.
- [x] 0.2 Specify product, curriculum, architecture, contracts, resource budgets and security boundaries.
- [x] 0.3 Deliver wireframes, flows and an interactive web prototype with honest model labels.
- [x] 0.4 Verify prototype model, document checks and record observed outcomes.
- [x] 0.4a Fix occupied-port preview startup with actionable guidance and a regression test.
- [ ] 0.4b Revise the prototype with on-map controls, staged builds/rerouting, local technology logos, a dark workbench and safe `make stop`.
- [ ] 0.5 Human approves ADR 001 proposals, scope, licence and visual direction.

Acceptance: another developer can locate responsibilities, schemas, lesson steps, limits, tests and implementation order without treating the prototype as a measured system. Verification details in the journal. Gate: 0.5 blocks production implementation, not delivery of this planning package.

## Phase 1 — Correct Java inventory baseline (1–1.5 weeks)

- [ ] 1.1 Scaffold Maven multi-module build (`inventory-service`, `lab-controller`, `contracts` only as needed), Java 25, Spring Boot; lock versions, toolchains and images. Add readiness/liveness contracts and architecture dependency guard.
- [ ] 1.2 Implement Inventory and Reservation use cases. Red: concurrent reservations cannot reduce available stock below zero. Real SQL atomic update and transaction; idempotency conflict/replay contracts.
- [ ] 1.3 PostgreSQL migration + fixture seed. Red: tenants cannot read or mutate each other's SKU; repeated release cannot increase stock twice. Add reservation expiry semantics.
- [ ] 1.4 Explicit request/response DTOs, errors and limits. Red: malformed quantity / tenant mismatch rejected safely; no exception body leaks.
- [ ] 1.5 Host Make path (no mandatory Docker), equivalent Compose baseline, tool doctor, CI build/static analysis/unit suite; explicit integration command.

Expected files: `pom.xml`, `mvnw`, `services/inventory-service/`, `infra/`, `scripts/`, `Makefile`, `compose.yaml`, `docs/`. Gate: isolated concurrent PostgreSQL tests, restart persistence, fresh-clone host + Docker smoke, meaningful coverage (target 80% line / 70% branch for domain/application), no stock or tenant invariant violation. Defaults should pass with no paid service. Capture baseline with raw fixture counts; no speed claim yet.

## Phase 2 — Simulation engine and guided frontend (1.5 weeks)

- [ ] 2.1 Java discrete-event simulator behind `LabRuntime`. Red: same seed/config/engine version produces identical event sequence; queues bounded; offered = completed + failed + dropped + in-flight delta.
- [ ] 2.2 Run lifecycle, typed command contracts, SSE snapshot/reconnect and bounded event buffers. Red: duplicate command is applied once; stale revision rejected; expired replay cursor triggers snapshot resync.
- [ ] 2.3 React feature shell, SVG topology nodes/edges, inspector, request timeline and right lesson panel. Port prototype visuals, not its aggregate capacity formula. Red: keyboard user completes a lesson with reduced motion.
- [ ] 2.4 Lesson state machine and run comparison. Red: clicking a button without receiving the required observation cannot complete the step; mode badges always present.

Expected files: `services/lab-controller/`, `frontend/src/features/{lab,lessons,inspector,comparison}/`, `contracts/`, `scenarios/`. Gate: complete deterministic single-database overload lesson, reconnect resync E2E, 60-second window/latency semantics match contracts, narrow screens usable.

## Phase 3 — Real load, observability and service scaling (1 week)

- [ ] 3.1 Fixed-target open-loop load generator with deadlines, bounded concurrency and dropped-schedule accounting. Red: slow responses do not silently lower offered load; emergency stop cancels dispatch.
- [ ] 3.2 Request IDs, bounded counters/histograms, JDBC pool metrics, trace sampling. Red: a sampled trace aligns to aggregate run counters without claiming exhaustive traces.
- [ ] 3.3 Add a second inventory instance through a small proxy. Red: stateless requests reach either instance, idempotency still holds, removed instance drains safely.
- [ ] 3.4 Lessons for connection pool saturation, slow queries/indexes, hot-row locks, virtual-thread limits and backpressure.

Gate: baseline vs extra-instance experiment uses same fixture and workload, records generator limits and CPU/RSS; connection budgets hold during deployments too. No automatic scaling claim from a manual instance button.

## Phase 4 — Redis and cache correctness (1–1.5 weeks)

- [ ] 4.1 Cache-aside stock display; DB remains authoritative for reservations. Red: cache outage cannot allow overselling; stale reads remain labelled.
- [ ] 4.2 Explicit stale-fill race demonstration and repair: per-key single-flight, TTL jitter, version-aware conditional cache updates/invalidation and primary reads for freshness-sensitive requests. Red: older data cannot overwrite a newer observed cache version.
- [ ] 4.3 TTL expiry storm, negative caching, hot-key and eviction experiments. Red: coalescing bounds database miss fan-out; `maxmemory` stays enforced.
- [ ] 4.4 Redis-down fallback with admission limit, timeout and circuit breaker. Red: fallback does not amplify traffic beyond the DB bulkhead.

Gate: stale values are allowed only under documented eventual-read semantics; UI shows measured hit/miss, version and staleness evidence. Multi-instance single-flight limitations documented. No cache used as stock authority.

## Phase 5 — Replication, read routing and failover (1.5 weeks)

- [ ] 5.1 Primary + streaming replica with lag telemetry and read-only credentials. Red: writes never go to a replica; lagged reads expose version mismatch.
- [ ] 5.2 Read-your-writes token, primary pinning and lag-aware fallback. Red: eligible reads at/above required version or explicit timeout; never false freshness success.
- [ ] 5.3 Failure state machine: detect → fence old primary → inspect lag/data-loss warning → explicit promotion → reroute → reseed old node. Red: promotion without verified fencing rejected; no dual write targets.
- [ ] 5.4 Separate crash restart/WAL recovery from failover; synchronous replication trade-off in simulation first.

Gate: kill-primary scenario produces measured RTO and known-write RPO report; expected async losses are disclosed; invariants checked on surviving committed records; old primary never auto-rejoins writable. Split-brain discussion includes real multi-host limitations.

## Phase 6 — Shards, skew and safe migration (1–1.5 weeks)

- [ ] 6.1 Two PostgreSQL shard primaries, stable tenant hash into 16 virtual buckets and versioned ownership map. Red: reads and writes for a tenant reach one owner; cross-tenant idempotency isolated.
- [ ] 6.2 Uniform versus hot-tenant workload. Red: a hot tenant remains hot after adding a shard; no promise that sharding fixes a hot row.
- [ ] 6.3 Bounded migration protocol: pause writes to selected bucket, drain in-flight operations, copy, verify, atomically change ownership epoch, resume, retire old copy after rollback window. Red: writes during migration retry or reject; no dual ownership; stale router rejected.
- [ ] 6.4 Inspect rows per shard, request distribution and cross-shard query trade-offs. No multi-shard transaction in core.

Gate: count/checksum and reservation invariants before/after migration; interrupted copy leaves original owner intact. Explain that table partitions in one PostgreSQL process are not this topology. Replicated shards combined topology stays optional if budget exceeded.

## Phase 7 — Blue/green and resilience (1 week)

- [ ] 7.1 Run two versioned pools; readiness warm-up; traffic switch; drain; rollback. Red: unready green cannot receive traffic; persisted idempotency works across versions.
- [ ] 7.2 Expand/contract migration exercise with compatible old/new versions. Red: rollback passes while schema is compatible; destructive contract step explicitly disables old-version rollback.
- [ ] 7.3 Timeout, capped retries with jitter, rate limits and circuit-breaker lessons. Red: retries obey end-to-end deadline/budget; unknown POST outcome queries idempotency result.

Gate: failures during switch are attributed, request version visible, in-flight operations drain or have explicit bounded timeout, rollback tested against real database. Blue/green is an atomic route switch; weighted rollout is a separately labelled optional canary.

## Phase 8 — Portfolio release and recovery evidence (1 week)

- [ ] 8.1 Backup/restore lesson and corruption drill in isolated disposable namespace; restore into a new database, verify integrity before routing. Explain why replicas are not backups.
- [ ] 8.2 Publish benchmark methodology, environment, raw summaries, known failures, screenshot tour and short case study with trade-offs.
- [ ] 8.3 Accessibility, 375/768/1440 layouts, reconnect/error states, performance budgets, documentation clean-room walkthrough.
- [ ] 8.4 Public simulation deployment with infrastructure controls absent; security review, dependency/image scans, licences and no secrets. CI minimum permissions and pinned actions.

Gate: a reviewer completes a 10-minute learning path without setup; a developer runs Make or Docker real lab from docs; no fabricated infrastructure metric; all core lesson acceptance tests green. Host/cloud deployment target remains a human decision.

## Deferred scope

Kubernetes, service mesh, Kafka, managed cloud failover, distributed transactions, real multi-region networking, a home-grown consensus algorithm and automatic online rebalancing are outside core. Introduce a reservation-expiry worker/outbox only after core correctness; build a second business microservice only if its boundary teaches a concrete problem.
