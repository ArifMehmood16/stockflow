> **Historical backend design — superseded.** Use [the current simulation design](../SIMULATION.md) and [README](../../README.md). Retained for context; not an active implementation backlog.

# Java inventory baseline

Status: historical first read-only diagnostic slice, now extended by authenticated run reads and reservation writes. The Java simulation engine and measured frontend telemetry remain subsequent tasks. The current run API is described in [the OpenAPI contract](openapi/inventory.yaml) and [README](../README.md).

## Run and inspect

`make run` performs the existing idempotent dataset setup, builds/tests the service, then starts both the API at `http://127.0.0.1:8081` and model UI at `http://127.0.0.1:4173`. `make api` starts only the API after setup/build. `make preview` remains a database-free model preview. `make stop` stops the registered UI/API processes and preserves PostgreSQL. `make api-stop` stops only the API. Set `API_PORT` and `PORT` on both start and stop to change them. Occupied ports are never taken over.

While the API runs, `make api-smoke` reads one existing inventory row directly and compares its code, quantity and version with the HTTP response, also checking both health endpoints. It performs no database writes. Run against an idle fixture; a concurrent external stock update can invalidate the snapshot comparison.

Implemented endpoints:

- `GET /health/live`: 200 while HTTP serving works, independent of PostgreSQL.
- `GET /health/ready`: 200 when a catalog/inventory row and, if selected, the run's primary inventory row can be read with required privileges; 503 on outage, missing/incompatible schema or empty data. It does not count millions of rows or prove complete import integrity.
- `GET /v1/catalog/{code}/stock`: code is 1–32 ASCII digits, preserving leading zeroes. Returns `code`, `productName`, `available`, `version`, `tenantId`, `bucket`, `source: "postgresql-primary"`, and `observedAt`. This is a real primary read, not a benchmark. Available stock and tenant assignments remain synthetic fixture fields over public product data.

Known diagnostic stock failures return `{code,message,requestId,retryable}`: 400 INVALID_CODE, 404 STOCK_NOT_FOUND, 503 STOCK_UNAVAILABLE. Responses are not cached. Diagnostic catalog writes return 405; exact authenticated run reservation POST routes are now allowed. No CORS access is enabled. Host allowlist rejects unknown hostnames; native launcher forces loopback binding. Docker publishes only to loopback. No credentials or database exception messages are returned.

This diagnostic catalog endpoint deliberately exposes **public catalog and synthetic inventory data only**. It must not hold private tenant data or be deployed publicly. It does not accept a tenant identity or mutate stock. The separate authenticated warehouse routes use run-owned stock and are specified in [contracts](contracts.md).

## Build and boundaries

- JDK 25, Spring Boot 4.0.8, pgJDBC 42.7.13, Maven 3.9.11.
- Root Maven reactor currently contains only inventory-service. Add controller/contracts modules when they have actual responsibilities.
- `tools/MavenBuild.java` fetches a SHA-512-pinned Maven distribution, verifies it before extraction and starts Maven using the current Java executable. Dependencies use `.lab/m2`; no installed Maven or shell wrapper is required. Enforcer also requires Java 25 and Maven 3.9.11 during the build. Spring's pinned parent manages lifecycle plugin/dependency versions; Enforcer, Boot, compiler, Failsafe and JaCoCo are explicit in the project POMs. The compiler runs `-Xlint:all`.
- `domain` and `application` contain records/use cases and the repository port, without framework/SQL imports. `adapter` owns JDBC; `web` owns HTTP translation. A JDK jdeps test checks compiled core dependencies.
- JDBC pool: maximum 4, minimum idle 0, 2-second acquisition/connection timeout, 2-second statement timeout, 3-second socket timeout. HTTP has 16 workers, 64 connections and a 16-entry accept backlog. Heap defaults to 256 MiB. These are containment defaults, not throughput guarantees or a total RSS cap.
- The diagnostic JDBC pool is read-only and its adapter contains only parameterized SELECTs. Native `make run`/`make api` use the generated `stockflow_catalog_reader` login, with SELECT grants on the diagnostic schema and no run writer rights. When `RUN_ID` selects an owned fixture, a separate bounded run-writer pool handles authenticated reads and writes. The trusted setup CLI alone retains the owner login. Docker's existing inventory environment still uses its isolated database bootstrap role; that path has not been exercised with a running daemon.
- Graceful shutdown uses 5 seconds. The launcher records the owned child PID/start time/executable, forwards parent shutdown to that child, and reuses the existing safe stop checks in a separate API registry. No port-based killing or force termination.

## Verification

`make test-api`: domain/application behavior, real local HTTP against a fake repository, database-outage mapping, method/Host rejection, and architecture guard. No database or external API is needed after dependency download. Fake repositories avoid Mockito agents.

`make test-api-integration`: explicit Maven integration profile, local PostgreSQL role needs CREATE DATABASE. Creates one random `stockflow_test_*` database, installs existing schema, inserts a small synthetic fixture, checks actual reads/parameters/schema failure and unchanged quantity/version, then closes the pool and drops only that owned database. The user's catalog is not altered.

`make verify`: tooling, API and frontend model tests, core coverage gate, syntax, local documentation links and whitespace checks. `make lint` runs the static checks without tests. The API is not yet connected to UI counters or animated requests.

Docker adds the inventory service between seed completion and UI startup. Official multi-architecture index digests for Eclipse Temurin 25 JDK Alpine and PostgreSQL 17 Alpine are pinned in Dockerfiles/Compose, with linux/amd64 and linux/arm64 manifests verified. Container build/run remains unverified while the daemon is stopped; Compose parsing alone is not runtime verification. Maven dependencies emit JDK native-access/Unsafe deprecation warnings from Maven internals; the observed build passes without disabling checks.

Native API launch copies the packaged JAR to a unique ignored runtime file before starting it. Rebuilding or cleaning Maven output therefore cannot replace classes beneath the running process. Owned runtime copies are removed when their process exits; failed cleanup can leave an ignored file. Shutdown does not forcibly kill a stuck JVM.

## Later run-owned extension

[ADR 002](adr/002-writable-inventory-contract.md) defines run-owned UUID stock, roles and fixture credentials. They are now implemented for the native API. `ReadOnlyBoundary` continues to reject all mutations except the two authenticated reservation POST paths. The catalog pool stays read-only; the run writer uses a separate four-connection pool. The frontend remains illustrative and does not call those routes yet.
