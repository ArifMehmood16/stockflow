# Resume StockFlow

## Current checkpoint

Phase 0 design package and interactive prototype delivered. Java services and real infrastructure are NOT implemented. Read README.md, AGENTS.md, PLAN.md, docs/adr/001-design-baseline.md and AI_DEVELOPMENT_LOG.md before continuing.

Repository: https://github.com/ArifMehmood16/stockflow (public).
Branch: `codex/phase-0-stockflow-design-and-prototype`.
First checkpoint: `c1e964b`. Later checkpoints: `git log --oneline` (this file is maintained before each commit, so the containing commit is the latest).

## Available now

- `make run` → http://127.0.0.1:4173 (Node 24; no install/dependencies).
- `make verify` → 5 model tests, syntax checks and local Markdown links.
- `make doctor` → tool prerequisites.
- `docker compose up --build -d` / `make run-docker` → optional prototype-only container path.

## Evidence and limitations

Five tests passed after four observed behavioural failures. Browser checks covered overload, scale/no DB improvement, caching, inspector, failure/fencing/promotion, reduced motion and focus retention. Responsive DOM overflow checked at 375/768/1440. HTTP returned 200 root / 404 hidden file / 405 POST. Compose configuration parses; **Docker build/run not tested because daemon is stopped**. Java runtime is absent. Full accessibility audit, all supported OS paths and real benchmarks remain future work. See docs/engineering-journal.md for exact results.

## Next task

Human reviews proposed ADR 001, visual direction, core scope and licence. Then start **PLAN 1.1** on a new dedicated phase branch. Do not carry on with arbitrary services or claim prototype constants are measured Java throughput. Implement one task at a time with red-green-refactor and checkpoint commits.

Before Phase 1, optionally start Docker and verify the prototype container. No need to redo the completed design package. The unrelated RAG repository was left unchanged. All current work belongs in this StockFlow directory.
