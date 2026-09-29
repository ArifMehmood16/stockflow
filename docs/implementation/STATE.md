> **Historical backend design — superseded.** The app is now a browser-only educational simulator. Use [the current simulation design](../SIMULATION.md) and [README](../../README.md). The content below is retained for context, not as an implementation backlog.

# StockFlow implementation state

This is the single task and checkpoint status ledger. Do not duplicate task checkboxes elsewhere. Task cards contain specifications, not completion claims.

- Implementation baseline: `ba824c7` (read-only Java API plus favicon; earlier `e13cd91` and `5946eda` contain API work).
- Working base: `main` contains A03; Phase A continuation is on `codex/phase-1-authenticated-run-stock-reads`.
- Current task: **A07 partial under its original scope** — native checks passed; native V1 review pending, Docker equivalence deferred.
- Next review: **V1 native baseline**; [packet available](reviews/V1.md), no independent verdict yet. Apply the later [scope review](SCOPE_REVIEW.md).
- Last accepted checkpoint: **V0** on A01 contract head `c5e8599a898fd49d79656a4dab8d7a98c2b2ade7`.
- Deferred verification: Docker runtime is untested because the daemon socket is unavailable. The native-first scope review moves equivalence proof to release; it does not claim a pass. V1 correctness review remains pending.

Task states: `todo`, `in_progress`, `partial`, `done` (implemented/self-tested), `blocked`. Checkpoint states: `not_requested`, `pending_review`, `changes_requested`, `accepted`, `blocked`. Store completion commit IDs/evidence path on the relevant line after they exist; do not invent or prefill hashes.

## Tasks

- A01: done — R3a correction `c5e8599a898fd49d79656a4dab8d7a98c2b2ade7` on `codex/a01-v0-retention-authority`; prior correction `44008c067b4f2bdcb1951ae9ba20d0d6b6603db6`
- A02: done — build implementation `da8ad31`, then owner-directed CI removal; evidence: [engineering journal](../engineering-journal.md)
- A03: done — fixture implementation `6520b7d`, native catalog-reader correction `23c9ac6`; evidence: [engineering journal](../engineering-journal.md)
- A04: done — scoped reads, run-issued credentials and denial tests; live small-fixture read verified; `feca5dd`
- A05: done — conditional decrement, durable idempotency and concurrency test; `f48ea77`
- A06: done — guarded release/expiry, restart and race tests; `6910ab0`
- A07: partial — native lifecycle, coverage, import and API integration passed at `b83703d`; isolated Docker runtime smoke blocked by stopped daemon
- B01: partial — minimal single-run native traffic control; durable commands and broader lifecycle deferred by scope review
- B02: todo
- B03: todo
- B04: todo
- C01: todo
- C02: partial — first right-hand guide uses real run status; other chapters remain illustrative
- C03: todo
- C04: todo
- D01: partial — single-read load up to 10,000 requests/s until stopped, bounded concurrency, actual rates and configurable run pool; workload distributions and benchmark accounting deferred
- D02: todo
- D03: partial — first workbench chapter starts/stops and polls the Java fixture run; remaining controls are model-only
- D04: partial — native second API start/readiness, round-robin HTTP dispatch and safe removal; live cross-instance replay passed; broader benchmarking remains deferred
- D05: todo
- E01: todo
- E02: todo
- E03: todo
- E04: todo
- E05: todo
- E06: todo
- F01: todo
- F02: todo
- F03: todo
- F04: todo
- F05: todo
- G01: todo
- G02: todo
- G03: todo
- G04: todo
- G05: todo
- H01: todo
- H02: todo
- H03: todo
- I01: todo
- I02: todo
- I03: todo
- I04: todo

## Checkpoints

- V0: accepted — reviewed A01 contract head `c5e8599a898fd49d79656a4dab8d7a98c2b2ade7`; prior changes_requested head `44008c067b4f2bdcb1951ae9ba20d0d6b6603db6`; packet: [reviews/V0.md](reviews/V0.md)
- V1: pending_review — native implementation head `b83703d`; Docker runtime smoke unavailable; packet: [reviews/V1.md](reviews/V1.md); independent verdict: none
- V2: not_requested — after B04; reviewed code commit: none; packet: none
- V3: not_requested — after C04; reviewed code commit: none; packet: none
- V4: not_requested — after D05; reviewed code commit: none; packet: none
- V5: not_requested — after E06; reviewed code commit: none; packet: none
- V6: not_requested — after F05; reviewed code commit: none; packet: none
- V7: not_requested — after G05; reviewed code commit: none; packet: none
- V8: not_requested — after H03; reviewed code commit: none; packet: none
- V9: not_requested — after I04; reviewed code commit: none; packet: none

## Handoff note

Owner uses Codex and ChatGPT with TDD and passing commits per working behavior. Follow the scope review for minimum delivery requirements. The retained public catalog is 4,532,480 imported products on the author's machine; a fresh clone must prepare its own data. The native API supports scoped reads, reserve/release and expiry. The browser remains illustrative until the first real walkthrough is connected.
