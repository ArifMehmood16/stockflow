> **Historical backend design — superseded.** The app is now a browser-only educational simulator. Use [the current simulation design](../SIMULATION.md) and [README](../../README.md). The content below is retained for context, not as an implementation backlog.

# ADR 002 — Writable inventory contract

Status: **recommended for V0 review**. This freezes the Phase 1 writable data contract. It does not migrate a database, issue a credential, or change the running read-only API. Owner acceptance is the V0 checkpoint, not this document's existence.

## Context

The imported diagnostic schema in `infra/schema.sql` stores one global row per public product code. `tenant_id` is an integer from 0 to 9999, `on_hand` and `version` (default 1) are synthetic, and the primary key is `code` alone. The read-only API reads that table with a read-only pool and rejects every method except GET and HEAD.

The earlier reservation sketch used UUID tenants and warehouses, a composite stock key, and a 100,000-row fixture cap. Those identifiers are a different ownership model. Treating the diagnostic integer tenant as a reservation tenant, or copying every catalog row into lesson stock on startup, would either mutate the shared catalog or present a fixture count the import never created.

On the author's machine the retained catalog is 4,532,480 products. That count is an observation in the engineering journal, not a constant the fixture command may assume. A fresh clone has however many rows its own import actually kept.

## Decisions

### 1. Two inventories, no destructive mapping

Keep `stockflow.catalog`, `stockflow.inventory` and `stockflow.dataset_import` unchanged. Lessons never insert, update or delete those rows. The diagnostic integer `tenant_id` and `bucket` are not converted into reservation tenants.

Writable stock lives in a run-owned schema. Its tenant and warehouse ids are UUIDs created for that run. Its `sku` is a copied catalog `code` (`^[0-9]{1,32}$`). There is no function that maps diagnostic tenant 17 onto a UUID.

Rejected: altering the 4.5 million diagnostic rows into UUID tenants; using the integer tenant as the write scope; dual-writing lesson stock into `stockflow.inventory`.

### 2. Composite keys inside a run schema

Each run gets one schema, `sf_run_` plus the run UUID in lowercase hexadecimal with no hyphens. The name is derived from the trusted run id, never from a browser-supplied identifier. The schema is the run boundary, so hot-path rows do not repeat `run_id`.

Inside that schema:

- Stock identity is `(tenant_id, warehouse_id, sku)`. The same SKU in two tenants, or the same tenant UUID in two run schemas, is a different stock row.
- Reservation identity is `(tenant_id, id)`. `id` is a new random UUID allocated only when a reserve commits. The same UUID in another tenant is a different reservation.
- Idempotency identity is `(tenant_id, key)` inside the run schema. The same key in another tenant or another run is a different operation. The warehouse and SKU belong to the request hash, not to the key.

A registry in schema `stockflow_runs` records ownership. Cleanup may drop only a schema named by that run's registry row. It must refuse `stockflow`, `stockflow_runs`, `public` and `pg_*`.

Baseline Make continues to use the configured existing PostgreSQL server for the diagnostic schema, the registry and these run schemas. Replication, sharding, crash-fault and restore lessons provision a separate owned PostgreSQL process or container and load a fixture copy into it. They do not stop or reconfigure the user's existing server.

Rejected: one shared schema with `run_id` on every primary key; a new database on the user's server for every lesson; resetting the diagnostic schema at run start.

### 3. Stock version

Writable `version` starts at 0. It increases by exactly 1 when a committed transaction changes `available`: a successful reserve, a release that wins the `ACTIVE` transition, or an expiry that wins that transition. Insufficient stock, unknown SKU, idempotency conflict, replay and a release of an already terminal reservation do not change `version`.

The conditional write is the authority:

```sql
UPDATE inventory
SET available = available - ?, version = version + 1, updated_at = clock_timestamp()
WHERE tenant_id = ? AND warehouse_id = ? AND sku = ? AND available >= ?
```

A client does not send an expected version on reserve. The version is the freshness value later lessons put in a session token. Diagnostic `version DEFAULT 1` stays on the other table.

Rejected: starting writable versions at 1 to mimic the diagnostic table; using `updated_at` as the freshness token; requiring the client to pass `expectedVersion` before the control plane exists.

### 4. Reservation expiry and exactly-once stock credit

`quantity` is an integer from 1 to 100. New reservations commit as `ACTIVE` with `expires_at = transaction_timestamp() + ttl`. The lesson profile sets `ttl`. The default is 30 seconds. The client cannot choose a longer life, and no profile may exceed 300 seconds.

Expiry and release race on one conditional update: `UPDATE reservation SET state = 'EXPIRED' or 'RELEASED' WHERE tenant_id = ? AND id = ? AND state = 'ACTIVE'`. Exactly one transaction sees one updated row and, in that same transaction, adds `quantity` back to `available` and increments `version`. The loser reads the terminal state, returns it, and does not credit stock. Rows are not deleted by expiry. Database time decides expiry, not the client clock.

A later bounded worker performs expiry. This ADR defines the transaction; it does not add the worker.

### 5. Idempotency claim, durable response, retention

The request hash is lowercase hex SHA-256 of the UTF-8 canonical JSON object with sorted keys:

- reserve: `{"op":"reserve","quantity":N,"sku":"...","warehouseId":"..."}`
- release: `{"id":"...","op":"release"}`

Reusing a reserve key for a release, or changing quantity, SKU or warehouse, is a different hash.

Claim and response share one transaction. The mechanism is a conflict-safe primary-key insert, not a committed placeholder and not an advisory lock. A plain `INSERT` that raises unique violation aborts the PostgreSQL transaction, so the following `SELECT` cannot replay. This contract does not do that.

1. Set `lock_timeout` to the remaining request deadline, at most 2 seconds.
2. Run `INSERT INTO idempotency_record (...) VALUES (..., state='CLAIMED', expires_at=transaction_timestamp()+interval '24 hours', status_code=NULL, response_json=NULL) ON CONFLICT (tenant_id, key) DO NOTHING RETURNING tenant_id`. The insert waits while another transaction holds the unique index entry.
3. If `lock_timeout` fires, that error aborts this transaction. Roll it back and return `409 IDEMPOTENCY_IN_PROGRESS` with `retryable: true`. This request inserted nothing.
4. If the other transaction rolls back, the waiting insert proceeds. `RETURNING` yields a row and this transaction owns the claim.
5. If the other transaction commits, `ON CONFLICT DO NOTHING` inserts nothing and leaves this transaction open. `SELECT` the stored row in this same transaction. Do not mutate stock. A different hash returns `409 IDEMPOTENCY_CONFLICT`. `COMPLETED` returns the stored status and body, including the original `sessionToken` bytes, with header `Idempotency-Replayed: true`. Then roll back this read-only transaction.
6. The owner, only when step 4 inserted the claim, either mutates stock and inserts the reservation, or records insufficient stock or not found. It mints the session token for a `201` through the session issuer below, stores that token inside `response_json`, updates the same idempotency row to `COMPLETED`, and commits once.

`CLAIMED` is never committed. A crash before commit leaves no claim and no stock change. A lost HTTP response after commit is repaired by step 5, which must not decrement stock again and must not mint a second token.

Insufficient stock stores the `409` body from the stock row read in that same transaction, including `available` and `version` at the decision. Replay returns those bytes even if a later request changes stock. A new key sees the current row and can succeed.

Reservation expiry does not rewrite or delete the idempotency body. Replaying the reserve key still returns the original `201` whose `state` is the commit-time `ACTIVE`, until key retention ends. Clients use `GET /v1/reservations/{id}` for the live state.

Idempotency retention is 24 hours from the claim (`expires_at`), or run deletion, whichever comes first. Reservation history uses a different clock: `terminal_at`, set to `transaction_timestamp()` in the same transaction that moves `ACTIVE` to `RELEASED` or `EXPIRED`. A terminal reservation may be deleted only when `terminal_at <= now() - interval '24 hours'`. `expires_at` on a reservation is the hold deadline, not the retention clock. Active rows have `terminal_at` null and are not retention candidates.

The request writer cannot delete. Cleanup uses role `sf_c_<32 hex>`, which receives `SELECT` and `DELETE` on every row of `idempotency_record` and `reservation` in its run schema. That grant is table-wide. It does not stop a `DELETE` of an active reservation or an unexpired key. The role has no `DELETE` on `inventory` or `catalog_snapshot`, no stock `UPDATE`, and no grant on `stockflow.inventory` or another run. HTTP handlers never use it.

The 24-hour clocks are trusted-worker rules, not row security and not a constrained routine. The worker may issue only these statements:

```sql
DELETE FROM idempotency_record
WHERE state = 'COMPLETED' AND expires_at <= transaction_timestamp();
DELETE FROM reservation
WHERE terminal_at IS NOT NULL
  AND terminal_at <= transaction_timestamp() - interval '24 hours';
```

A later implementation test must show the worker uses those predicates. It must not expect PostgreSQL to reject an early `DELETE` by this role, because the grant allows it. No row-security policy is part of this contract. After the worker deletes a `COMPLETED` idempotency row under the first statement, the key may be reused. The worker does not delete a key merely because the reservation became terminal.

Each run keeps at most 200,000 idempotency rows and 200,000 reservation rows. The writer counts and, at the cap, returns `429` without deleting. Later cleanup is what frees a slot. The planned server ceiling is 500 offered requests/s for at most 300 seconds, which is 150,000 requests; 200,000 stored keys cover one such run of unique writes with margin. This cap is not an inventory-row limit. The withdrawn 100,000-row fixture example is not reused.

Rejected: committing `201` before the stock update; an application-level read-modify-write; deleting the idempotency row when the reservation expires; treating a replay of insufficient stock as a fresh availability check.

### 6. Read and write roles

The HTTP service must not use the database owner login once these roles exist.

| Role | Who uses it | Rights |
| --- | --- | --- |
| `stockflow_catalog_reader` | Diagnostic catalog API | `SELECT` on `stockflow.catalog`, `stockflow.inventory`, `stockflow.dataset_import`. Role default is read-only. |
| `sf_w_<32 hex>` | That run's reserve, release, expiry and Phase 1 stock reads | `USAGE` on its schema; `SELECT`, `INSERT`, `UPDATE` on its four tables. No `DELETE`. No grant on `stockflow.inventory` or any other run schema. |
| `sf_c_<32 hex>` | That run's retention worker only | `USAGE` on its schema; table-wide `SELECT` and `DELETE` on `idempotency_record` and `reservation`. The grant is not row-filtered. The worker SQL applies the retention clocks. No `DELETE` on stock or catalog snapshot, and no use by HTTP handlers. |
| `sf_r_<32 hex>` | Future replica reads | `SELECT` only, role default read-only. Created now so later grants do not redefine the contract. Phase 1 HTTP does not open it. |
| Existing owner login | Trusted fixture CLI only | Create the registry, run schemas and roles. Not placed in the API process environment after the split. |

Phase 1 authenticated reads use the run writer on the primary. `consistency=session` checks a session token's `minVersion` against that primary row. `consistency=eventual` returns `409 CAPABILITY_UNAVAILABLE` until a replica reader exists. It must not label a primary read as eventual.

Passwords are random and stored only under ignored `.lab/runs/<runId>/` with mode `0600`. JDBC URLs do not embed them. Logs must not print them.

If the local login cannot `CREATE ROLE`, fixture preparation fails. The service does not fall back to the owner role for writes.

The current `ReadOnlyBoundary` remains in force until a later card adds the specific authenticated routes. This decision does not allow a global write bypass.

### 7. Credential issuer before a controller exists

Tenant credentials and session tokens are different types. They share the run MAC key and the `sf1.` wire format. They do not share an issuer.

A04 verifies tenant tokens. A03's fixture CLI is the only tenant issuer until the Java controller exists. Proposed types, added when those cards start, live in `tools/src/stockflow/FixtureCredentials.java`:

```java
public interface FixtureCredentialIssuer {
  IssuedCredential issue(UUID runId, UUID tenantId, Instant expiresAt);
}

public record IssuedCredential(String token, Instant expiresAt) {}

public interface FixtureCredentialVerifier {
  VerifiedScope verify(String token);
}

public record VerifiedScope(UUID runId, UUID tenantId, Instant expiresAt) {}
```

Proposed CLI, not added by this card: `java tools/Build.java fixture-issue <runId> <tenantId>`. It prints one tenant token to stdout and does not log it. It does not implement session issuance.

The inventory service issues the `sessionToken` on a reserving `201`. It verifies tenant tokens and does not issue them. Proposed type, added with the reserve path:

```java
public interface SessionTokenIssuer {
  String issue(SessionClaim claim);
}

public record SessionClaim(
    UUID runId,
    UUID tenantId,
    UUID warehouseId,
    String sku,
    long minVersion,
    Instant expiresAt) {}
```

`runId` and `tenantId` come from the verified tenant credential. `warehouseId` and `sku` come from the accepted operation. `minVersion` is the stock version stored in that same response. `expiresAt` is the idempotency row's `expires_at`, 24 hours from the claim, not the reservation hold of 30 seconds. The service calls `issue` only while it owns the claim, puts the returned string in `response_json.sessionToken`, and commits that body with the stock change. Replay returns those stored bytes and does not call `SessionTokenIssuer`. A `409` body has no session token. A `consistency=session` read verifies the presented token and echoes it; it does not mint a tenant credential or replace the stored reserve token. A session token does not keep a reservation `ACTIVE` after `expires_at`.

The MAC key is 32 random bytes in `.lab/runs/<runId>/credential.key`. The token is `sf1.` + unpadded base64url(UTF-8 payload) + `.` + unpadded base64url(HMAC-SHA256). Tenant payload keys are sorted: `{"exp":<epoch seconds>,"runId":"<uuid>","tenantId":"<uuid>","typ":"tenant"}`. Session payload keys are sorted: `{"exp":<epoch seconds>,"minVersion":<long>,"runId":"<uuid>","sku":"<code>","tenantId":"<uuid>","typ":"session","warehouseId":"<uuid>"}`. Each verifier accepts only its own `typ`. Verification uses a constant-time MAC compare and rejects an expired, unknown-run or wrong-type token with `401`. A tenant token for tenant A naming tenant B's warehouse returns the scoped `404`, not the other tenant's stock.

The controller later calls `FixtureCredentialIssuer` for tenant tokens. It must not invent a third format. `SessionTokenIssuer` stays in the inventory service because the token's `minVersion` is the version committed with the stock row.

Rejected: trusting `X-Tenant-Id`; minting a tenant token inside the request path; storing raw tenant tokens in PostgreSQL; re-issuing `sessionToken` on replay; using the fixture CLI to sign session tokens.

### 8. Writable fixture size

Fixture creation is explicit. `make run` and catalog setup must not copy stock into a run schema.

Deterministic ids use Java `UUID.nameUUIDFromBytes` over UTF-8 `stockflow-fixture:<seed>:<kind>:<index>` for `tenant` and `warehouse`. Reservation ids are random at commit so a retry returns the stored id.

**Small profile**, when `FIXTURE_ROWS` is unset. Two tenants, two warehouses, the first 100 catalog codes in `code` order, and that same SKU set on every tenant/warehouse pair. That is 400 stock rows, `available = 1000`, `version = 0`. This is a correctness fixture. Its label is `small`. It is not a scale claim.

**Explicit profile**, when `FIXTURE_ROWS` is set to an integer ≥ 1. One stock row per distinct catalog code, tenants and warehouses still two, assignment round-robin. `actual_rows = min(requested, catalog_rows)` after the disk check. No synthetic product is created to fill the request. If the catalog is smaller than the request, the receipt sets `capped_by = catalog` and `actual_rows` to the catalog count. Displays and comparisons call the writable size `actual_rows` only.

Disk admission runs before copy. The estimate is `actual_rows * 2048 * 3 + 256 MiB`, covering the snapshot, indexes and WAL headroom. The CLI stats `SHOW data_directory` when that path is readable. If the usable space is lower, preparation fails, any partial schema is dropped, and no `READY` receipt is published. If the data directory is not visible to the CLI, an explicit fixture is refused rather than capped at a hidden row count. The small profile may still proceed and record `disk_check = not_visible`.

The receipt stores `profile`, `seed`, `requested_rows`, `actual_rows`, `catalog_rows`, `capped_by`, `estimated_bytes` and `free_bytes_at_admission`. Preparation that stops mid-copy remains `PREPARING` or `FAILED` and is not served. A matching `READY` receipt skips a second copy. Catalog import growth does not rewrite a ready snapshot.

The old "100,000 inventory rows per run" example is withdrawn. It must not be a default, a silent maximum, or a stand-in for a million writable rows.

Initial `available` is always 1000 on writable rows. Lesson stock does not copy diagnostic `on_hand`.

### 9. Catalog metadata on a future shard

A shard is a separate PostgreSQL primary. It cannot use a foreign key into the bootstrap database, and the hot path cannot query `stockflow.catalog`.

Fixture preparation copies `code`, `product_name`, `brands`, `categories` and `source` into `catalog_snapshot` in the run schema. Writable `inventory.sku` references only that local snapshot. When a later shard owns buckets, the copy onto that primary includes the inventory, reservation and idempotency rows for those tenants and the snapshot rows those SKUs need. The snapshot is immutable for the run.

Bucket assignment, frozen here and tested with golden vectors in G01: SHA-256 of the tenant UUID's 16 big-endian bytes, then the low 4 bits of the first digest byte. Values are 0 through 15. The same tenant UUID in two runs hashes to the same bucket and still stays isolated by schema and database.

## Schema sketch

The approved shape is now implemented by the versioned files in `infra/migrations/`; the sketch below remains the contract reference. The actual migration files are the executable source.

```sql
CREATE SCHEMA stockflow_runs;
CREATE TABLE stockflow_runs.ownership (
  run_id uuid PRIMARY KEY,
  schema_name text NOT NULL UNIQUE
    CHECK (schema_name ~ '^sf_run_[0-9a-f]{32}$'),
  seed text NOT NULL CHECK (length(seed) BETWEEN 1 AND 64),
  profile text NOT NULL CHECK (profile IN ('small', 'explicit')),
  requested_rows integer CHECK (requested_rows IS NULL OR requested_rows >= 1),
  actual_rows integer NOT NULL CHECK (actual_rows >= 0),
  catalog_rows integer NOT NULL CHECK (catalog_rows >= 0),
  capped_by text NOT NULL CHECK (capped_by IN ('none', 'catalog')),
  estimated_bytes bigint NOT NULL CHECK (estimated_bytes >= 0),
  free_bytes_at_admission bigint,
  disk_check text NOT NULL CHECK (disk_check IN ('measured', 'not_visible')),
  state text NOT NULL CHECK (state IN ('PREPARING', 'READY', 'FAILED')),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Repeated inside schema sf_run_<32 hex>:
CREATE TABLE catalog_snapshot (
  code text PRIMARY KEY CHECK (code ~ '^[0-9]{1,32}$'),
  product_name text NOT NULL,
  brands text NOT NULL,
  categories text NOT NULL,
  source text NOT NULL
);
CREATE TABLE inventory (
  tenant_id uuid NOT NULL,
  warehouse_id uuid NOT NULL,
  sku text NOT NULL,
  available integer NOT NULL CHECK (available >= 0),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, warehouse_id, sku),
  FOREIGN KEY (sku) REFERENCES catalog_snapshot (code)
);
CREATE TABLE reservation (
  tenant_id uuid NOT NULL,
  id uuid NOT NULL,
  warehouse_id uuid NOT NULL,
  sku text NOT NULL,
  quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 100),
  state text NOT NULL CHECK (state IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
  expires_at timestamptz NOT NULL,
  terminal_at timestamptz,
  stock_version bigint NOT NULL CHECK (stock_version >= 0),
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, warehouse_id, sku)
    REFERENCES inventory (tenant_id, warehouse_id, sku),
  CHECK (
    (state = 'ACTIVE' AND terminal_at IS NULL)
    OR (state IN ('RELEASED', 'EXPIRED') AND terminal_at IS NOT NULL)
  )
);
CREATE INDEX reservation_active_expiry ON reservation (expires_at)
  WHERE state = 'ACTIVE';
CREATE INDEX reservation_terminal_retention ON reservation (terminal_at)
  WHERE terminal_at IS NOT NULL;
CREATE TABLE idempotency_record (
  tenant_id uuid NOT NULL,
  key text NOT NULL CHECK (length(key) BETWEEN 1 AND 128 AND key ~ '^[\x21-\x7E]+$'),
  request_hash text NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  state text NOT NULL CHECK (state IN ('CLAIMED', 'COMPLETED')),
  status_code integer,
  response_json jsonb,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, key),
  CHECK (
    (state = 'CLAIMED' AND status_code IS NULL AND response_json IS NULL)
    OR (state = 'COMPLETED' AND status_code IS NOT NULL AND response_json IS NOT NULL)
  )
);
```

`CLAIMED` exists so a failed transaction can roll the claim back. A committed row is `COMPLETED`.

## Walkthrough

Example ids, not a claim that this SKU was imported:

- Run R1 `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1`, schema `sf_run_aaaaaaaaaaaa4aaa8aaaaaaaaaaaaaa1`
- Run R2 `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2`
- Tenant North `11111111-1111-4111-8111-111111111111`
- Tenant South `22222222-2222-4222-8222-222222222222`
- Warehouse W `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb`
- SKU `00000000000101`
- Reserve key `retry-1`

Both tenants start at `available 1000`, `version 0` in both runs.

1. North on R1 reserves quantity 1. The insert claims `retry-1`. The conditional update sets available to 999 and version to 1. The transaction inserts reservation `cccccccc-cccc-4ccc-8ccc-ccccccccccc1` as `ACTIVE` and commits the `201` body. South and R2 stay at 1000 / 0.
2. The response is lost. The client sends the same key and body. `ON CONFLICT DO NOTHING` inserts nothing and leaves the transaction open. The following `SELECT` finds the committed hash match and returns the stored `201`, including the original `sessionToken`, plus `Idempotency-Replayed: true`. Available stays 999 and version stays 1. No second reservation is inserted.
3. If that retry had arrived while step 1 still held the unique-index lock, it would wait up to 2 seconds. On timeout it returns `409 IDEMPOTENCY_IN_PROGRESS` and inserts nothing. It must not create a second reservation.
4. The same key with quantity 2 returns `409 IDEMPOTENCY_CONFLICT`. Stock stays 999 / version 1.
5. South on R1 uses the same key and body. That is a different `(tenant, key)`. South commits its own `201`, available 999, version 1. North is unchanged.
6. North on R2 uses the same key and body. R2's schema has its own row, so R2 goes to 999 / 1 and R1 stays at 999 / 1.
7. A row at available 1 receives quantity 2 with key `short-1`. The conditional update changes zero rows. The transaction stores `409 INSUFFICIENT_STOCK` with available 1 and the current version, and commits. Version does not change. Replaying `short-1` returns that same body even after a later restock. A new key can reserve quantity 1.
8. At `expires_at`, expiry wins `ACTIVE → EXPIRED` for the North R1 reservation, sets `terminal_at` to that transaction's timestamp, credits 1, and moves version from 1 to 2 (available 1000). Replaying `retry-1` still returns the original `201` with `state ACTIVE` and the original session token until the idempotency `expires_at`. `GET` returns `EXPIRED`. `POST` release returns `200` with `EXPIRED` and does not credit again, so version stays 2 and `terminal_at` stays the expiry time. The reservation row remains until `terminal_at` is 24 hours old.
9. The retention worker, using `sf_c_<32 hex>`, deletes the idempotency row with the `expires_at` predicate above. `retry-1` may then reserve again and decrement stock once. The same worker deletes the terminal reservation with the `terminal_at` predicate. The role grant would also allow deleting those rows early; this contract does not claim the database rejects that.

The lost-response retry in step 2 is this exchange. The second response adds the replay header and does not change stock:

```http
POST /v1/reservations HTTP/1.1
Host: 127.0.0.1:8081
Idempotency-Key: retry-1
Authorization: Bearer sf1.<tenant-token>

{"warehouseId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","sku":"00000000000101","quantity":1}

HTTP/1.1 201 Created
Idempotency-Replayed: true

{"id":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1","state":"ACTIVE","quantity":1,"stockVersion":1,"sessionToken":"sf1.<session-token>"}
```

That `sessionToken` was minted once by `SessionTokenIssuer` before the original commit, with `minVersion` 1 and `exp` equal to the idempotency `expires_at`. The replay copies it from `response_json`.

The insufficient-stock decision in step 7 stores this body and replays it unchanged:

```http
HTTP/1.1 409 Conflict

{"code":"INSUFFICIENT_STOCK","message":"Available stock is 1.","requestId":"req-short-1","retryable":false,"available":1,"version":4}
```

## Compatibility

No `ALTER` of the diagnostic tables. The catalog endpoint, its integer tenant field and its version default of 1 stay. New objects are the registry and run schemas, added by a later migration card. Reservation URLs stay. This contract adds `IDEMPOTENCY_IN_PROGRESS`, `CAPABILITY_UNAVAILABLE` and the `Idempotency-Replayed` header. `consistency=eventual` is specified and unavailable until replicas exist.

## Unresolved

- V0 has not accepted this recommendation. A02 must not start from the recommendation alone.
- Licence and public hosting remain the open ADR 001 choices. They do not change this local data contract.
- Whether this machine's login can `CREATE ROLE` is an A03 execution fact. Failure stays failure; this ADR does not offer a second isolation model.
