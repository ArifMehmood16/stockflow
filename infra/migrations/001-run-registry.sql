CREATE TABLE stockflow_runs.ownership (
  run_id uuid PRIMARY KEY,
  schema_name text NOT NULL UNIQUE CHECK (schema_name ~ '^sf_run_[0-9a-f]{32}$'),
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
