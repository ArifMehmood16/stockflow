# Codex validation checkpoints

The owner explicitly requests reviews at these gates. The implementing agent prepares evidence and fixes findings; a separate review pass examines the scoped change and records a verdict. **Self-testing does not accept a checkpoint.** Use the ten milestones below unless an earlier material decision/security issue genuinely blocks progress.

Current scope override: [the development scope review](SCOPE_REVIEW.md) narrows these original gate definitions to delivered functionality. The first native journey does not require a React migration, Java simulator, SSE or observability. Review backend correctness, the browser journey and the first real solution as runnable milestones; use a short evidence entry, not a new packet for each small commit. Native-first work defers Docker equivalence to the release check; keep it unverified until exercised. Existing verdicts and the historical V1 packet remain unchanged. Apply the retained ownership, transaction and recovery checks whenever the corresponding feature is delivered.

## Request a review

```text
Validate StockFlow checkpoint V1 using docs/implementation/reviews/V1.md
and docs/implementation/STATE.md. Read AGENTS.md and the relevant task cards.
Review only the recorded base-to-head implementation range plus its contracts
and evidence. Check correctness, regression risk, security/ownership boundaries,
and whether claimed tests/results are real. Run the focused checks needed.
Do not implement the next phase. Record accepted, changes_requested or blocked
in the checkpoint packet and STATE, with concrete findings and required fixes.
Do not count unavailable required infrastructure as a pass. Keep the report concise.
```

Replace V1 with the actual gate. A review can use repository files and commit history; the user should not need to paste the entire chat or diff. If a packet is missing, request only the missing facts or inspect Git; do not invent a reviewed commit range.

## Evidence packet rules

Use [the review template](review-template.md) to create `docs/implementation/reviews/Vn.md` only when that checkpoint is ready. Create the reviews directory with the first real packet, not placeholder packets for all gates.

- Record the actual implementation base/head hashes. For V0 the implementation baseline is ba824c7 plus the planning handoff; describe the docs-only range explicitly. Later bases are the preceding accepted checkpoint's code head.
- Keep the packet <=600 words excluding links. Include scope/tasks, commands and outcomes, deterministic reproducer, material decisions, limitations and cleanup evidence.
- Link to small sanitized fixtures/reports/screenshots and relevant journal entries. Do not commit secrets, full SQL dumps or huge logs. Do not omit a failed attempt that explains a material limitation.
- Commit implementation first, then the packet as a docs-only checkpoint. A packet's code head is the implementation commit, not its own self-referential hash. Codex also examines any intervening docs changes that affect the contract.
- An acceptance attaches to the reviewed code head. New implementation fixes after a review require an updated head and targeted revalidation; do not reuse the old verdict blindly.
- The review records blocking findings with file/behavior, a reproduction, why it matters and an objective fix criterion. Cosmetic suggestions need not block correctness gates. The implementing agent fixes only those findings and requests a scoped recheck; no whole-project restart.
- Human choices such as licence, public hosting and newly proposed data contracts are identified separately from test results. A design recommendation is not already an owner approval. V0 can accept technical consistency while explicitly naming any owner decision that still blocks A02.

## V0 — Writable contract and data ownership

**After A01; before A02.** Review the proposed ADR and small examples, not an implementation diff.

- Does the actual numeric catalog map unambiguously to run/tenant/warehouse stock, without destructive migration or unsafe cross-run access?
- Are reservation/idempotency/expiry/version semantics sufficient to write deterministic race tests?
- Can the retained multi-million-row catalog and explicitly selected writable fixture coexist without misleading counts or copying everything on each startup?
- Are native baseline and owned failure clusters distinct, with precise reset/fault scope?
- Are read/write roles and credential issuance explicit, and new dependencies justified?

Evidence: ADR, DDL sketches, two-tenant/two-run examples, one retry/expiry timeline, size/resource policy and unresolved owner decisions. Accept only when blocking material choices are resolved or explicitly owner-approved; never start migration code to settle them implicitly.

## V1 — Correct real inventory baseline

**After A07; before B01.** Maps to PLAN Phase 1 exit.

Review migrations, credential scope, transactions, concurrent reserve/release/expiry, durable idempotency and least privilege. Verify actual SQL races/rollback/restart, safe error contracts and preserved global data. Validate native build/coverage evidence; check default tests need no database after bootstrap. CI/CD is deferred. Review Make/Docker equivalence at release under the current scope override.

Required native proof: conservation ledger, duplicate-key/race tests, cross-tenant/run denial, migration twice, API restart, native setup/stop and exact validation commands. Docker is deferred by the owner's native-first scope direction and remains unverified. A native verdict must name this scope; it cannot claim the original full Make/Docker gate passed.

## V2 — Deterministic runtime and event contract

**After B04; before C01.** Intermediate Phase 2 checkpoint.

Review Java event ordering, finite queues, deterministic seeds, request conservation, run ownership/generation, command deduplication/revision and SSE backpressure/reconnect. Verify lessons require observations rather than clicks. Confirm metric provenance is explicit and logical scale does not allocate unbounded events.

Required proof: repeated identical seeded trace, conservation under overload/drop, stale command/old generation rejection, slow-client replay expiry and comparable report. No UI rewrite needed for this review.

## V3 — Guided single-view simulation

**After C04; before D01.** Completes PLAN Phase 2.

Review preserved visual direction, initially fitted architecture with pan/zoom and Fit system, on-map build/fault/solution controls, component/trace inspector, readiness-driven route animation, persistent chapter state and right-side guide. Verify keyboard/reduced motion, 375/768/1440 widths, camera preservation during builds, separate write/read/WAL routing, mode labels and disconnect/stale handling.

Required proof: runnable guided baseline E2E, representative screenshots/short recording, reconnect/resync and bounded history. Read-only API and favicon remain usable; no frontend arithmetic is mislabeled authoritative Java behavior.

## V4 — Measured end-to-end traffic and scaling

**After D05; before E01.** Maps to PLAN Phase 3.

Review offered versus scheduled/admitted/completed accounting, monotonic latency/histograms, exact known-write reconciliation, generator ceilings, fixed targets, supervisor ownership, leased faults and global connection budgets. Inspect real routing to two API instances and controlled index/lock/pool bottleneck comparisons.

Required proof: UI request traced to real SQL, actual fixture counts, raw workload/window summaries, before/after query plans, stop/crash/drain behavior and generator saturation disclosure. Never approve an arithmetic model as a real load test.

## V5 — Cache failure correctness

**After E06; before F01.** Maps to PLAN Phase 4.

Review namespace isolation, authoritative primary writes, fill coalescing across actual instance scope, lease ownership, stale-fill/version-fence behavior after invalidation/eviction, bounded negatives/L1 and timeout/breaker/fallback limits. Review native/Docker Redis prerequisites and distribution/licence choice.

Required proof: separate reproducible stampede, stale, penetration, hot-key and crash/eviction drills; origin attempts, versions, key counts, memory/queue bounds and inventory invariants. Recovery must distinguish restarted from warm. A local single-flight must not be sold as cross-instance coordination.

## V6 — Replication and verified recovery

**After F05; before G01.** Maps to PLAN Phase 5.

Review actual physical replica roles, lag basis, read-your-writes token scope, writes-to-primary-only, bounded fallback and fencing before promotion. Review interrupted supervisor transitions and former-primary rejoin, plus WAL restart as a separate recovery path.

Required proof: two-process identity, replay observations, known-write RPO and defined RTO, single-writer state after interruption, preserved idempotency and reseed/catch-up. Show the user's bootstrap PostgreSQL was not a fault target. Explain unknown/lost async outcomes; do not infer data recovery from readiness alone.

## V7 — Shards and repeated safe expansion

**After G05; before H01.** Maps to PLAN Phase 6.

Review stable hash vectors, 16-bucket ownership epochs, separate database processes, paused/drained writes including expiry, complete mutable row closure, copy/verification/cutover recovery and stale-router fencing. Check ownership conservation and no double-counted S1. Review post-cutover reverse migration semantics, not unsafe snapshot rollback.

Required proof: two-to-three physical-owner expansion where admitted, multiple simulated expansions up to six, checksums/quantities/reservations/idempotency preservation, interruption before/at/after cutover, hot-tenant skew and failed-owner isolation. New empty shards must not claim a capacity gain.

## V8 — Deployment and resilience

**After H03; before I01.** Maps to PLAN Phase 7.

Review readiness/warm-up, switch/drain/rollback, release identity, doubled pool budgets, old/new schema compatibility and contract-migration boundary. Verify whole-request deadlines and total retry budgets across layers, with durable unknown-write reconciliation.

Required proof: unready-green rejection, bad-release rollback, two-version database compatibility, in-flight request accounting, preserved idempotency and bounded retry-storm repair. Blue/green is not a data undo operation.

## V9 — Portfolio release candidate

**After I04; before public publishing.** Maps to PLAN Phase 8 local release gates.

Review restore into a new owned destination with integrity proof, ten-minute lesson journey, fresh-clone Make/Docker setup/repeat skip/stop, accessibility and retained-resource bounds. Verify simulation-only public packaging excludes infrastructure controls/secrets, even when endpoints are guessed. Check dataset attribution, licence decision, dependency/image/secret scans and supported platform claims.

Required proof: restored write ledger, reproducible raw benchmark summaries, final screenshots, all gate verdicts with code heads, fresh-clone commands and explicit limitations. Hosting target/recurring spend/public publishing still need the owner's explicit decision; this technical gate alone does not authorize them.

## After a review

The reviewer updates the packet and STATE with verdict, reviewed head, findings and date, commits the review record, and stops. The implementing agent fixes only requested issues on top of that history. The owner requests either the recheck or next single card. Do not rewrite history to hide a failed check, add unverifiable approval claims, or erase the original evidence.
