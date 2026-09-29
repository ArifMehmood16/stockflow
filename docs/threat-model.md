# Threat model

Scope: educational lab using synthetic data, initially local. Public site should expose simulation only. Native preview control now supports bounded traffic and one extra owned API when a run is selected; it must not be publicly exposed.

## Current native traffic and scaling control

The preview accepts only fixed start/stop/add-instance/remove-instance paths. Only exact HTTP browser Origins for `127.0.0.1` or `localhost` on the preview port are allowed; other hosts, schemes and ports are rejected; targets are loopback inventory ports selected by trusted configuration, with redirects disabled. The extra API uses the adjacent configured port, the same selected run credentials and a 256 MiB heap cap. The browser supplies no process arguments, paths, credentials or target URLs. The existing launcher refuses occupied ports, snapshots the trusted application artifact, registers the owned child and waits for database readiness before routing. Removal requires the workload to stop and drain; only the owned child handle is terminated. Normal preview shutdown cleans it up; an abrupt OS kill can leave a registered child requiring `api-stop`. The preview holds the selected run signing key to issue local tenant credentials; it is a trusted local controller, not a public multi-user service. Traffic counters are dispatched requests/cycles, not claims of database capacity or throughput improvement.

## Boundaries and controls

- Browser → controller: untrusted commands; schema validation, body ≤16 KiB, allowed enums/ranges, expected revision, per-session/run ownership, deduplication, Origin checks and CSRF protection for state changes. Loopback binding alone is not authentication; malicious websites can target localhost.
- Controller → load target: fixed service endpoints from trusted run registry, no arbitrary URL/IP/redirect target. Prevent use as a traffic amplifier/SSRF client. Hard rate/concurrency/duration caps enforced server-side and supervisor-side.
- Controller → supervisor: per-launch capability token, loopback/Unix socket, fixed operations and resource IDs. Never expose Docker socket or shell execution through web API. Supervisor verifies process/container ownership and fault lease.
- Inventory → database/cache: least-privilege role per route; parameterized SQL, primary/replica read-only credential separation, tenant scope in keys/queries, nonnegative DB constraint, scoped idempotency and transaction boundaries. No cache used as authorization or stock authority.
- Telemetry → browser: display untrusted strings as text, never innerHTML from server data; bound buffers, sample traces, redact URLs/credentials. No raw customer data; fixture names synthetic. SSE gaps explicitly signalled.
- Run reset → filesystem/volumes: only dedicated `.lab/<runId>` or labelled volume with ownership marker; canonicalize and verify path; reject symlinks/traversal and unowned resources. Never `docker system prune` or blanket volume deletion.
- Failure → recovered topology: fence before promotion, block old writer route, warn of async loss, reseed old primary. Do not claim protection against arbitrary multi-host split brain from local PID checks.
- Public distribution: inspect tracked files, no `.env`, generated tokens or data dumps. Public repository does not authorize public infrastructure controls or spending. Automated CI/CD is deferred by the owner.

## Failure containment

Resource admission before run; watchdog outside UI; faults auto-expire; one active real run; client disconnect stops load; reject exhausted storage; stop dispatch on generator/resource breach. Cleanup is idempotent and visible. Host-mode limits are softer than container cgroups; document that risk. No real destructive corruption exercises outside disposable fixtures.

## Residual risks

Initial prototype static server is for local preview only. Model is deliberately simplified. No production authentication, multi-tenant internet service, full OS sandbox, automatic HA or independent-host disaster recovery is delivered. Future supervisor is a privileged boundary requiring focused review. Licence and public hosting decisions remain human-owned.

## Phase 0 preview lifecycle update

Technology SVGs are vendored from a pinned upstream tag with attribution, screened, served as images through explicit paths and covered by the existing self-only CSP. No CDN requests at runtime. Local `make stop` uses an ignored per-port registry plus process start/command identity before SIGTERM; it does not infer ownership from listening ports, force-kill, or expose stop through HTTP. The registry is not a defence against malicious code with the same OS permissions. Docker does not enable registry writes. UI build timers are illustrative only and cancel on reset/chapter switch.

## Catalog bootstrap boundary

New trusted CLI → local PostgreSQL boundary: Java accepts only loopback database hosts (the isolated `database` hostname additionally allowed inside Compose), rejects URL query options, and passes credentials as driver properties. It creates only stockflow objects and never resets the supplied database. UI HTTP remains static GET/HEAD with an explicit allowlist and no connection to the database or load target. `.env`, catalog caches, manifests and JDK downloads are ignored.

External catalog → file/COPY boundary: source URL is a pinned HTTPS official export; validate unique numeric product codes, cap retained text, parse with a streaming library, strip control characters and quote every output CSV field. Fixed SQL/prepared parameters plus JDBC COPY keep text from becoming SQL. Limit selection/scan sizes and parser field/column sizes. Complete imports are atomic; migration/reset of other schemas is absent. Keep transaction/lock timeouts. Record source exhaustion only on clean parser/gzip EOF. File lock plus PostgreSQL advisory lock serializes setup. Receipt/count-based startup checks are not protection against equal-count malicious substitutions or content corruption.

Java preview lifecycle checks PID, start instant and executable. It does not discover or kill a process by port. Process records are user-private on POSIX; a malicious process under the same OS account is outside this bookkeeping boundary. Default Docker database has no host port; local development passwords must not be reused for public deployment. Docker deployment and native Windows require separate validation.

## Phase 1 read-only API boundary

Native Java API binds to loopback and uses the existing local database; Docker API publishes only on loopback and uses the isolated database. The HTTP boundary permits GET/HEAD plus exact authenticated reservation POST paths, has no CORS grant, rejects browser Origin on writes and unexpected Host names, generates request IDs, disables response caching and returns safe fixed stock errors. Write bodies are capped at 1024 bytes; oversized or streamed reservation bodies are rejected. Numeric codes and run SKUs are validated before JDBC; SQL uses bound parameters. Limits cover both connection pools, HTTP workers/connections/backlog, header size and database timeouts. No arbitrary query/load target/control operation is exposed.

The catalog endpoint returns public product data and synthetic inventory/tenant assignments without authentication. It must not be reused for private tenant data or exposed to the internet. Run stock, reservation reads and writes require a signed tenant credential; session tokens are a separate type bound to run, tenant, warehouse, SKU and minimum version. The service checks scope before its parameterized SQL. Native JDBC uses the SELECT-only `stockflow_catalog_reader` role for diagnostics and one `sf_w_*` run role for scoped reads/writes; the trusted setup CLI retains the owner connection. No cleanup credential is passed to HTTP. Docker still uses its isolated bootstrap role pending container-path validation. Local Host filtering and database read privileges do not replace authentication or an OS sandbox. Unknown framework paths retain Spring's sanitized default errors; known stock errors use the documented envelope.

Maven dependencies are fetched from Maven Central, with a checksum-pinned Maven distribution and versioned dependency management. The build can execute dependency/plugin code and requires normal dependency review. No credentials are placed in Java command-line arguments; child environment carries the JDBC settings. Files/data/secrets remain ignored. Multi-architecture Docker image index digests are pinned; container execution and public deployment review remain pending.

## Writable fixture and reservation boundary

ADR 002 separates the diagnostic catalog from run-scoped stock. The explicit trusted fixture CLI creates checksummed migrations, a `stockflow_runs` ownership receipt, a run schema, and separate catalog-reader, run-writer, cleanup and replica-reader roles. A real-login integration test shows a writer cannot update `stockflow.inventory` or read another run's schema. Native HTTP receives only the catalog reader and selected run writer credentials. The trusted CLI issues one-hour tenant tokens; the service verifies them and mints session tokens only inside a successful reserve claim. The idempotency claim, conditional stock update, reservation and stored response commit together. The expiry worker uses the writer role and guarded ACTIVE transition, not deletion. The cleanup role's `DELETE` grant covers every row of its two history tables; a retention worker is not yet implemented and must use the documented 24-hour predicates. An early delete would be a worker defect, not a privilege error. Role passwords and the MAC key are ignored owner-only files under `.lab/`, never placed in JDBC URLs or logs. Only a registry-matched `sf_run_*` schema can be dropped by the fixture cleanup helper. Fault, failover and restore targets must be separate owned database processes, never the user's existing server.
