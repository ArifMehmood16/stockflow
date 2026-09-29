# Implementation contracts

Status: proposed v1 contract, with Phase 1 run-scoped GET routes now implemented. The design prototype still does not call them. Maintain machine-readable OpenAPI and JSON Schema from Phase 1/2, with generated strict frontend types and compatibility tests.

## Inventory data

[ADR 002](adr/002-writable-inventory-contract.md) is the writable contract. The diagnostic `stockflow.inventory` table, with its integer `tenant_id` and product-code primary key, stays as imported and is never mutated by a lesson. Writable rows live in one schema per run. The sketch below is not a migration.

```sql
CREATE TABLE inventory (
  tenant_id uuid NOT NULL,
  warehouse_id uuid NOT NULL,
  sku text NOT NULL CHECK (sku ~ '^[0-9]{1,32}$'),
  available integer NOT NULL CHECK (available >= 0),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, warehouse_id, sku)
);
CREATE TABLE reservation (
  tenant_id uuid NOT NULL,
  id uuid NOT NULL,
  warehouse_id uuid NOT NULL,
  sku text NOT NULL,
  quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 100),
  state text NOT NULL CHECK (state IN ('ACTIVE','RELEASED','EXPIRED')),
  expires_at timestamptz NOT NULL,
  terminal_at timestamptz,
  stock_version bigint NOT NULL CHECK (stock_version >= 0),
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, warehouse_id, sku)
    REFERENCES inventory (tenant_id, warehouse_id, sku)
);
CREATE TABLE idempotency_record (
  tenant_id uuid NOT NULL,
  key text NOT NULL CHECK (length(key) BETWEEN 1 AND 128),
  request_hash text NOT NULL,
  state text NOT NULL CHECK (state IN ('CLAIMED','COMPLETED')),
  status_code integer,
  response_json jsonb,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, key)
);
```

`sku` is a catalog code. `version` starts at 0 and increments only when `available` changes. A duplicate idempotency key uses `INSERT ... ON CONFLICT DO NOTHING` and then a `SELECT` in that same open transaction. A unique-violation error is not used, because it would abort the transaction before the replay read. The claim and the stored response, including `sessionToken` on a `201`, commit together. A crash before commit leaves no claim. Replay returns the stored body and does not mint a new session token. The same key under another tenant or another run is a different operation.

`terminal_at` is null while `ACTIVE` and is set when the reservation becomes `RELEASED` or `EXPIRED`. Reservation retention runs from `terminal_at`. Idempotency retention runs from the key's `expires_at`. The request writer cannot delete those rows. The per-run cleanup role has table-wide `DELETE` on both history tables, so the database will not reject an early delete. The retention worker must apply those two clocks in its `DELETE` statements. That predicate is a trusted-worker rule, not a row-security policy.

The default writable fixture is the small profile: 2 tenants, 2 warehouses, 100 shared catalog SKUs on each pair, 400 rows, `available` 1000. An explicit `FIXTURE_ROWS` value copies that many distinct catalog codes, capped by the catalog rows actually present and by disk admission. The withdrawn 100,000-row example is not a default or a hidden maximum. Receipts record `actual_rows`. Each of those histories keeps at most 200,000 rows per run. Full DDL, roles and the worked example are in ADR 002.

## Inventory HTTP

Tenant identity comes from a run-issued synthetic client credential, never trusted solely from a path/header. Local fixture clients get assigned tenant scopes. Future internet auth is out of core scope.

- `GET /v1/warehouses/{warehouseId}/stock/{sku}?consistency=eventual|session` → 200 `{sku,available,version,source,cachedAt,observedAt}`. Omitted consistency means primary. `session` requires the opaque session token and reads the primary at `minVersion`. `eventual` returns 409 `CAPABILITY_UNAVAILABLE` until a replica reader exists; a primary read is never labeled eventual. 404 unknown item in scope; 503 required freshness unavailable.
- `POST /v1/reservations`, required `Idempotency-Key`, body `{warehouseId,sku,quantity}` → 201 `{id,state,quantity,stockVersion,sessionToken}`. The inventory service mints `sessionToken` once for that commit; the fixture CLI issues only tenant credentials. Replay returns the stored status and body, including that same token, plus `Idempotency-Replayed: true`. Reuse of a key with a different body → 409 `IDEMPOTENCY_CONFLICT`. An in-flight claim that exceeds the 2-second lock wait → 409 `IDEMPOTENCY_IN_PROGRESS`, `retryable: true`, with no stock change. Insufficient stock → 409 `INSUFFICIENT_STOCK`; the stored body is replayed unchanged even if stock later increases.
- `POST /v1/reservations/{id}/release`, same key contract and a distinct release hash → 200 canonical release result. Already released or expired stays terminal and does not credit stock. The reserve key is not a release key.
- `GET /v1/reservations/{id}` → current tenant-scoped state; use to inspect a known committed operation.
- Health: `/health/live`, `/health/ready`. Readiness depends on critical primary route, not optional Redis. Private telemetry endpoints not publicly routed.

Error envelope `{code,message,requestId,retryable}`. 400 malformed fields; 401/403 invalid scope; 404 scoped missing; 409 state/stock conflict; 413 body too large; 429 admission/limit with Retry-After; 503 dependency unavailable; 504 deadline. No stack traces, SQL or connection URLs.

## Control-plane HTTP

- `POST /api/v1/runs` body `{mode,lessonId,seed,profile,workload}` → 201 run snapshot. Max 1 active real run per local supervisor; proposed simulation cap 1 per browser session. Reject unsupported mode/capability, don't silently fall back.
- `GET /api/v1/runs/{runId}` → authoritative snapshot with revision, state, mode, engineVersion, capabilities, budgets, topology, workload, lesson, telemetryCursor.
- `POST /api/v1/runs/{runId}/commands` body `{commandId,expectedRevision,type,payload}` → 202 `{commandId,status:'accepted'}`. Completion delivered as event; acceptance alone does not advance a lesson.
- `GET /api/v1/runs/{runId}/events` → SSE with Last-Event-ID support.
- `POST /api/v1/runs/{runId}/stop` → stop dispatch immediately, then bounded drain and fault cleanup; idempotent.
- `GET /api/v1/runs/{runId}/report` → sanitized JSON, max 5 MiB. Summary includes config hashes, fixtures, mode, metric provenance, invariant results and limitations.
- `DELETE /api/v1/runs/{runId}` → only stopped/expired own run; remove scoped data and artifacts after explicit user action. Never wipe a named native DB without ownership marker validation.

Commands: start, pauseDispatch, resumeDispatch, setWorkload, setInstanceCount, setCachePolicy, addReplica, setReadPolicy, injectFault, clearFault, fencePrimary, promoteReplica, addShard, migrateBucket, prepareGreen, switchTraffic, rollback, reset. Each has typed allowlisted parameters and state preconditions; no arbitrary URLs, SQL, process names or shell. Workload changes apply at next tick and emit revision. Unsupported combinations → 409 with reason/capability. Promotion includes explicit `acceptPotentialDataLoss` when async lag/unknown outcomes exist.

Command deduplication retained for run life. Same ID + same request returns the same outcome; different payload with same ID conflicts. Expected revision prevents racing tabs. Run states: CREATED → PREPARING → READY → RUNNING ↔ PAUSED → STOPPING → STOPPED; any preparation/runtime unrecoverable issue → FAILED with cleanup. Fault state is separate from run lifecycle. Reset stops, cleans, restores fixture and increments run generation; late old-generation events discarded.

## Event contract

```json
{
  "schemaVersion": 1,
  "runId": "opaque-id",
  "generation": 1,
  "sequence": 142,
  "revision": 6,
  "mode": "simulation",
  "engineVersion": "sim-v1",
  "occurredAt": "2026-09-29T12:00:00Z",
  "virtualTimeMs": 21000,
  "type": "request.completed",
  "requestId": "r-42",
  "data": {"operation":"stock.read","nodeId":"inventory-blue-1","durationMs":17,"status":200,"source":"replica","itemVersion":9}
}
```

Types: run.stateChanged, topology.changed, command.completed/failed, request.started/completed, cache.hit/miss/invalidated, replication.progress, fault.started/cleared, shard.migrationProgress, deployment.stageChanged, metrics.window, invariant.checked, lesson.stepSatisfied, telemetry.gap. Sequences monotonically increase within a generation. SSE initial snapshot precedes deltas; a replay buffer retains at most 10,000 events or 60 seconds. If cursor expired, send resyncRequired and a fresh snapshot; client never stitches a missing history as complete. Deduplicate sequence; detect gaps; reconnect with capped exponential backoff. Send heartbeat every 10 seconds; stale badge after 15 seconds.

## Metrics and simulation semantics

Real mode uses monotonic elapsed timing per request, wall time only for display. Server outputs 1-second interval counters and 60-second rolling histograms. p50/p95/p99 computed from merged histograms, never averages of percentiles. Include sample count, errors/timeouts separately, and whether generator or observer saturated. Outstanding requests are not successes. `offered = started + schedulerDropped`; `started = completed + failed + inFlightDelta` over aligned windows. Business rejections (409) separate from transport/availability failure.

Record offered/admitted/completed req/s; read/write mix; latency ms; error categories; queue depth; pool active/waiting; DB query/lock time; replica replay/write LSN/lag; cache hit/miss/eviction; per-shard share; RSS/CPU and GC pause. Every metric declares measured/modelled, unit, interval, source and missing status. Never render missing as zero.

Simulation uses seeded PRNG, priority queue ordered by `(virtualTime,eventSequence)`, service time distributions, finite worker/connection capacity, deadlines, cache TTL, explicit write/version propagation and bounded replication lag. No sleeps for simulated work. Small time quanta drive UI publication; engine work budget prevents browser-driven huge runs. Little's law is a steady-state explanatory check, not a tail-latency calculator. Verify request conservation and deterministic replay. The current prototype's capacity arithmetic is a disposable visualization sketch, not this engine.

Only sample up to 20 request traces/s and render at most 30 particles simultaneously; aggregate counters account for every generator attempt. Graph animation is illustrative routing and time-compressed; a particle is not necessarily one request. Inspector makes sampling explicit. Pausing simulation freezes virtual time; real pause freezes dispatch only.

## Lesson schema

Store versioned JSON definitions: `id,title,prerequisites,modeCapabilities,fixture,startingConfig,steps[]`. Each step has `instruction,targetControl,allowedAction,expectedObservation,explanation,tradeoff,timeoutHint,recoveryAction`. Completion predicates evaluate server observations, not client clicks. Example: enable cache AND observe at least 20 eligible reads AND hitRate > 0 AND primaryReadRate below recorded baseline under matching workload. Thresholds for real mode calibrated and labelled; insufficient evidence stays incomplete. Quiz answers provide explanation; never gate emergency stop.

## Implemented Phase 1 read-only catalog slice

The diagnostic `GET /v1/catalog/{code}/stock`, `GET /health/live` and `GET /health/ready` exist. Exact response/error semantics and local-only scope are recorded in [Java baseline](java-baseline.md). Authenticated primary warehouse stock and reservation GET routes now read run-owned data using a run writer login and HMAC tenant credential. They do not write stock. Machine-readable reservation/control-plane specifications remain future work.
