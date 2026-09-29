BEGIN;
SELECT pg_advisory_xact_lock(73628401);
CREATE SCHEMA IF NOT EXISTS stockflow;
CREATE TABLE IF NOT EXISTS stockflow.catalog (
    code text PRIMARY KEY CHECK (code ~ '^[0-9]{1,32}$'),
    product_name text NOT NULL,
    brands text NOT NULL,
    categories text NOT NULL,
    source text NOT NULL DEFAULT 'Open Food Facts'
);
CREATE TABLE IF NOT EXISTS stockflow.inventory (
    code text PRIMARY KEY REFERENCES stockflow.catalog(code),
    tenant_id integer NOT NULL CHECK (tenant_id BETWEEN 0 AND 9999),
    bucket smallint NOT NULL CHECK (bucket BETWEEN 0 AND 15),
    on_hand integer NOT NULL CHECK (on_hand >= 0),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0)
);
CREATE INDEX IF NOT EXISTS inventory_tenant_bucket ON stockflow.inventory (tenant_id, bucket);
CREATE TABLE IF NOT EXISTS stockflow.dataset_import (
    subset_sha256 text PRIMARY KEY,
    source_url text NOT NULL,
    selected_rows integer NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE stockflow.dataset_import ADD COLUMN IF NOT EXISTS source_complete boolean NOT NULL DEFAULT false;
COMMIT;
