# Java inventory baseline

Status: first read-only service slice implemented. Reservation writes, tenant credentials, the Java simulation engine and measured frontend telemetry are subsequent tasks.

## Run and inspect

`make run` performs the existing idempotent dataset setup, builds/tests the service, then starts both the API at `http://127.0.0.1:8081` and model UI at `http://127.0.0.1:4173`. `make api` starts only the API after setup/build. `make preview` remains a database-free model preview. `make stop` stops the registered UI/API processes and preserves PostgreSQL. `make api-stop` stops only the API. Set `API_PORT` and `PORT` on both start and stop to change them. Occupied ports are never taken over.

While the API runs, `make api-smoke` reads one existing inventory row directly and compares its code, quantity and version with the HTTP response, also checking both health endpoints. It performs no database writes. Run against an idle fixture; a concurrent external stock update can invalidate the snapshot comparison.

Implemented endpoints:

- `GET /health/live`: 200 while HTTP serving works, independent of PostgreSQL.
- `GET /health/ready`: 200 when a catalog/inventory row can be read with required columns and privileges; 503 on outage, missing/incompatible schema or empty data. It does not count millions of rows or prove complete import integrity.
- `GET /v1/catalog/{code}/stock`: code is 1–32 ASCII digits, preserving leading zeroes. Returns `code`, `productName`, `available`, `version`, `tenantId`, `bucket`, `source: "postgresql-primary"`, and `observedAt`. This is a real primary read, not a benchmark. Available stock and tenant assignments remain synthetic fixture fields over public product data.

Known stock failures return `{code,message,requestId,retryable}`: 400 INVALID_CODE, 404 STOCK_NOT_FOUND, 503 STOCK_UNAVAILABLE. Responses are not cached. HTTP writes return 405. No CORS access is enabled. Host allowlist rejects unknown hostnames; native launcher forces loopback binding. Docker publishes only to loopback. No credentials or database exception messages are returned.

This diagnostic catalog endpoint deliberately exposes **public catalog and synthetic inventory data only**. It is not the future tenant-authorized reservation contract and must not hold private tenant data or be deployed publicly. It does not accept a tenant identity or mutate stock. The future authenticated warehouse endpoint remains specified separately in contracts.md; this slice does not silently replace it.

## Build and boundaries

- JDK 25, Spring Boot 4.0.8, pgJDBC 42.7.13, Maven 3.9.11.
- Root Maven reactor currently contains only inventory-service. Add controller/contracts modules when they have actual responsibilities.
- `tools/MavenBuild.java` fetches a SHA-512-pinned Maven distribution, verifies it before extraction and starts Maven using the current Java executable. Dependencies use `.lab/m2`; no installed Maven or shell wrapper is required. Spring's parent manages exact plugin/dependency versions. A JDK other than 25 fails the service launcher early.
- `domain` and `application` contain records/use cases and the repository port, without framework/SQL imports. `adapter` owns JDBC; `web` owns HTTP translation. A JDK jdeps test checks compiled core dependencies.
- JDBC pool: maximum 4, minimum idle 0, 2-second acquisition/connection timeout, 2-second statement timeout, 3-second socket timeout. HTTP has 16 workers, 64 connections and a 16-entry accept backlog. Heap defaults to 256 MiB. These are containment defaults, not throughput guarantees or a total RSS cap.
- JDBC is configured read-only and the adapter contains only parameterized SELECTs. It currently uses the owner's configured database role, which may have broader privileges; this is not a database permission boundary. Introduce dedicated service roles before adding real write/read routing.
- Graceful shutdown uses 5 seconds. The launcher records the owned child PID/start time/executable, forwards parent shutdown to that child, and reuses the existing safe stop checks in a separate API registry. No port-based killing or force termination.

## Verification

`make test-api`: domain/application behavior, real local HTTP against a fake repository, database-outage mapping, method/Host rejection, and architecture guard. No database or external API is needed after dependency download. Fake repositories avoid Mockito agents.

`make test-api-integration`: explicit Maven integration profile, local PostgreSQL role needs CREATE DATABASE. Creates one random `stockflow_test_*` database, installs existing schema, inserts a small synthetic fixture, checks actual reads/parameters/schema failure and unchanged quantity/version, then closes the pool and drops only that owned database. The user's catalog is not altered.

`make verify`: tooling, API and frontend model tests, syntax and local documentation links. The API is not yet connected to UI counters or animated requests.

Docker adds the inventory service between seed completion and UI startup. Container build/run and release image digest locking remain unverified/pending; Compose parsing alone is not runtime verification. Maven dependencies emit JDK native-access/Unsafe deprecation warnings from Maven internals; the observed build passes without disabling checks.

Native API launch copies the packaged JAR to a unique ignored runtime file before starting it. Rebuilding or cleaning Maven output therefore cannot replace classes beneath the running process. Owned runtime copies are removed when their process exits; failed cleanup can leave an ignored file. Shutdown does not forcibly kill a stuck JVM.

## Writable contract, not implemented

[ADR 002](adr/002-writable-inventory-contract.md) recommends run-owned UUID stock, roles and fixture credentials. This slice still serves only the diagnostic catalog read. `ReadOnlyBoundary` still rejects non-GET/HEAD methods, and the pool remains read-only. No reservation route, token or writable schema is created here.
