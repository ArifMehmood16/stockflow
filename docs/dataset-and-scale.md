# Public catalog and scale contract

## Dataset choice

Use **Open Food Facts**. Its public catalog fits product lookups and an inventory service, and its official export contains millions of products. Bulk exports avoid sending the lab's artificial traffic to anyone else's API. Source: [official export service](https://github.com/openfoodfacts/openfoodfacts-exports), [dataset card](https://huggingface.co/datasets/openfoodfacts/product-database), [usage and licence documentation](https://openfoodfacts.github.io/openfoodfacts-server/api/).

The dataset card identifies a multi-million-row food split. Do not equate its displayed count with the selected CSV fixture: different export times, invalid/missing codes and duplicate codes affect the result. The actual imported count is authoritative.

The database is ODbL; individual contents use DbCL. Preserve attribution, provenance and the applicable database redistribution/share-alike obligations when publishing derived data. Images have separate terms and are not imported. This repository does not redistribute the catalog or claim a project licence overrides its terms. Dataset values are community contributions, not verified business facts.

## Reproducible selection

`Dataset.SOURCE` pins the official CSV gzip export to the S3 object version reached from `https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz`. Metadata was verified on 2026-09-29; the export was last modified 2026-09-28. Compressed object size observed: 1,275,171,186 bytes. The app streams rather than saving the whole compressed archive.

Selection rules, in order:

1. Decode UTF-8 and parse quoted tab-separated records with univocity; do not split on tabs/newlines manually.
2. Accept unique numeric codes of 1–32 digits, preserving leading zeros; retain the first occurrence.
3. Keep code, product name, brands and categories only. Substitute code for absent name. Remove control characters and cap fields at 512/256/512 characters.
4. Stop at `DATASET_ROWS` valid unique products or successful source EOF, whichever comes first. No synthetic product expansion. A truncated/corrupt gzip or download error is a failure, not a smaller successful dataset.
5. Store the selected CSV in ignored `.lab/dataset`, with SHA-256, pinned source URL, actual/requested counts, timestamp and whether the complete source was consumed. Partial files never become valid cache entries.

The first million selected products produced a 96 MiB local subset and SHA-256 `10de5b1696ad18f5c2811736ce01ae971dda192e0b4a5f18f466a3006ea85161` during the initial download. The Java writer can serialize equivalent CSV with different line endings; compare checksums within one manifest, not across serializer implementations. The complete-catalog checkpoint is recorded in the engineering journal.

Selection follows export order and is **not a statistically representative sample**. Popularity is a workload assumption, not inferred from export order. Checksum repeatability depends on the pinned object remaining available; a missing object must fail visibly rather than silently move to a new snapshot.

## Data model and import

Java is the sole operational/runtime tooling language. `Database.java` converts the user's PostgreSQL URL to JDBC while passing credentials separately in properties. Make uses the existing local database; Compose uses a private database service. Credentials remain in ignored `.env` or environment variables. `postgresql+psycopg` is accepted as an input scheme only.

`infra/schema.sql` creates only the `stockflow` schema and:

- `catalog(code PK, product_name, brands, categories, source)` — public product fields.
- `inventory(code PK/FK, tenant_id, bucket, on_hand, version)` — one inventory row per selected product. Tenant and stock values are deterministic synthetic fixtures, not Open Food Facts facts.
- `dataset_import(subset_sha256 PK, source_url, selected_rows, source_complete, imported_at)` — receipt committed only with a verified import.

Synthetic values use the unsigned first 64 bits of SHA-256(code): tenant = seed mod 10,000; bucket = tenant mod 16; quantity = 10 + seed mod 991. Existing quantities are never reset on restart. This is bootstrap schema; the service phase must migrate to its approved tenant/SKU and reservation contract rather than treating this seed table as a complete reservation API.

Setup takes a dataset file lock and database advisory lock. Import uses JDBC COPY into a temporary table, inserts missing catalog/inventory rows with conflict handling, verifies the selected inventory count, and commits its receipt in the same transaction. Empty text fields remain empty strings, not accidental NULLs. SQL values use prepared parameters; product text is never executable SQL. A failed insert rolls back both tables and the receipt.

## Skip contract

Before downloading or copying, setup searches for a receipt from the same pinned source that covers the requested limit, or records complete source exhaustion. It verifies that both actual table counts cover that receipt. Matching data produces **“Matching catalog already loaded; skipping download, COPY and inventory writes.”** The importer checks again under the transaction lock to handle concurrent callers.

If rows are missing or no compatible receipt exists, setup reuses a checksum-verified subset or fetches one, then inserts only missing rows. Raising the limit can extend a partial catalog. Lowering it does not delete rows. Completion at fewer rows than requested is recorded, preventing repeated attempts to reach an impossible count.

Startup counts plus a receipt are a fast completeness check, **not a full database integrity audit**: equal-count manual substitutions and altered product fields are not detected. Future recovery verification needs sorted key/content hashes, an acknowledged-write ledger and stock invariants. Never treat this startup check as an RPO/RTO or corruption-recovery proof.

## Scale and teaching order

The browser sketch owns a separate one-million-logical-record fixture, 16 equal virtual buckets and a workload slider from 1,000 to 250,000 modeled req/s. It does not fetch actual product payloads or benchmark the imported PostgreSQL catalog. Real catalog buckets are hash-distributed and will not be exactly equal. Do not combine illustrative and observed values in one benchmark chart.

Teach in this order: dataset/workload → overloaded query path → targeted improvement → new correctness/failure problem → mitigation → next bottleneck. CPU, RSS, connection limits, disk, generator capacity and Docker quotas form a later **capacity reality check**, not an early clamp on the simulated flash sale. Actual imports and future load runs must still respect process/disk limits.

Future workloads: uniform 90/10 reads/writes; write-heavy 40/60; 80% demand to one tenant; Zipf-like hot products with a published seed; 30% repeated nonexistent IDs; synchronized TTL expiry; cold/warm cache; step/ramp/burst arrivals. Report offered/completed/rejected separately, include in-flight change, and keep data distributions and workload seeds fixed between comparisons.
