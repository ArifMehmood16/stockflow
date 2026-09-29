# Guided experiment curriculum

Lessons share one evolving fixture and topology. Changing chapters preserves architecture, workload, in-flight builds, faults and installed protections. Only explicit Reset lab restores the baseline. Save a comparison checkpoint before changing one variable; rerun a matched workload, inspect evidence and discuss the remaining cost. The right guide points to controls in the architecture. The current aggregate sketch implements the fault/fix interactions in [the playbook](system-failure-playbook.md); full service-level acceptance behaviors below remain implementation tasks.

## 01 — One service, one database

Question: where does a request spend time? Start at 50 req/s → click **Run baseline** → hover/focus API then database → inspect one read and one reservation. Raise **Offered load** until latency/queue grows. Look at offered vs completed, acquisition wait, query time and generator drops. The service may be idle waiting for a connection; CPU alone does not explain saturation. Pass: UI identifies the observed saturated resource, and conservation counters reconcile. Avoid guessing a universal database RPS limit.

## 02 — Scale service instances and expose the next bottleneck

Overload one API with bounded synthetic work → **Add instance** → inspect load-balancer routes → compare. Then choose DB-bound workload; repeat and see primary capacity/lock bottleneck remain. Show total pool connections before and after. Pass: client state/idempotency shared correctly; no oversell; UI explains why two APIs do not necessarily double throughput. Add **Limit concurrency** to show controlled rejection instead of unbounded queue growth.

## 03 — Queries, indexes and contention

Choose **Slow lookup** → inspect query plan summary → **Add supporting index** → rerun same query. Separately choose **Hot SKU** and show row-lock contention persists despite index/service scaling. Use a bounded fixture and allowlisted migration, never arbitrary SQL. Pass: rows scanned/query time come from database evidence; negative-stock invariant holds. Trade-off: index storage and write maintenance. Do not use a deliberate sleep as proof of index improvement.

## 04 — Cache-aside reads

Set 90/10 mix → run baseline → **Enable Redis** → follow miss → DB → fill → hit path → compare warm and cold windows. Inspector shows key scope, value version, TTL and source. Mutations always use primary. Pass: hit ratio denominators include eligible reads only; write-heavy preset demonstrates smaller benefit. Cache memory and stale display are costs.

## 05 — Stale reads and invalidation races

**Prime cache** → **Reserve units** → **Read display stock** → compare cached vs authoritative version. Select **Delay cache fill**, read old row, commit a write/invalidate, then resume delayed fill: delete-on-write alone can reintroduce old data. **Enable version guard** prevents older fill replacing a newer known version; **Require fresh read** bypasses cache. Pass: timeline identifies exact stale interval and limits; no promise of instantaneous consistency when invalidation is lost. TTL and outbox delivery are additional mitigations with different guarantees.

## 06 — Stampede, eviction and cache outage

**Expire popular keys** under concurrent reads → observe DB miss fan-out → **Coalesce misses** and **Jitter TTLs** → compare. Then **Fill cache** until eviction and **Stop Redis**. Show bounded fallback, circuit state and rejection count. Pass: no lock waiter leak; failed loader releases/lets lease expire; missing-item negative cache has short TTL; cache outage cannot overload an unbounded fallback path. Hot keys and poor key cardinality remain visible. In-process coalescing versus cross-instance coordination is explicit.

## 07 — Read replica and read-your-writes

**Add replica** → wait until ready → **Route eventual reads** → writes remain on primary. **Delay replica replay** → reserve → immediate read sees older version. **Require session consistency** routes primary or waits with bound for a verified eligible version. Pass: source/version shown, writes never target standby, lag unavailable renders unknown; extra replicas don't increase primary write capacity. Trade-off: memory/storage/network, replay conflicts and stale reads.

## 08 — Crash recovery and safe failover

Branch A: no replica → **Crash primary** → requests fail → **Restart primary** → WAL recovery → check ledger. Branch B: replica ready → **Fail primary** → writes halted → **Fence old primary** → review lag/known writes → **Promote replica** → reroute → verify → **Reseed old node**. Pass: promotion impossible before fencing; lost/unknown acknowledged-write outcomes explicit; no dual-primary write routes. RTO and known-write RPO are measured only in real mode. Simulation can compare synchronous availability trade-off. “Green” means ready plus evidence, not no data loss.

## 09 — Partitioning versus sharding

Show a range-partitioned table inside one DB, then **Add shard** using two actual DB instances. Inspect bucket map and tenant routing. Rerun balanced workload and compare per-shard load. Pass: all operations for tenant remain co-located; adding a DB without moving buckets is shown as no change. Trade-offs: cross-shard queries, connection multiplication, schema rollout and operational overhead.

## 10 — Hot tenant and migration

**Concentrate traffic** → one tenant saturates a shard → **Move bucket** → watch pause/drain/copy/check/switch/resume stages. A very hot tenant remains indivisible. Inject **Interrupt copy** to retain old ownership. Pass: counts/checksums/reservations/idempotency records match; stale epoch rejected; no success admitted during write pause. Optional advanced re-keying splits a tenant, but requires a new domain/transaction design and is not a magic toggle.

## 11 — Blue/green deployment

Run blue v1 → **Prepare green v2** → readiness and synthetic smoke → **Switch traffic** → watch version tags and blue drain → **Rollback**. Repeat with broken green; switching must be blocked. Show expand/contract schema sequence and why dropping an old column removes rollback safety. Pass: persisted idempotency survives version routing; no unready target; at most one configured write route; report counts timeout/unknown outcomes. Extra pool capacity during overlap is a cost. Canary percentages are an optional separate experiment.

## 12 — Timeouts, retries and recovery from overload

**Delay database** → observe queue and timeouts → **Set deadline** → **Enable bounded retry** → inspect retry amplification → **Open circuit** / **Rate limit**. Pass: retry budgets apply once, total duration bounded, writes retried only with stable idempotency key, saturation triggers admission rejection. A breaker isolates a dependency; it cannot repair it.

## 13 — Backups and data recovery

**Create checkpoint backup** → mutate fixture → **Corrupt owned fixture** → show replica also has corruption → **Restore to new database** → compare acknowledged-write ledger/counts → route only after validation. Pass: backup restore is proven with a checksum and invariant results; RPO depends on backup/WAL retention; no destructive reset of external data. Point-in-time recovery with continuous WAL archive is stretch, labelled separately from core snapshot backup.

## Capstone and portfolio story

“Keep stock correct during a flash sale, then deploy a change and lose the primary.” User chooses architecture under a resource budget and explains costs. Score explanation and invariant evidence, not maximum RPS. Export before/after config, latency histogram, error categories, resource usage, correctness results and limitations. No unsupported claim of production HA from a laptop.
