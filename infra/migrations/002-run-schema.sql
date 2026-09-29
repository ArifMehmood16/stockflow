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
  sku text NOT NULL REFERENCES catalog_snapshot(code),
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
  state text NOT NULL CHECK (state IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
  expires_at timestamptz NOT NULL,
  terminal_at timestamptz,
  stock_version bigint NOT NULL CHECK (stock_version >= 0),
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, warehouse_id, sku)
    REFERENCES inventory (tenant_id, warehouse_id, sku),
  CHECK ((state = 'ACTIVE' AND terminal_at IS NULL)
      OR (state IN ('RELEASED', 'EXPIRED') AND terminal_at IS NOT NULL))
);
CREATE INDEX reservation_active_expiry ON reservation (expires_at) WHERE state = 'ACTIVE';
CREATE INDEX reservation_terminal_retention ON reservation (terminal_at) WHERE terminal_at IS NOT NULL;
CREATE TABLE idempotency_record (
  tenant_id uuid NOT NULL,
  key text NOT NULL CHECK (length(key) BETWEEN 1 AND 128 AND key ~ '^[\x21-\x7E]+$'),
  request_hash text NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
  state text NOT NULL CHECK (state IN ('CLAIMED', 'COMPLETED')),
  status_code integer,
  response_json jsonb,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, key),
  CHECK ((state = 'CLAIMED' AND status_code IS NULL AND response_json IS NULL)
      OR (state = 'COMPLETED' AND status_code IS NOT NULL AND response_json IS NOT NULL))
);
