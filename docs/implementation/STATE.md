# StockFlow implementation state

This is the single task and checkpoint status ledger. Do not duplicate task checkboxes elsewhere. Task cards contain specifications, not completion claims.

- Implementation baseline: `ba824c7` (read-only Java API plus favicon; earlier `e13cd91` and `5946eda` contain API work).
- Working base: `main` contains A03; Phase A continuation is on `codex/phase-1-authenticated-run-stock-reads`.
- Current task: **A05 implemented/self-tested**; A06 is next in the owner-requested Phase A delivery push.
- Next review: **V1 after A07**; A02 itself has a task completion check.
- Last accepted checkpoint: **V0** on A01 contract head `c5e8599a898fd49d79656a4dab8d7a98c2b2ade7`.
- Active blocker: none for A04. Docker runtime verification remains untested and is still required for V1, but the owner deferred Docker polish.

Task states: `todo`, `in_progress`, `done` (implemented/self-tested), `blocked`. Checkpoint states: `not_requested`, `pending_review`, `changes_requested`, `accepted`, `blocked`. Store completion commit IDs/evidence path on the relevant line after they exist; do not invent or prefill hashes.

## Tasks

- A01: done — R3a correction `c5e8599a898fd49d79656a4dab8d7a98c2b2ade7` on `codex/a01-v0-retention-authority`; prior correction `44008c067b4f2bdcb1951ae9ba20d0d6b6603db6`
- A02: done — build implementation `da8ad31`, then owner-directed CI removal; evidence: [engineering journal](../engineering-journal.md)
- A03: done — fixture implementation `6520b7d`, native catalog-reader correction `23c9ac6`; evidence: [engineering journal](../engineering-journal.md)
- A04: done — scoped reads, run-issued credentials and denial tests; live small-fixture read verified; completion commit pending
- A05: done — conditional decrement, durable idempotency and concurrency test; completion commit pending
- A06: todo
- A07: todo
- B01: todo
- B02: todo
- B03: todo
- B04: todo
- C01: todo
- C02: todo
- C03: todo
- C04: todo
- D01: todo
- D02: todo
- D03: todo
- D04: todo
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
- V1: not_requested — after A07; reviewed code commit: none; packet: none
- V2: not_requested — after B04; reviewed code commit: none; packet: none
- V3: not_requested — after C04; reviewed code commit: none; packet: none
- V4: not_requested — after D05; reviewed code commit: none; packet: none
- V5: not_requested — after E06; reviewed code commit: none; packet: none
- V6: not_requested — after F05; reviewed code commit: none; packet: none
- V7: not_requested — after G05; reviewed code commit: none; packet: none
- V8: not_requested — after H03; reviewed code commit: none; packet: none
- V9: not_requested — after I04; reviewed code commit: none; packet: none

## Handoff note

Owner now uses Codex and ChatGPT to implement and validate one item at a time. The retained public catalog is 4,532,480 imported products on the author's machine; a fresh clone must prepare its own data. Working API is read-only; simulation remains the browser prototype until B/C are implemented.
