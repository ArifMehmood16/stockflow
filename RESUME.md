# Resume StockFlow

## Current checkpoint

Phase 0 design package and interactive prototype delivered. Java services and real infrastructure are NOT implemented. Read README.md, AGENTS.md, PLAN.md, docs/adr/001-design-baseline.md and AI_DEVELOPMENT_LOG.md before continuing.

Repository: https://github.com/ArifMehmood16/stockflow (public).
Branch: `codex/phase-0-stockflow-design-and-prototype`.
First checkpoint: `c1e964b`. Later checkpoints: `git log --oneline` (this file is maintained before each commit, so the containing commit is the latest).

## Available now

- `make run` → http://127.0.0.1:4173 (Node 24; no install/dependencies).
- `make verify` → 5 model tests plus 1 local preview regression test, syntax checks and local Markdown links.
- `make doctor` → tool prerequisites.
- `docker compose up --build -d` / `make run-docker` → optional prototype-only container path.

## Evidence and limitations

Five tests passed after four observed behavioural failures. Browser checks covered overload, scale/no DB improvement, caching, inspector, failure/fencing/promotion, reduced motion and focus retention. Responsive DOM overflow checked at 375/768/1440. HTTP returned 200 root / 404 hidden file / 405 POST. Compose configuration parses; **Docker build/run not tested because daemon is stopped**. Java runtime is absent. Full accessibility audit, all supported OS paths and real benchmarks remain future work. See docs/engineering-journal.md for exact results.

## Next task

Human reviews proposed ADR 001, visual direction, core scope and licence. Then start **PLAN 1.1** on a new dedicated phase branch. Do not carry on with arbitrary services or claim prototype constants are measured Java throughput. Implement one task at a time with red-green-refactor and checkpoint commits.

Before Phase 1, optionally start Docker and verify the prototype container. No need to redo the completed design package. The unrelated RAG repository was left unchanged. All current work belongs in this StockFlow directory.

## Latest maintenance checkpoint

Fixed occupied-port startup guidance after the agent-started preview conflicted with the user’s `make run`. Agent-owned preview was stopped; port 4173 left free. Tests now include a local loopback listener and require permission to bind a port in restricted agent environments. Phase 1 remains unstarted.

## Current UI revision checkpoint

Completed PLAN 0.4b: dark workbench, light ready components, technology logos, on-map build/scale/recovery controls, readiness stages and route switching. Learning path stays; guide points to map controls. Added `make stop` (same PORT as make run), per-port process identity checks, and tests. `make verify` passes 11 tests, syntax checks and 18 links. Browser checks covered building→ready, no early routing, green switch gate, reset cancellation, keyboard inspection, reduced motion and narrow-screen containment. Actual `PORT=4175 make stop` ended the isolated agent preview cleanly; repeated stop reported no registered preview. The test preview is no longer running.

Checkpoint `543c683` was pushed; final evidence/screenshot is in the containing follow-up commit. The previously blocked port fix is also on the remote. Use `git log --oneline` for the latest checkpoint.

If a preview launched before tracking was added still runs in your terminal, use Ctrl-C once and restart with `make run`; thereafter `make stop` manages it. No need to redo this UI revision. Next planned work remains human review of ADR 001, then Phase 1.1; do not start Java without that review.
