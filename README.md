# StockFlow

**Grow one inventory system. Expose a bottleneck. Add a solution. See its next failure.**

StockFlow is a Java and distributed-systems portfolio app. The dark visual workbench keeps one evolving architecture on screen: learning chapters preserve topology, load, builds and installed fixes. Build components on the map, route traffic, expand a cluster to six shards, move bucket ownership, and inject cache, database, replica, API and deployment problems.

## What runs today

- **Real Java tooling:** local HTTP preview, safe process stop, PostgreSQL schema setup, public-catalog download, JDBC COPY import and import verification.
- **Real Java inventory reads:** Spring Boot service on port 8081; stock lookup reads the loaded PostgreSQL catalog, with liveness/readiness checks and bounded JDBC connections. See the [Java baseline](docs/java-baseline.md).
- **Real writable run fixtures and reservations:** an explicit Java command creates isolated, versioned PostgreSQL stock from actual catalog codes. Small runs use up to 400 rows; `FIXTURE_ROWS` selects up to the available unique catalog codes. The API reads an active run with a run-issued tenant credential and commits atomic, idempotent reservations.
- **Real local PostgreSQL data:** Open Food Facts products plus deterministic synthetic inventory quantities/tenant assignments. `DATASET_ROWS` is an upper bound: import the lower of that limit and the available valid, unique products. No duplicate products are invented to reach the limit.
- **Bounded real traffic control:** with an owned `RUN_ID`, the Java preview can send up to 50 read/reserve/release cycles/s for at most 30 seconds and eight concurrent cycles to the loopback inventory API. A cycle makes four HTTP requests. The control reports actual cycle counts, final stock/version and recent elapsed time. The visible workbench is still illustrative and does not yet use this control.

UI integration with the API, Redis, physical replication and separate shard processes remain planned. The animation still uses its illustrative model; creating a fixture does not convert those counters to measured telemetry.

## Run locally with Make

Prerequisites:

1. **JDK 25** (`java` and `javac`, not a JRE) for the service build. Set `JAVA_HOME` or put Java on PATH. Make also recognizes a project-local macOS JDK at `.lab/jdk/Contents/Home` when present; fresh clones do not include it. [Install Temurin](https://adoptium.net/installation/).
2. **Make** and an **existing PostgreSQL 17+ server and database**. The trusted setup role needs CONNECT, CREATE SCHEMA, CREATE ROLE and ownership of StockFlow objects. The running native API receives a separate SELECT-only catalog reader login. Setup never creates/replaces your local database and does not change other schemas.
3. Internet access on first setup for checksum-pinned Java tooling/Maven, Spring dependencies and the public catalog export. Maven is downloaded and invoked by Java into `.lab`; no separate Maven installation, Python, Node, PostgreSQL command-line client or Docker is needed to **run** the app. Later cached builds can use the downloaded dependencies.
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

`make run` creates the `stockflow` schema if absent, checks the import receipt and actual table counts, and **skips download and COPY when the matching dataset is already loaded**. It provisions the catalog-reader role, builds/tests the Java API and starts it alongside the UI. Incomplete imports are retried transactionally. Existing quantities are preserved. Increasing the limit imports missing products; lowering it never deletes existing data. The startup count check detects missing rows but is not a full corruption audit; see [dataset contract](docs/dataset-and-scale.md).

Useful commands:

```sh
make preview          # Java server + browser model only; no database setup
make api              # Setup/build/start just the real stock API at 8081
make api-smoke        # Compare a running API response with the local database
make api-stop         # Stop only the registered API
make setup            # Schema + dataset only
make fixture          # Explicitly create isolated writable run stock after setup
make db-status        # Actual PostgreSQL row counts and bucket distribution
make data-fetch       # Download/project dataset without connecting to PostgreSQL
make db-init          # Schema only
make db-import        # Same skip/verify behavior as setup
make stop             # Stop the registered UI and API; keep PostgreSQL running
PORT=4174 API_PORT=8082 make run    # Use other UI/API ports
PORT=4174 API_PORT=8082 make stop   # Stop the matching UI/API
```

`make stop` does not stop your PostgreSQL server or delete data. It checks process ID, start time and executable before sending a graceful stop. It never kills an arbitrary port owner. If an old Node preview from an earlier revision occupies the port, use Ctrl-C in that terminal once. An occupied port gives guidance instead of taking over the listener.

`make fixture` is separate from `make run`: it never changes the diagnostic `stockflow.catalog` or `stockflow.inventory`. With `FIXTURE_ROWS` unset it uses the first 100 available catalog codes for each of two tenants and two warehouses (normally 400 stock rows). For a scale run, use `FIXTURE_ROWS=1000000 make fixture`; this creates at most one stock row per available unique product. It estimates disk headroom before copying and records the actual row count, seed and ownership in `stockflow_runs`. The command prints a new run ID; repeat with `RUN_ID=<that ID> make fixture` to verify and reuse the READY fixture without resetting quantities. `FIXTURE_SEED` defaults to `stockflow-demo`; a run ID cannot be reused with a different seed or size. The trusted setup role needs `CREATE ROLE` and database schema-creation authority. Per-run writer, cleanup and reader credentials are stored only under ignored `.lab/runs/<run ID>/` with owner-only permissions.

To read a run, issue a one-hour tenant credential locally and start the API for that run. Keep token output private; `TENANT_INDEX` is 0 or 1. The API receives only its run writer login and the separate diagnostic catalog reader, never the setup owner. Only one run is active per API process; the public diagnostic catalog route remains read-only.

```sh
RUN_ID=<run ID> TENANT_INDEX=0 make -s fixture-issue
RUN_ID=<run ID> make api
# GET /v1/warehouses/<warehouse UUID>/stock/<SKU> with Authorization: Bearer <token>
```

Run stock reads default to the primary. `consistency=eventual` returns `CAPABILITY_UNAVAILABLE` until a real replica exists. Reservation lookup is authenticated and tenant-scoped. `POST /v1/reservations` requires a bearer tenant credential, a unique `Idempotency-Key` (1–128 printable non-space ASCII characters), and JSON `{warehouseId,sku,quantity}` with quantity 1–100. A repeated key with the same request returns the stored result and `Idempotency-Replayed: true`; a changed request conflicts. Stock and the response commit in one PostgreSQL transaction. `POST /v1/reservations/{id}/release` uses its own idempotency key and returns a terminal result. A bounded Java worker expires overdue ACTIVE reservations every second. Release and expiry each credit stock only if their guarded transition wins.

To exercise the real fixture, start both processes with the selected run: `RUN_ID=<run ID> make run` (or start the API and preview separately with the same `RUN_ID`). The preview exposes loopback-only `GET /lab/traffic`, `POST /lab/traffic/start?rate=2&seconds=5&concurrency=1`, and `POST /lab/traffic/stop`. The target is always the local inventory API; no URL can be supplied. `offered`, `completed`, `failed` and `dropped` count cycles, each containing four API requests. Scheduled expiry lets accepted cycles finish; explicit stop interrupts them. The preview control is available only when `RUN_ID` is selected. The browser UI will be connected in the next slice.

The implemented inventory HTTP shape is in [the inventory OpenAPI file](docs/openapi/inventory.yaml). The local run API currently supports one selected run per process. The traffic control is a bounded functional experiment, not a benchmark or multi-instance deployment. History cleanup is later work; the current 200,000-row history caps can reject new writes if a long-lived run reaches them.

## Run with Docker (Make optional)

Prerequisites: **Docker Engine/Desktop running**, **Docker Compose v2**, internet access for images/dependencies/catalog, and enough Docker disk/RAM for the import. No host Java, Node, Python or local PostgreSQL is required.

```sh
docker compose up --build -d
docker compose logs -f seed
# After seed succeeds, open http://127.0.0.1:4173

docker compose down
```

Or use `make run-docker` / `make down`. Compose creates an **independent PostgreSQL database**, a one-shot **Java seed container**, the **inventory API** and a **Java UI server**. It ignores your local `DATABASE_URL`. The database has no published port; UI and API ports are published on loopback only. `STOCKFLOW_DB_PASSWORD` configures the Docker database and JDBC connection; Java passes that password separately from the JDBC URI. Keep the password stable after a volume is initialized.

Named volumes preserve PostgreSQL data and the dataset cache. The UI waits for a healthy database, successful seed and ready API. Recreated seed containers skip a verified existing import. `docker compose down` preserves volumes; do not add `-v` unless you explicitly intend to erase this lab's data. Docker image tags are paired with verified multi-architecture index digests; update them deliberately for security releases. **Compose configuration has been checked; container build/run is unverified while the Docker daemon is stopped.**

## Use the workbench

1. Start traffic and send 30,000 modeled requests/s. Inspect the single-database bottleneck; add an API instance and compare.
2. Build Redis. Watch provisioning and readiness before the cache route appears. Use **⚡ Faults** on its card to inject stampede, stale reads, penetration, a hot key or a crash. Apply the named fix in the canvas; read its trade-off on the right.
3. Move to another chapter. The same system, active faults, load and protections remain. Only **Reset lab** clears them.
4. Add a shard: it appears empty and gives no immediate capacity benefit. Follow **Pause writes → Copy buckets → Verify copy → Switch owners**. Add more shards, inspect counts and inject naive-modulo routing, skew or a shard failure.
5. Build a read replica and explore lag/fallback. Fail, fence and promote the primary; recovery remains explicitly illustrative.
6. Prepare green, switch at the router, inject a bad release and roll back. The readiness gate blocks a faulty release.

The canvas starts fitted to the full system. Its left dock keeps offered load, start/pause and illustrative completed/DB/rejected rates visible in a compact vertical stack. The load slider and −/+ buttons use regular 10,000 req/s steps from 10,000 to 250,000. Select **⚡ Faults** on a component to open the vertical fault drawer inside the canvas; its tab collapses it. Drag empty canvas space to pan, scroll over the diagram to zoom around the pointer, or use the toolbar **− / +** controls. **Fit system** restores the full topology. With the canvas focused, arrow keys pan, +/− zoom and 0 fits; keyboard focus brings offscreen component controls into view. Your view stays in place when components are built. Learning/guide panels scroll independently. **Focus canvas** expands the architecture; **Show learning path** restores the panels. On narrow screens, zoom or use Inspector for readable detail. Motion can be disabled. Primary writes, replica reads and asynchronous WAL replication have separate labels, colors and paths.

Hover or keyboard-focus a component or individual shard for its current modeled state, build progress, fault or mitigation. Details follow the pointer, flip at viewport edges and keep the inspected card clear. They may overlap surrounding content and never intercept clicks. Moving to an action or pressing Escape dismisses them; clicking a component keeps its live details in the Inspector. If no overlay fits, the status strip provides a fallback. Build/readiness effects stay inside reserved card slots; new shards animate into their slots and flash when bucket ownership changes. Motion controls apply to these effects too. Hover values remain illustrative, not database telemetry.

## Verify and develop

```sh
make test-java        # Java behavior tests; local loopback/owned child process checks
make test-api         # Service unit, HTTP and architecture tests; no database needed
make test-api-integration # JDBC stock reads in an owned temporary database
make test            # Also frontend model tests (Node.js 24+ required for these tests)
make lint            # Java compiler warnings, JS syntax, docs links, Git whitespace
make verify          # Tests, core coverage gate and lint
make test-integration # Real JDBC tests; role needs CREATE DATABASE for an owned temporary DB
make doctor
```

Default tests do not use a database or public API after dependency bootstrap. Integration tests create and remove only a UUID-named `stockflow_test_*` database. They verify schema idempotence, COPY, empty text fields, skipped repeat imports, quantity preservation and rollback. Do not run them against a role that should not have local database-creation privileges.

JaCoCo 0.8.14 checks at least 80% line and 70% branch coverage in the current domain/application packages; this is a focused policy gate, not a claim of equivalent HTTP/JDBC coverage. The Spring Boot 4.0.8 parent pins the remaining lifecycle plugins and dependency versions. Maven Enforcer requires Java 25 and Maven 3.9.11; use the checksum-verified Java launcher. First build downloads Maven plugins/dependencies, while a populated `.lab/m2` supports offline repeat builds. Database integration remains an explicit local command.

Operational tooling is Java under `tools/`; browser code/tests remain JavaScript. Pinned pgJDBC provides the PostgreSQL wire protocol and COPY API; univocity parses the large quoted TSV/CSV streams correctly. The JDK alone has neither capability. No runtime Python remains.

## Build package

**Continuing development with Codex and ChatGPT:** start with the [43 small task cards](docs/implementation/README.md), [state ledger](docs/implementation/STATE.md), and [validation checkpoints](docs/implementation/VALIDATION.md). Implement one requested item, commit it, then stop at its checkpoint.

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

The application stack is Java 25, Spring Boot and PostgreSQL, with Redis and React/TypeScript planned. The owner's continuation request authorizes the Java baseline milestone. Future hosting/licence choices remain open. Do not confuse a seeded database, successful stock read, process restart or animated topology with verified performance, failover or data recovery.
