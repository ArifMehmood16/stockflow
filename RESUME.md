# Resume StockFlow

Public repository: https://github.com/ArifMehmood16/stockflow
Current handoff branch: `codex/cursor-implementation-handoff`.
Code baseline: `ba824c7` (includes Java API checkpoints `e13cd91`/`5946eda` and favicon).

## How the owner wants to work now

Cursor implements one requested item at a time. Codex validates at the named milestones to reduce repeated model usage. Read [CURSOR](CURSOR.md), [STATE](docs/implementation/STATE.md) and the requested card from [the task index](docs/implementation/README.md). PLAN remains the phase-level authority; do not load every task card into each agent conversation.

**Next Cursor item: A01. Next Codex checkpoint: V0 after A01.** A01 writes the recommended data/identity/transaction contract and stops before implementation. No new checkpoint has been accepted merely because this plan was created. See [VALIDATION](docs/implementation/VALIDATION.md) for short review prompts and evidence requirements.

## Working implementation

- `make run`: verify/skip catalog import, build Java service, start read-only API at 8081 plus prototype UI at 4173.
- `make preview`: Java-served UI only, no database/API startup.
- `make api`, `make api-stop`, `make api-smoke`: real stock API lifecycle and database comparison.
- `make stop`: owned UI/API only; PostgreSQL/data preserved.
- Existing suites: `make verify`, `make test-integration`, `make test-api-integration`.
- Java 25, Spring Boot 4.0.8, pgJDBC 42.7.13; Java-launched pinned Maven 3.9.11. No Python scripts/backend. Node currently supports frontend tests and will support React/TypeScript build tooling.
- Last recorded verification: 28 tooling assertions, 10 API/architecture tests, 14 frontend model tests; isolated JDBC integration; real API/database stock comparison; launch-copy survives clean rebuild. These are historical results, not verification of later Cursor changes.
- Docker Compose includes database/seed/API/UI; config parses but container build/run is unverified because the daemon was stopped.
- UI is a dark fitted cumulative prototype with on-map actions, guide, logos and favicon. Its rates/failures are modeled arithmetic; authoritative Java simulation and measured animation are still future tasks.

## Local data and safety

Ignored `.env` targets the existing local database `postgres` with DATASET_ROWS=10000000. Do not print/commit credentials. A project-local JDK/cache exists under ignored `.lab`; fresh clones supply/install documented prerequisites.

The owner's `stockflow.catalog` and `stockflow.inventory` each contain 4,532,480 valid unique public products/synthetic stock records. Selection uses the lower of requested maximum and available products. Repeat setup skips verified imports; do not manufacture duplicates or reimport needlessly.

Real write lessons need run-owned stock, credentials and transactions. A01 resolves the current numeric catalog versus proposed UUID warehouse contract. Fault/failover/restore lessons must use separate owned native clusters or containers; never stop/reconfigure the user's PostgreSQL instance. Native Make still uses that existing instance for baseline operation.

A first packaged API run once had a class-loading error; restart and final clean-rebuild/live smoke passed. Per-launch JAR isolation was added. Original root cause remains unestablished in the journal; investigate if it recurs, not as a reason to repeat already-completed work indefinitely.

No unrelated RAG repository work. No public deployment, licence choice or spending is authorized by this handoff. Commit each completed item, preserve reviewed history and update only the relevant status/evidence.
