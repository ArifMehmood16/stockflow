# Cursor implementation task index

Start with [CURSOR](../../CURSOR.md). [PLAN](../../PLAN.md) remains the phase-level authority; these **43 task cards** refine its implementation order. [STATE](STATE.md) is the only task/checkpoint status ledger. [VALIDATION](VALIDATION.md) defines the ten review gates.

The initial task is **A01**. Do not redo the delivered prototype, import or read-only API. Execute one requested card, stop, and let the owner choose the next item. A checkpoint blocks the next card until validation is accepted. There is no automatic multi-agent or phase-wide execution.

## Ordering and completion

Cards are strictly sequential in the index below. This refines dependency ordering inside PLAN phases: decide the writable data contract before migrations; define the run boundary before its simulator; build the guided simulation before connecting real load. B and C together complete PLAN Phase 2. Other letter groups map to successive phases. An accepted checkpoint completes only its stated scope, not every later feature.

A02 and A07 finish the outstanding build/operations parts of 1.1/1.5; existing 1.1a stays completed. Phase-level checkboxes are updated only after all relevant cards and gate evidence pass. A task may be implemented/self-tested while its checkpoint is still pending; those are different states.

Future task paths/commands are proposals to introduce at the named card. Do not create empty services or test targets now. A missing prerequisite blocks that card; it is not permission to skip the test or silently substitute simulation for real evidence.

## A — Correct inventory and durable writes

Tasks A01–A07. Review: V0 after the contract decision; V1 after the baseline.

- [A01 — Freeze the writable inventory contract](tasks/A01.md) → **STOP for V0**
- [A02 — Make the existing build reproducible](tasks/A02.md)
- [A03 — Add migrations and owned writable fixtures](tasks/A03.md)
- [A04 — Authenticate scoped stock reads](tasks/A04.md)
- [A05 — Reserve stock atomically with idempotency](tasks/A05.md)
- [A06 — Release and expire reservations exactly once](tasks/A06.md)
- [A07 — Prove the complete inventory baseline](tasks/A07.md) → **STOP for V1**

## B — Java simulation and control plane

Tasks B01–B04. Review: V2.

- [B01 — Create the Java run and command authority](tasks/B01.md)
- [B02 — Implement deterministic queued simulation](tasks/B02.md)
- [B03 — Publish bounded snapshots and resumable events](tasks/B03.md)
- [B04 — Ship the Java baseline lesson runtime](tasks/B04.md) → **STOP for V2**

## C — Guided frontend and cumulative animation

Tasks C01–C04. Review: V3.

- [C01 — Create the frontend shell without losing the design](tasks/C01.md)
- [C02 — Connect the cumulative architecture map](tasks/C02.md)
- [C03 — Handle reconnects, sampled traces and UI errors](tasks/C03.md)
- [C04 — Complete the guided simulation experience](tasks/C04.md) → **STOP for V3**

## D — Measured traffic and service scaling

Tasks D01–D05. Review: V4.

- [D01 — Build a bounded real load generator](tasks/D01.md)
- [D02 — Measure latency and component demand](tasks/D02.md)
- [D03 — Drive the map with measured requests](tasks/D03.md)
- [D04 — Supervise API instances and route around failures](tasks/D04.md)
- [D05 — Prove baseline bottlenecks and scaling](tasks/D05.md) → **STOP for V4**

## E — Redis problems and mitigations

Tasks E01–E06. Review: V5.

- [E01 — Introduce Redis cache-aside safely](tasks/E01.md)
- [E02 — Demonstrate and bound cache stampedes](tasks/E02.md)
- [E03 — Repair stale-fill races and expose freshness](tasks/E03.md)
- [E04 — Handle cache penetration and negative entries](tasks/E04.md)
- [E05 — Demonstrate hot keys without promising magic scaling](tasks/E05.md)
- [E06 — Recover from cache crashes and eviction pressure](tasks/E06.md) → **STOP for V5**

## F — Replication, freshness and recovery

Tasks F01–F05. Review: V6.

- [F01 — Provision an owned primary and physical replica](tasks/F01.md)
- [F02 — Route reads with session freshness](tasks/F02.md)
- [F03 — Inject replica lag and replica failure](tasks/F03.md)
- [F04 — Fence, promote and rejoin safely](tasks/F04.md)
- [F05 — Distinguish crash recovery from failover](tasks/F05.md) → **STOP for V6**

## G — Multiple shards and safe expansion

Tasks G01–G05. Review: V7.

- [G01 — Route across two physical database shards](tasks/G01.md)
- [G02 — Add empty shards through verified provisioning](tasks/G02.md)
- [G03 — Pause, copy and verify moving buckets](tasks/G03.md)
- [G04 — Switch ownership atomically and recover migration](tasks/G04.md)
- [G05 — Teach shard skew, wrong routing and crashes](tasks/G05.md) → **STOP for V7**

## H — Blue/green and resilience

Tasks H01–H03. Review: V8.

- [H01 — Deploy and switch blue/green pools](tasks/H01.md)
- [H02 — Prove schema-compatible rollback](tasks/H02.md)
- [H03 — Bound retries, timeouts and circuit breaking](tasks/H03.md) → **STOP for V8**

## I — Recovery evidence and portfolio release

Tasks I01–I04. Review: V9.

- [I01 — Restore a backup and prove recovered data](tasks/I01.md)
- [I02 — Polish accessibility and long-running behavior](tasks/I02.md)
- [I03 — Publish an honest portfolio evidence package](tasks/I03.md)
- [I04 — Validate fresh-clone operation and prepare release](tasks/I04.md) → **STOP for V9**

## Command availability

Available at handoff: `make preview`, `make run`, `make stop`, `make setup`, `make api`, `make api-stop`, `make api-smoke`, `make test-java`, `make test-api`, `make verify`, `make test-integration`, `make test-api-integration`, `make doctor`, `make run-docker`, `make down`.

Introduced later, not runnable today:

- A02: `make lint`.
- B01: `make test-controller`; B02: `make test-simulation`.
- C01: `make test-frontend` plus named frontend build/typecheck targets; C03: `make test-e2e`.
- D01: `make test-load`; D04: `make test-runtime-integration`.
- E01: `make test-cache-integration`.
- F01: `make test-replication-integration`.
- G01: `make test-sharding-integration`.
- H01: `make test-deployment-integration`.
- I01: `make test-recovery-integration`; I04: `make release-check`.

All service/lifecycle/fixture orchestration stays Java. Frontend TypeScript/Node build and test tooling is allowed. Narrow Make recipes may invoke the tools; no new Python backend or operational scripts.

Public deployment is a separate owner decision after V9. Planning its artifact does not authorize publishing, recurring spend or exposing real infrastructure controls.
