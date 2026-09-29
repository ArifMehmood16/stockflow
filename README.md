# StockFlow

**Grow one inventory system. Expose a bottleneck. Add a solution. See its next failure.**

StockFlow is a Java and distributed-systems portfolio app. The dark visual workbench keeps one evolving architecture on screen: learning chapters preserve topology, load, builds and installed fixes. Build components on the map, route traffic, expand a cluster to six shards, move bucket ownership, and inject cache, database, replica, API and deployment problems.

## What runs today

- **Real Java tooling:** local HTTP preview, safe process stop, PostgreSQL schema setup, public-catalog download, JDBC COPY import and import verification.
- **Real local PostgreSQL data:** Open Food Facts products plus deterministic synthetic inventory quantities/tenant assignments. `DATASET_ROWS` is an upper bound: import the lower of that limit and the available valid, unique products. No duplicate products are invented to reach the limit.
- **Illustrative frontend simulation:** a million logical records and up to 250,000 modeled requests/s. Its rates, failures, build timings and recoveries are teaching assumptions, not measurements from the imported database. No request load is sent to PostgreSQL yet.

Java inventory endpoints, Redis, physical replication, separate shard processes, real load generation and deployment automation remain planned. The existing PostgreSQL catalog is groundwork for those services, not evidence that they are implemented.

## Run locally with Make

Prerequisites:

1. **JDK 25+** (`java` and `javac`, not a JRE). Set `JAVA_HOME` or put Java on PATH. Make also recognizes a project-local macOS JDK at `.lab/jdk/Contents/Home` when present; fresh clones do not include it. [Install Temurin](https://adoptium.net/installation/).
2. **Make** and an **existing PostgreSQL 17+ server and database**. The supplied role needs CONNECT, CREATE SCHEMA and ownership of StockFlow objects. Setup never creates/replaces your local database and does not change other schemas.
3. Internet access on first setup for two checksum-pinned Java dependencies and the public catalog export. No Python, Node, PostgreSQL command-line client, Maven or Docker is needed to **run** the app.
4. Allow several GB of spare disk for catalog, indexes, transaction/WAL growth and the cached subset. Download/import time depends on the chosen limit and network/disk speed. Resource controls are discussed after the scaling lessons; real setup still needs available resources.

```sh
cp .env.example .env
# Edit DATABASE_URL for your EXISTING database; keep .env private.
make run
# Open http://127.0.0.1:4173
```

Example configuration (replace credentials; do not commit `.env`):

```dotenv
DATABASE_URL=postgresql://YOUR_USER:YOUR_PASSWORD@localhost:5432/postgres
DATASET_ROWS=10000000
STOCKFLOW_DB_PASSWORD=YOUR_LOCAL_DOCKER_PASSWORD
```

The supplied `postgresql+psycopg://` URL format is accepted and converted to JDBC; **psycopg/Python is not used**. URL-encode credentials containing special characters. `DATASET_ROWS` supports 1–10,000,000; the default is 10,000,000, selecting the full pinned catalog when fewer valid products exist. Stock quantities and tenant assignments are synthetic; product descriptions are real public data.

`make run` creates the `stockflow` schema if absent, checks the import receipt and actual table counts, and **skips download and COPY when the matching dataset is already loaded**. Incomplete imports are retried transactionally. Existing quantities are preserved. Increasing the limit imports missing products; lowering it never deletes existing data. The startup count check detects missing rows but is not a full corruption audit; see [dataset contract](docs/dataset-and-scale.md).

Useful commands:

```sh
make preview          # Java server + browser model only; no database setup
make setup            # Schema + dataset only
make db-status        # Actual PostgreSQL row counts and bucket distribution
make data-fetch       # Download/project dataset without connecting to PostgreSQL
make db-init          # Schema only
make db-import        # Same skip/verify behavior as setup
make stop             # Stop only this registered Java preview
PORT=4174 make run    # Use another port
PORT=4174 make stop   # Stop the matching preview
```

`make stop` does not stop your PostgreSQL server or delete data. It checks process ID, start time and executable before sending a graceful stop. It never kills an arbitrary port owner. If an old Node preview from an earlier revision occupies the port, use Ctrl-C in that terminal once. An occupied port gives guidance instead of taking over the listener.

## Run with Docker (Make optional)

Prerequisites: **Docker Engine/Desktop running**, **Docker Compose v2**, internet access for images/dependencies/catalog, and enough Docker disk/RAM for the import. No host Java, Node, Python or local PostgreSQL is required.

```sh
docker compose up --build -d
docker compose logs -f seed
# After seed succeeds, open http://127.0.0.1:4173

docker compose down
```

Or use `make run-docker` / `make down`. Compose creates an **independent PostgreSQL database**, a one-shot **Java seed container**, and a **Java HTTP server**. It ignores your local `DATABASE_URL`. The database has no published port; the UI is bound to loopback. `STOCKFLOW_DB_PASSWORD` configures the Docker database and JDBC connection; Java passes that password separately from the JDBC URI. Keep the password stable after a volume is initialized.

Named volumes preserve PostgreSQL data and the dataset cache. The UI waits for a healthy database and successful seed. Recreated seed containers skip a verified existing import. `docker compose down` preserves volumes; do not add `-v` unless you explicitly intend to erase this lab's data. Docker image tags need release-time digest pinning. **Compose configuration has been checked; container build/run is unverified while the Docker daemon is stopped.**

## Use the workbench

1. Start traffic and send 30,000 modeled requests/s. Inspect the single-database bottleneck; add an API instance and compare.
2. Build Redis. Watch provisioning and readiness before the cache route appears. Use **⚡ Faults** on its card to inject stampede, stale reads, penetration, a hot key or a crash. Apply the named fix in the canvas; read its trade-off on the right.
3. Move to another chapter. The same system, active faults, load and protections remain. Only **Reset lab** clears them.
4. Add a shard: it appears empty and gives no immediate capacity benefit. Follow **Pause writes → Copy buckets → Verify copy → Switch owners**. Add more shards, inspect counts and inject naive-modulo routing, skew or a shard failure.
5. Build a read replica and explore lag/fallback. Fail, fence and promote the primary; recovery remains explicitly illustrative.
6. Prepare green, switch at the router, inject a bad release and roll back. The readiness gate blocks a faulty release.

The canvas fits its available width and height without scrolling. Learning/guide panels scroll independently. **Focus canvas** temporarily expands the architecture; **Show learning path** restores the panels. Narrow screens preserve the full topology but scale labels down; use the inspector and focus view for detail. Motion can be disabled. The right-side walkthrough explains each operation, remaining cost and next action.

## Verify and develop

```sh
make test-java        # Java behavior tests; local loopback/owned child process checks
make test            # Also frontend model tests (Node.js 24+ required for these tests)
make verify          # Tests, frontend syntax, local Markdown links
make test-integration # Real JDBC tests; role needs CREATE DATABASE for an owned temporary DB
make doctor
```

Default tests do not use a database or public API after dependency bootstrap. Integration tests create and remove only a UUID-named `stockflow_test_*` database. They verify schema idempotence, COPY, empty text fields, skipped repeat imports, quantity preservation and rollback. Do not run them against a role that should not have local database-creation privileges.

Operational tooling is Java under `tools/`; browser code/tests remain JavaScript. Pinned pgJDBC provides the PostgreSQL wire protocol and COPY API; univocity parses the large quoted TSV/CSV streams correctly. The JDK alone has neither capability. No runtime Python remains.

## Build package

- [Plan and acceptance gates](PLAN.md)
- [Product and learning journey](docs/product.md)
- [Architecture and diagrams](docs/architecture.md)
- [Dataset selection, schema, scale and import contract](docs/dataset-and-scale.md)
- [Fault/fix implementation playbook](docs/system-failure-playbook.md)
- [Experiment curriculum](docs/experiments.md)
- [UX, wireframes and flows](docs/design/ux.md)
- [API, data and simulation contracts](docs/contracts.md)
- [Operations and resource limits](docs/operations.md)
- [Evaluation](docs/evaluation.md), [threat model](docs/threat-model.md)
- [Architecture decisions](docs/adr/001-design-baseline.md)
- [Developer handoff](docs/handoff.md), [evidence](docs/engineering-journal.md), [resume](RESUME.md)

The proposed application stack is Java 25, Spring Boot, PostgreSQL, Redis and React/TypeScript. The Java bootstrap now runs; the production service phases still require the recorded architecture review. Do not confuse a seeded database, process restart or animated topology with verified performance, failover or data recovery.
