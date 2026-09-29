# Resume StockFlow

Repository: https://github.com/ArifMehmood16/stockflow (public).
Branch: `codex/phase-0-stockflow-design-and-prototype`.
Use `git log --oneline` for checkpoint hashes. Read README, PLAN, AGENTS, ADR 001 and latest AI_DEVELOPMENT_LOG before edits.

## Current work

PLAN 0.4c/0.4d: one cumulative fitted topology, modeled scale/faults, Java operational tooling, and real catalog bootstrap. Model/UI checkpoint `1fcbead` was pushed. The containing Java checkpoint supersedes all Python/Node operational tooling. Frontend code and tests remain JavaScript.

- `make run`: Java setup/skip + preview at 4173; `make preview`: Java preview only.
- `make stop`: stops only registered Java preview; never PostgreSQL.
- `make setup`, `make db-status`: actual database setup/counts.
- `make verify`: Java tool tests + frontend model tests + links.
- `make test-integration`: owned temporary PostgreSQL database; requires CREATE DATABASE permission.
- Docker Compose defines isolated PostgreSQL, Java seed and Java preview. Config parses; daemon stopped, container execution unverified.

## User's local state

Ignored .env points to the existing local `postgres` database and has DATASET_ROWS=10000000. It contains credentials; do not print or commit it. A verified project-local Temurin JDK is in ignored .lab/jdk/Contents/Home and Make finds it. Fresh clones require their own JDK 25+.

**Actual committed database:** stockflow.catalog and stockflow.inventory each contain 4,532,480 rows, the available valid unique products below the ten-million maximum. Complete source receipt committed. A subsequent make run skipped download and COPY successfully. The source, cached projections and manifests stay in .lab/dataset, excluded from Git. The full projection was upgraded to CSV format version 2 to handle embedded quotes safely.

The browser sketch separately models one million logical rows, 16 buckets and up to 250k illustrative req/s; no real API/load/Redis/replication/sharding is running. Do not describe animated recovery as measured recovery. Chapter changes preserve all state; Reset lab clears it.

## Next checkpoint

Finish final browser/responsive checks, docs/diff review and commit/push if not recorded below. Then stop at the Phase 0 gate. Full inventory service phases still need recorded architecture review; the owner has explicitly approved Java/local-PG/Docker bootstrap choices, not arbitrary further services. Do not modify the unrelated RAG repository.

## Validation checkpoint

PLAN 0.4c and 0.4d implementation/host checks completed. make verify: 24 Java assertions + 14 model tests + 24 local links. Real JDBC integration includes CSV quotes, empty values, skipped repeats, missing-row repair, smaller-than-limit completion and rollback. Full host make run works and skips the loaded 4,532,480-row catalog. Browser fits desktop/narrow canvas without inner scroll and preserves load/protections through shard expansion. Docker config parses but its daemon is stopped. Next production work remains the Phase 0 review gate; no further service implementation is implied by this checkpoint.
