# Evaluation and verification strategy

Current delivery uses the [reduced scope review](implementation/SCOPE_REVIEW.md): one failing behavior test, minimal implementation, focused checks and a regular commit. The benchmark protocol, histogram/report requirements and full simulator/streaming checks below are future evaluation work. Run infrastructure suites only when the changed behavior depends on them; preserve transaction, isolation and recovery tests.

## Current prototype

Use Node's built-in test runner; no test dependencies. Tests cover load saturation/cache effect, replica routing without extra write capacity, primary failure/fencing/promotion, reset/caps and request conservation. Initial empty implementation produced four meaningful behavioural failures after the missing-module scaffold issue. Final outcomes and browser evidence are recorded in the journal; no production benchmark exists.

The aggregate prototype model assumes fixed read/write mix, fixed per-node capacity and instant topology changes. It does not model latency distributions, queues, WAL, race conditions, TTL or process failures. Tests validate the sketch's internal rules, not PostgreSQL/Redis guarantees.

## Production test pyramid

- Unit/JUnit: inventory invariants, idempotency policy, stable tenant hash, command/run states, deterministic simulation, retry/fault/resource budgets.
- Architecture/ArchUnit: domain/application framework independence; HTTP adapters don't own business transactions; simulator and real telemetry provenance mandatory.
- SQL integration: concurrent reservation/release and duplicate keys across API instances; crash after commit/before response; migrations and old/new schema compatibility. Explicit Testcontainers suite plus native PostgreSQL option for non-Docker local workflow.
- Redis integration: stale-fill ordering, lease ownership/expiry, TTL jitter, negative cache, outage admission and memory limit behaviour. Never use mocks to prove Redis atomicity.
- Replication integration: controlled replay delay, primary failure, rejected unfenced promotion, divergent old node quarantine, known-write loss report and safe rejoin. These tests opt in and own all processes.
- Shard integration: tenant isolation, epoch conflict, interrupted copy, checksum/idempotency preservation, no double-owner writes.
- Frontend/Vitest/Testing Library: guide predicate state, stale data badge, keyboard controls, command errors and accessible inspector.
- E2E/Playwright: baseline→cache→compare and primary failure→fence→promote; simulation default; real pipeline nightly/manual with resource preflight.
- Security: scope/path/URL/command injection, CORS/Origin/CSRF, body/rate caps, telemetry redaction, dependency/image secret scans.

## Benchmark protocol

Record commit, JVM flags, exact Java/Postgres/Redis versions, machine/VM resources, topology, fixture/seed, workload mix, concurrency, offered RPS, warm-up, duration, state of cache, measurement window and generator drops. Proposed real protocol: 10 seconds warm-up + 60 seconds measurement, 3 repetitions per configuration; reset fixture each run; compare medians with ranges, not invented confidence. Separate cold and warm cache windows. Use open-loop scheduled arrival counts to expose scheduler saturation and avoid coordinated-omission mistakes. Store histograms/summary and bounded sampled traces; no raw confidential payloads.

Correctness gates are absolute within tested fixture: no negative available stock; no duplicate reserve/release effects; no cross-tenant access; no success before durable transaction commit; no simultaneous valid write owners. After asynchronous failover, acknowledged-write loss is an explicit measured outcome, not mislabelled passing durability. Reconcile recovered inventory with the recovered reservation history and report ledger differences separately.

Performance acceptance is improvement under a matching controlled workload, not fixed universal RPS. Do not promise a speedup for every workload; correct “no gain” with explanation is a valid lesson result. Once measured, record thresholds and environment in versioned evaluation files. Thresholds currently TBD.

## UI/performance gates

Targets to measure: ≤30 simultaneous animated particles, ≤1 Hz metric render, ≤10,000 buffered events, no unbounded DOM log, responsive stop action under load, no horizontal body overflow at 375 px. Test prefers-reduced-motion and keyboard lesson completion. Never assert FPS without capture.

## Evidence export

Every report carries mode, engine/schema versions, fixture hash, exact workload/config, run window, counters, histogram sample counts, fault/deployment timeline, invariant verdicts, missing-data/gap flags and resource caps. Reports should make an interviewer able to challenge the conclusion.

## Current bootstrap and cumulative-model checks

`make test-java` covers URL/credential separation, data projection/deduplication/upper-limit behavior, owned process stop/refusal, port ownership, static-file allowlist and HTTP method rejection. `make test` adds frontend model tests for persistent lessons, build readiness, conservation, empty new shards, repeated rebalance, cancellation and cache effects. `make test-integration` uses a disposable local PostgreSQL database to verify COPY (including empty fields and embedded quotes), schema idempotence, repeat-import skip, preserved stock and rollback.

The full-catalog run found a CSV behavior difference: univocity's default writer could leave embedded quotes in unquoted fields, whereas PostgreSQL treats them as CSV syntax. A synthetic fixture reproduced SQLSTATE 22P04 before forcing every field to be quoted/escaped. A cached projection upgrade reuses the downloaded data. Use observed journal evidence for the final count and repeat-startup result; do not infer it from a passing small fixture.

## Read-only Java API evidence

The new `make test-api` suite checks lookup validation, leading-zero preservation, known/missing product responses, safe database-outage errors, independent liveness/readiness, method/Host rejection and compiled core dependency boundaries. HTTP tests use a fake repository; `make test-api-integration` separately proves actual JDBC behavior in an owned PostgreSQL database. `make api-smoke` compares a running packaged API against the already imported catalog, without modifying it. No throughput/latency benchmark or tenant-authorization proof is claimed by these tests.
