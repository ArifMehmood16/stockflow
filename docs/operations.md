# Running StockFlow and respecting machine limits

## Available today

`make run` starts the static prototype on loopback using Node 24. `make stop`, `make test`, `make verify`, `make doctor`, `make run-docker`, `make down` are implemented. Local runs register PID plus OS process start/command identity in ignored `.lab/preview-<port>.json`. Stop sends SIGTERM only on a match and waits up to three seconds; it does not force-kill. A stale or mismatched record leaves the process untouched. Run/stop must use the same PORT. Docker uses `make down` and does not write this local registry. The registry is local process bookkeeping, not a security boundary against another process running as the same OS user. Native stop uses POSIX `ps`, matching macOS/Linux/WSL support. Docker serves only the prototype. No native tools are installed automatically.

## Target operating modes (not yet implemented)

Host Make is the author's primary workflow. It launches Java and the frontend on the host, using native PostgreSQL/Redis binaries when the selected lesson needs them. Docker is an alternative, not an implicit dependency. `make setup` will validate tools, download pinned Maven/frontend dependencies with explicit network notice, and prepare an ignored `.lab/` directory. `make run MODE=simulation` will need only JDK and Node. `make run MODE=real PROFILE=baseline` will additionally initialize owned PostgreSQL data on a non-default port. Replication/sharding profiles initialize separate data directories and processes. No sudo or editing the user's existing database service.

A Java launcher or narrowly scoped scripts manage child processes, lock file, ownership marker, ports and cleanup. Fail fast if port occupied; never kill the occupant. Ctrl-C stops child processes and clears expiring faults; database data persists unless user invokes scoped reset. Store generated secrets in ignored `.lab/` with restrictive permissions. On restart, validate ownership before cleaning stale PIDs; don't trust a reused PID alone.

Proposed future targets: setup, run, stop, reset RUN_ID=..., seed, test, test-integration, test-e2e, lint, verify, doctor, report, run-docker, down. `make reset` requires stopped run and a run identifier, never deletes all Docker volumes. Docker commands must have direct Compose equivalents for users without Make.

## Platform scope

Primary: macOS arm64 and Linux x86_64/arm64 with GNU/POSIX tools, JDK 25, Node 24, Maven Wrapper and PostgreSQL 17 / Redis 8 tools. Windows: Docker Desktop or WSL2; native Windows multiprocess orchestration is not a core promise. Phase 1 checks exact supported patch versions and publishes pinned image digests per architecture. Do not claim all architectures tested until smoke evidence exists.

## Resource profiles (initial engineering budgets, not measurements)

- **Prototype:** one small Node static server + browser; no backend/data services. Current Compose limit 128 MiB/0.5 CPU for server. Browser overhead excluded.
- **Simulation:** one controller JVM (heap 256 MiB, process budget 512 MiB) + browser. Target total app RSS ≤1 GiB excluding browser; one active run, max 500 modelled req/s by default, 5-minute run, bounded event queue.
- **Baseline:** controller 512 MiB + one inventory JVM 512 MiB + PostgreSQL 512 MiB + proxy/web 128 MiB: ~1.7 GiB process caps, budget **3 GiB** with overhead. Cap 2 CPU-equivalents and default 50 offered req/s.
- **Cache:** baseline + Redis 128 MiB process cap (`maxmemory` 64 MiB, eviction policy explicit). Budget **3.5 GiB**.
- **Replication:** baseline + one PostgreSQL replica 512 MiB. Budget **4 GiB**; cap 3 CPU-equivalents.
- **Sharding:** baseline with two primaries, no replica initially; optional second API instance. Budget **5 GiB**, cap 4 CPU-equivalents.
- **Deployment:** blue + green API, one DB, optional Redis; budget **4 GiB**; double connection count during overlap.
- **Combined advanced topology:** at most 3 API instances + 2 primaries + 2 replicas + Redis/controller/proxy. Opt-in **8 GiB** total app envelope, needs ≥16 GiB host RAM and explicit resource check. Never start automatically on low-memory hosts.

Author machine observed during planning: 24 GiB RAM / 10 logical CPUs; JDK unavailable. Host memory is not Docker VM memory; inspect daemon allocation separately. Budget 6–8 GiB Docker VM for advanced selected lessons, preserve headroom for OS/IDE/browser. Do not automatically reconfigure Docker Desktop. Need benchmarks to validate all initial caps.

## Enforced limits

Default real run: 50 offered req/s; UI max 300, server hard ceiling 500; 60-second duration, hard 300 seconds; concurrency 64; scheduler queue 256; timeout 2 seconds; one retry for safe/idempotent requests only within original deadline; stop on sustained resource breach. Synthetic workload only. Ramp at most 50 req/s per second. Supervisor safety loop independent of UI; client disconnect pauses dispatch after 15 seconds and expires run after 60 seconds. Faults max 30 seconds except explicit stopped-primary recovery state, which remains visible and requires controlled restore.

DB pool: start 8 connections per inventory instance per active primary, read-replica pool 4 only if enabled. Global cap per DB 60 application connections + 20 admin/replication headroom, `max_connections=80`. Controller rejects topology expansion if sum of blue/green/shard/read pools breaches that database's budget. PostgreSQL shared buffers initial 128 MiB under 512 MiB cap; statement/lock timeouts tested under load. JDBC connection acquisition deadline shorter than request deadline. Virtual threads do not remove these limits.

Redis cap 64 MiB data, 128 MiB process starting budget; overhead measured and cap revised if needed. TTL 5 seconds default, ±20% jitter; negative TTL 1 second. Keys/values bounded; stock cache only, never credential/session authority. Cache is disposable; DB is durable authority.

Telemetry: 60-second replay/10,000 events (whichever smaller), 20 trace samples/s, max 30 moving particles, 1 Hz metric snapshot, retained report ≤5 MiB, disk allowance 2 GiB/run, max 3 retained runs by default. Backpressure drops optional traces with a visible dropped count, never correctness events without a gap/resync signal.

## Docker topology plan

Use mutually exclusive composed files or validated profiles for baseline/cache/replication/sharding/deployment. Avoid `--profile '*'`. Pin images, non-root app users, healthchecks, tmpfs where safe, read-only root filesystems, memory/cpu/pid caps and bounded logs. Data volumes belong to a run. Only web/control API published to 127.0.0.1; PostgreSQL and Redis remain on private network unless a deliberate developer-only loopback mapping is selected.

Compose healthchecks signal readiness; they do not implement failover. Single-host Compose cannot demonstrate independent host failure, production election or network partition safety. Native processes lack container-enforced CPU/RSS caps; workload caps and monitoring reduce risk but cannot guarantee a hard host resource boundary. Recommend Docker for stricter per-process isolation.

## Runbooks

- Saturation: reduce offered load → inspect generator/pool/DB separately → clear faults → stop. Never auto-raise pool size.
- Redis unavailable: circuit opens, bounded primary fallback; measure error rate. Restore Redis, close breaker after probes, cold-cache warm gradually.
- Primary failure: stop writes, fence, inspect replica freshness, explicit promotion, route, verify write ledger, reseed old instance. If no replica, restart original and show WAL crash recovery.
- Data loss/corruption: freeze affected fixture, restore latest backup to a NEW run-owned DB, compare checksum/invariants, then change route. Replica may contain same corruption.
- Failed blue/green: route back to still-compatible blue, drain green, retain traces. If schema contract already ran, rollback is blocked and explain why.

Operational guidance references [Compose profiles](https://docs.docker.com/compose/how-tos/profiles/), [service resource settings](https://docs.docker.com/reference/compose-file/services/) and [container resource constraints](https://docs.docker.com/engine/containers/resource_constraints/). Limits are proposals to validate, not measured guarantees.
