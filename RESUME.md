# Resume StockFlow

Public repository: https://github.com/ArifMehmood16/stockflow
Current branch: `codex/phase-1-java-inventory-baseline`.
Read README, PLAN, AGENTS, ADR 001 and the latest AI_DEVELOPMENT_LOG before editing.

## Current work

The owner requested continuation after the status report. Phase 1 read-only foundation now exists: one Maven/Spring Boot inventory-service module, framework-free stock lookup, JDBC adapter, health endpoints, safe stock errors, local API launch/stop and integration tests. No reservation writes or real traffic visualization yet.

- `make run`: reuse/verify imported data, Maven package/tests, real API 8081 + modeled UI 4173.
- `make preview`: UI only, without PostgreSQL or API setup.
- `make api`: API only after setup/build; `make api-stop`: owned API only.
- `make api-smoke`: compare a running real API stock response against one loaded database row.
- `make stop`: registered UI/API only; keep PostgreSQL and data.
- `make verify`: 28 Java tool assertions, 10 API/architecture tests, 14 model tests and doc links.
- `make test-api-integration`: new JDBC adapter integration in an owned temporary database.
- `make test-integration`: existing dataset COPY/skip/rollback integration.
- Docker Compose now includes API, but daemon is stopped and runtime verification is pending.

JDK 25 required for service build. Maven 3.9.11 is checksum-pinned and Java-launched; cached in .lab. Service uses Spring Boot 4.0.8 / pgJDBC 42.7.13. No installed Maven/Python/Node needed for running; Node is still needed for frontend tests.

## Local state

Ignored .env targets the existing local `postgres` database with DATASET_ROWS=10000000. Do not print or commit it. Project-local JDK exists in ignored .lab/jdk/Contents/Home. Fresh clones supply their own JDK.

stockflow.catalog and stockflow.inventory each contain 4,532,480 rows, the available valid unique source products below the configured maximum. Repeated setup skips import. No synthetic product expansion. Public product descriptions plus synthetic quantities/tenant IDs. Dataset caches/manifests stay ignored.

## Next checkpoint

Finish any final lifecycle/rebuild validation, documentation/diff review and checkpoint commit/push not recorded in the journal. The first packaged smoke timed out with a class-loading error; a restart of the same artifact passed real reads. Added a regression-tested per-launch artifact copy so Maven rebuilds cannot replace a running API's JAR. Record the final packaged checks rather than asserting an unproven original root cause.

Next implementation scope remains Phase 1: inventory reservations, authenticated run/tenant/warehouse fixtures and explicit SQL concurrency/idempotency/expiry. Resolve the documented imported-catalog versus run-fixture schema distinction at that task. Do not retrofit unscoped writes onto the diagnostic catalog endpoint. Further baseline work includes image digest locking, CI and Docker/fresh-clone checks. Phase 2/3 add authoritative Java simulation, real load generation and frontend telemetry. Do not describe modeled counters as actual database measurements.

No unrelated RAG repository work. Existing Phase 0 and Java bootstrap checkpoints are in Git history. Follow the user's instruction to commit progress incrementally.
