# Development scope review

Owner direction: deliver the core app quickly, use TDD and regular commits, and defer observability. Reviewed all 43 task cards and V0–V9. This is a scope and sequencing review, not an independent code review or acceptance of V1. Implementation status remains in [STATE](STATE.md).

This document and the short working order in [PLAN](../../PLAN.md) take precedence over larger deliveries and ordering in the older cards. The cards retain useful correctness cases and future ideas; a deferred feature is not required to finish a smaller milestone. Do not mark a whole card done after implementing only its retained portion.

## What counts as functioning

Keep one system on the existing canvas. A user starts bounded Java traffic, sees a real read/reserve/release change owned PostgreSQL stock, stops traffic, and adds a real component whose readiness changes routing. The guide explains that result. Implement each subsequent fault/fix on that same system.

Keep only feedback needed by that interaction: component state, actual fixture size, request counts/rate, a recent operation's elapsed time, stock/version, and a short failure reason. Animate an illustrative subset of those operations. Poll a current snapshot initially; discard stale responses and mark disconnected state. Do not build span collection, histograms, CPU/RSS dashboards, exporters, telemetry storage, report pipelines or an observability service. Basic health checks, workload limits and correctness assertions remain necessary.

Use small fixtures for tests. Reuse the selected real dataset for demonstrations without importing it again. Keep high modeled rates separate from bounded real requests. Retain simulation as a future capability; implementing every feature twice is not a prerequisite for its first real demonstration.

## Review of every card

### A — Preserve the existing baseline

- **[A01](tasks/A01.md): keep.** Reuse the approved data/ownership contract; reopen only a concrete correctness conflict.
- **[A02](tasks/A02.md): keep delivered tooling.** No new CI/CD, build-system changes, dependency upgrades or module scaffolding for this milestone.
- **[A03](tasks/A03.md): keep.** Reuse owned fixtures, repeat-import detection and role isolation. No new dataset or synthetic expansion.
- **[A04](tasks/A04.md): keep.** Scoped reads and token verification are needed by the real app; fix demonstrated authorization defects only.
- **[A05](tasks/A05.md): keep.** Atomic reserve, durable replay/conflict and rollback are required; do not reduce their tests to accelerate delivery.
- **[A06](tasks/A06.md): keep.** Release/expiry must credit once and survive restart. Add no scheduler platform or broker.
- **[A07](tasks/A07.md): finish native correctness review.** Fix blocking findings and keep startup/stop/restart working. Docker equivalence is deferred to the release check under the owner's native-first direction; retain its unverified status. V1 still needs a review verdict.

### B — Reduce to the control the current screen needs

- **[B01](tasks/B01.md): minimum now.** One owned active run, start/stop/status and bounded workload inputs. Duplicate start/stop must be safe. Keep credentials out of the browser. Defer generic command registries, persistent command histories and speculative runtime interfaces; module/process placement must follow an actual responsibility.
- **[B02](tasks/B02.md): defer full engine.** Keep the existing model explicitly illustrative. Build deterministic Java simulation later when a simulator lesson needs it; it does not block a real local walkthrough.
- **[B03](tasks/B03.md): snapshot only now.** Poll current run/component state. Defer SSE, replay buffers, resumable traces, heartbeat protocols and metric schemas. Old or failed polls must not invent success.
- **[B04](tasks/B04.md): one guide now.** Complete the baseline using observed backend success. Defer scenario DSLs, config hashes, exports and comparison reports.

### C — Connect the existing frontend

- **[C01](tasks/C01.md): defer framework migration.** Reuse current Java-served HTML/JavaScript, styling, logos and favicon. Add a framework only if maintaining a delivered feature needs it.
- **[C02](tasks/C02.md): minimum now, extend per feature.** Wire existing controls to actual pending/ready/failed state. Preserve one canvas, pan/zoom, nearby hover and right guide; reroute only after readiness. No visual redesign or generic graph editor.
- **[C03](tasks/C03.md): error handling now.** Show disconnected/unavailable state and keep stop usable. Bound visible history/particles. Defer event replay and trace inspection; a fresh snapshot is enough for polling recovery.
- **[C04](tasks/C04.md): one complete walkthrough now.** Guide a real read, reserve/release, start/stop and first component addition. Disable unsupported real controls with reasons. Defer comparison tooling and a full simulation curriculum.

### D — Bring the real traffic path forward

- **[D01](tasks/D01.md): next backend work with B01.** Fixed local target, small bounded rate/duration/concurrency, valid fixture requests, prompt stop and no hidden accumulation of queued work. Count rejected/dropped attempts so the UI does not report them as success. No load-testing platform.
- **[D02](tasks/D02.md): defer observability.** Retain simple request counts and elapsed time inside D01/D03 only. No percentiles, spans, SQL/pool timing, process monitoring, histogram library or metric export.
- **[D03](tasks/D03.md): next frontend work with C02/C04.** A browser action reaches Java and PostgreSQL; stock changes reconcile, actual row count is shown and real/model modes stay distinct. This is the first runnable milestone.
- **[D04](tasks/D04.md): first real solution.** Start a second owned API, health-check it, route to both and stop it safely. Reuse existing process identity checks and enforce a connection budget. Test replay across instances. Defer a general infrastructure orchestrator.
- **[D05](tasks/D05.md): small lesson after D04.** Compare the same workload with one/two APIs and explain when the primary remains the bottleneck. Defer benchmark campaigns, query-plan UI and a catalog of index/lock/CPU lessons.

### E — Keep every requested cache problem, add them individually

- **[E01](tasks/E01.md): first cache slice.** One real owned Redis, scoped cache-aside reads, TTL and primary-only writes. Show hit/miss and source. Require namespace isolation, bounded memory and safe unavailability; no parallel simulator implementation first.
- **[E02](tasks/E02.md): keep stampede.** Reproduce concurrent expiry; add bounded per-process single-flight and TTL jitter first. Explicitly show that two JVMs can each fill. A distributed lease is an optional later lesson, not a prerequisite.
- **[E03](tasks/E03.md): keep stale-fill correctness.** Reproduce an old fill after a newer write; add the smallest version-aware repair and primary fallback for strict reads. Test the race and describe lost cache-state limits; no outbox or stronger consistency claim.
- **[E04](tasks/E04.md): keep penetration.** Demonstrate repeated missing keys, short negative TTL and a bounded cache. Test auth separation and negative expiry. No Bloom filter or new item-management feature just for this lesson.
- **[E05](tasks/E05.md): keep hot keys.** Reuse the skewed workload and coalescing already available. Show the remaining hot-write bottleneck. Add L1 only if the demonstrated read problem needs it, rather than introducing another cache automatically.
- **[E06](tasks/E06.md): keep crash/recovery.** Stop only owned Redis, bound primary fallback and show recovery/cold-cache behavior. Implement minimal timeout/admission protection; add a breaker only when the failure experiment requires it. Preserve stock invariants.

### F — Real replicas and database recovery

- **[F01](tasks/F01.md): keep two real processes.** Build an owned primary/replica on separate ports; never reconfigure the existing local PostgreSQL. Show readiness, replication progress and fixture counts. Detailed WAL dashboards and dual Make/Docker implementations are not prerequisites for the first native demo.
- **[F02](tasks/F02.md): keep read/write routing.** Writes go to primary; eligible reads go to replica; session reads use existing scoped tokens and primary fallback. Show source/version. No generalized consistency framework.
- **[F03](tasks/F03.md): keep lag/failure.** One allowlisted lag fault and one owned replica-stop fault with bounded fallback and verified catch-up. No need for a whole network-fault toolkit.
- **[F04](tasks/F04.md): keep explicit failover.** Positive fencing, potential-loss acknowledgement, one writer, route switch and verified old-node rejoin are required. No automatic elections or HA platform. Recovery numbers may be shown only if actually measured; no separate RTO/RPO reporting system.
- **[F05](tasks/F05.md): keep WAL restart.** Verify known committed writes after an owned primary crash/restart. Explain the difference from promotion. Defer synchronous-replication simulation.

### G — Multiple shards, addition and migration

- **[G01](tasks/G01.md): keep real sharding.** Two owned PostgreSQL primaries, stable buckets and tenant routing for stock, reservations and idempotency together. Keep isolation and conservation tests; no distributed transaction service.
- **[G02](tasks/G02.md): keep third-shard addition.** Provision an empty ready node from the map; it receives no traffic before ownership moves. Repeat only within resource limits. Six real nodes are not required for the first demonstration.
- **[G03](tasks/G03.md): keep safe copy.** Pause/drain writes including expiry, copy the complete mutable data, verify it and leave the old owner authoritative until cutover. First support safe cancel/restart of copying; defer fine-grained resumable transfer machinery.
- **[G04](tasks/G04.md): keep safe cutover.** One accepted writer, stale-owner fencing, durable ownership and data verification are required. Demonstrate interruption recovery. Do not offer instant rollback to a stale copy; defer reverse migration UI until needed.
- **[G05](tasks/G05.md): keep requested failures.** Show skew, unsafe modulo routing in illustration, and one owned shard outage. Healthy shards keep working; a lost shard stays unavailable unless a verified recovery source exists. No automatic rebalance platform.

### H — Working blue/green first

- **[H01](tasks/H01.md): keep.** Reuse the API supervisor/router for two versions, readiness-gated switch, bounded drain and rollback. Keep durable idempotency. No deployment platform or CI/CD.
- **[H02](tasks/H02.md): defer advanced schema exercise.** Initial blue/green versions share a compatible schema. Add expand/backfill/contract as a later dedicated lesson; it must not block basic deployment switching.
- **[H03](tasks/H03.md): baseline limits now, advanced drill later.** Keep request deadlines and original idempotency keys; use no automatic retries initially where unnecessary. Defer generalized retry-budget propagation, storm dashboards and a separate resilience framework.

### I — Finish after the core features work

- **[I01](tasks/I01.md): later recovery lesson.** Backup and verified restore into a new owned destination remain in scope. No damage to the user's database, no full backup-management product.
- **[I02](tasks/I02.md): maintain usability per slice; final audit later.** Fix overlaps, inaccessible controls and broken keyboard/stop behavior when found. Defer broad animation polish and prolonged performance campaigns.
- **[I03](tasks/I03.md): defer portfolio packaging.** Keep README commands and attribution accurate while building. Demo video, case study, benchmark reports and full evidence package follow a working app.
- **[I04](tasks/I04.md): release checks later.** Preserve Make/Docker options and prerequisites. Fresh-clone Docker equivalence, public-mode isolation and hosting review are release work; never advertise an untested path as verified. No new release aggregator or CI until useful.

## Checkpoints and work cadence

- **V0:** retain its recorded acceptance; no new contract review without a real conflict.
- **V1:** review native stock correctness and lifecycle; record Docker as deferred/unverified. This review does not accept V1 or erase historical limitations.
- **V2:** defer the full simulator/event gate with B02/B03.
- **V3:** review the first real guided browser journey on the existing frontend; React and SSE are not entry criteria.
- **V4:** review the first real second-instance experiment; detailed telemetry and benchmarking are not entry criteria.
- **V5:** review cache faults/fixes when delivered, focusing on origin work, freshness and stock safety.
- **V6:** retain replication, fencing and recovered-data proof; remove dependence on an observability platform.
- **V7:** retain multiple real owners, verified migration and single-writer proof; six simulated expansions are not a prerequisite for a native two-to-three demo.
- **V8:** review basic blue/green switching/rollback first. Schema evolution and advanced retry drills remain separately deferred.
- **V9:** retain final fresh-clone, Docker, usability, recovery and public-mode checks when preparing release.

Each behavior follows: focused failing test → observed failure → minimum implementation → focused passing test → relevant integration/browser check → commit. Push passing slices for recovery. Run the broad existing suite at a combined milestone or when shared behavior changes; do not repeatedly run every infrastructure suite after unrelated edits. Document the result once in a short milestone entry. A card is not a reason for a new module, dependency, report or separate permission pause. Stop at a requested review checkpoint or a genuine blocker, not after every small commit.

No runtime acceptance result changed in this documentation review. The next implementation milestone is the minimal B01/D01 run worker followed immediately by D03/C02/C04 browser integration, after the pending native baseline review.
