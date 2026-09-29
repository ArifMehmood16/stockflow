# Threat model

Scope: educational lab using synthetic data, initially local. Public site should expose simulation only. Current prototype has no backend control, external load target or stored secrets.

## Boundaries and controls

- Browser → controller: untrusted commands; schema validation, body ≤16 KiB, allowed enums/ranges, expected revision, per-session/run ownership, deduplication, Origin checks and CSRF protection for state changes. Loopback binding alone is not authentication; malicious websites can target localhost.
- Controller → load target: fixed service endpoints from trusted run registry, no arbitrary URL/IP/redirect target. Prevent use as a traffic amplifier/SSRF client. Hard rate/concurrency/duration caps enforced server-side and supervisor-side.
- Controller → supervisor: per-launch capability token, loopback/Unix socket, fixed operations and resource IDs. Never expose Docker socket or shell execution through web API. Supervisor verifies process/container ownership and fault lease.
- Inventory → database/cache: least-privilege role per route; parameterized SQL, primary/replica read-only credential separation, tenant scope in keys/queries, nonnegative DB constraint, scoped idempotency and transaction boundaries. No cache used as authorization or stock authority.
- Telemetry → browser: display untrusted strings as text, never innerHTML from server data; bound buffers, sample traces, redact URLs/credentials. No raw customer data; fixture names synthetic. SSE gaps explicitly signalled.
- Run reset → filesystem/volumes: only dedicated `.lab/<runId>` or labelled volume with ownership marker; canonicalize and verify path; reject symlinks/traversal and unowned resources. Never `docker system prune` or blanket volume deletion.
- Failure → recovered topology: fence before promotion, block old writer route, warn of async loss, reseed old primary. Do not claim protection against arbitrary multi-host split brain from local PID checks.
- Public distribution: inspect tracked files, no `.env`, generated tokens or data dumps; CI read-only default permissions and pinned actions. Public repository does not authorize public infrastructure controls or spending.

## Failure containment

Resource admission before run; watchdog outside UI; faults auto-expire; one active real run; client disconnect stops load; reject exhausted storage; stop dispatch on generator/resource breach. Cleanup is idempotent and visible. Host-mode limits are softer than container cgroups; document that risk. No real destructive corruption exercises outside disposable fixtures.

## Residual risks

Initial prototype static server is for local preview only. Model is deliberately simplified. No production authentication, multi-tenant internet service, full OS sandbox, automatic HA or independent-host disaster recovery is delivered. Future supervisor is a privileged boundary requiring focused review. Licence and public hosting decisions remain human-owned.
