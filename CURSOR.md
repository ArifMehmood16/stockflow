# Implement StockFlow with Cursor

The owner implements with Cursor one item at a time and asks Codex to validate the named checkpoints. **Start at A01. Do not start a whole phase or rebuild completed work.**

- [Task index](docs/implementation/README.md): 43 ordered, bounded tasks.
- [Current state](docs/implementation/STATE.md): task status, next item and review verdicts.
- [Validation gates](docs/implementation/VALIDATION.md): V0–V9 and review evidence.
- [Phase plan](PLAN.md): authoritative phase scope, refined by the task cards.
- [Repository rules](AGENTS.md): coding boundaries and truthful verification.

## Copy this into Cursor for the first item

```text
Work in /Users/mehmooa7/porfolio-development/stockflow.
Read AGENTS.md, CURSOR.md and docs/implementation/STATE.md.
Implement only docs/implementation/tasks/A01.md. Read the README/current
PLAN phase and relevant source/contracts; do not read every task card.
A01 is a decision/specification task: do not change runtime code or the database.
Document recommendations and unresolved choices, update STATE and the AI log,
commit the task, and prepare the V0 packet. Stop for my Codex validation.
Do not mark V0 accepted or begin A02.
```

This prompt explicitly names files; it does not rely on Cursor automatically discovering any particular rule format. No Cursor plugin, subscription setting or IDE automation is required by this handoff.

## Copy this for a subsequent item

Replace `A02` with the next requested ID. Do not paste all cards into a chat.

```text
Implement only task A02 in docs/implementation/tasks/A02.md.
Read AGENTS.md, CURSOR.md and the relevant lines of docs/implementation/STATE.md.
Confirm the previous task and any blocking checkpoint are complete/accepted.
Inspect only the card's source/tests and linked contract sections.
Use failing behavior tests where appropriate, implement the smallest complete
change, and run focused plus required phase checks. Preserve the working UI,
Java tooling, public catalog and unrelated database/process state.
Record actual evidence, update STATE/required docs, review the diff, and commit.
Stop after this one item. If it ends a checkpoint, prepare its review packet;
do not accept your own checkpoint or start the next item. Report in <=200 words.
```

## What already exists

Code baseline `ba824c7`, following `e13cd91`/`5946eda`:

- Java 25 tooling; checksum-pinned Maven 3.9.11 launcher; Spring Boot 4.0.8 inventory service with pgJDBC 42.7.13.
- Read-only `GET /v1/catalog/{code}/stock` and `/health/live`, `/health/ready`; no reservation writes or tenant credential issuer yet.
- Native Make uses existing PostgreSQL database `postgres` and dedicated `stockflow` schema. Docker provisions a separate database. Native connection details stay in ignored `.env`; never paste them into prompts/logs.
- On the author's machine: 4,532,480 valid unique products and matching synthetic inventory rows. DATASET_ROWS is an upper limit; use actual available products when fewer exist. Import/receipt checks skip verified repeats. No fabricated ten-million-row expansion.
- Dark prototype, one fitted cumulative map, left learning path/right guide, on-map controls, technology SVGs, SF favicon, simulated cache/replica/shard/deployment workflows. Current metrics are illustrative browser arithmetic, not real traffic or authoritative Java simulation.
- Safe Java API/preview launch and stop, plus per-launch API JAR copy so clean builds cannot replace a running artifact.
- Last recorded checks: 28 tooling assertions, 10 service/architecture tests, 14 model tests, native JDBC integration and live API/database comparison. These are historical evidence, not a substitute for testing new changes.
- Docker configuration parses; container build/run has **not** been verified. Initial packaged runtime once showed a class-loading failure; final startup/rebuild/smoke checks passed, original cause remains unestablished in the journal. Do not repeatedly investigate it unless it recurs or a task changes that boundary.

## Critical mismatches to resolve once

A01 must resolve these before writable implementation:

1. Actual import uses numeric tenant IDs and product codes; proposed reservation examples use UUID tenant/warehouse keys. Do not quietly mix them or expose unscoped writes.
2. Actual `ReadOnlyBoundary` rejects all non-GET/HEAD operations and JDBC uses read-only settings. Add specific authenticated routes/roles, not a global bypass.
3. The old 100k run-fixture example is not the import limit. Specify actual writable fixture size separately from retained catalog size, support explicitly selected large fixtures within admitted disk/process budgets, and preserve imported data.
4. Bootstrap PostgreSQL is user-owned. Real crash/failover/shard/restore lessons must provision separate owned native processes/containers, never stop/reconfigure the user's existing server. Make still reuses that server for the baseline.
5. The owner now explicitly allows canvas pan and zoom. Start with the full architecture fitted; keep a Fit system control, pointer/keyboard navigation and cumulative state. Building components must preserve the chosen view. This supersedes the earlier fit-only restriction; use Inspector/focus mode for additional detail.

## Working loop and token budget

1. Read this guide and README once at a new task/chat; on subsequent turns read the current state, requested card, latest relevant log entry and actual diff. Read only the current PLAN phase and directly relevant contracts. Do not preload the whole documentation tree.
2. Restate scope/acceptance/files in a few sentences. Do not ask the owner to reapprove routine choices already authorized. New irreversible architectural/product decisions remain explicit.
3. Work on a readable feature branch. Continue the current branch within its stage; at the next stage create `codex/phase-N-description` from the last validated code plus handoff. Never reset to the repository's older default branch, discard uncommitted work or rewrite reviewed commits.
4. Use behavioral red/green testing for code changes, especially defects and concurrency. Documentation/simple asset edits need honest direct validation, not artificial tests. Default tests stay deterministic and offline after dependency bootstrap; integration suites are opt-in and own their resources.
5. Update only relevant docs plus a short factual AI log entry. No repeated whole-plan rewrites. Review staged changes for secrets, unrelated edits, boundary violations and misleading metric claims.
6. Commit the completed item. Implementation completion in STATE means self-tested, not independently reviewed. The user has already authorized incremental commits and publication to this public repository; push only these reviewed project changes, never credentials/data.
7. At a gate, create a compact packet for Codex. Reference the implementation commit before the packet-only docs commit; never amend that referenced commit. Use STATE to record actual hashes after they exist. If recording a hash requires a docs-only follow-up commit, that is fine.
8. Stop. The owner requests each next item. If a task genuinely cannot fit one coherent change, record proposed suffixed subtasks and dependencies; do not silently mark the parent done or skip acceptance criteria.

Keep task summaries <=200 words and checkpoint packets <=600 words plus short command output references. Commit small sanitized JSON summaries/screenshots only when useful. Large raw logs stay ignored under `.lab/evidence/`; do not add dataset dumps, tokens, passwords or environment dumps. For a failure, retain the relevant test/error excerpt and command, not thousands of lines of repeated output.

## Blockers and check results

Use `passed`, `failed`, `not_run` and `blocked` distinctly. Docker being unavailable is not a pass. A body of code without verified acceptance tests is not a completed card. Do not lower coverage, weaken assertions, mark expected failures or quietly downgrade a real scenario to simulation to finish a task.

If blocked by missing infrastructure, continue only independent in-card work, record what remains and stop for the owner. Do not repeat the same failing setup indefinitely or begin a dependent card. The owner can explicitly defer scope, but the deferred gate remains visible rather than being relabeled accepted.

## What to send back after each item

```text
Task: A02 — done | blocked | partial
Commit(s): actual hashes
Changed: <=3 concise bullets
Red/green or direct validation: command + observed outcome
Required checks: passed/failed/not_run with reason
Known gaps: only material items
Next: A03, or STOP for V0/V1/etc.
```

Use [VALIDATION](docs/implementation/VALIDATION.md) when the next step is a Codex review. This guide coordinates the agents; it does not transfer authority to alter unrelated repositories, deploy publicly, spend money or bypass required safety/ownership checks.
