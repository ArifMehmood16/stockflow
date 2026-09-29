> **Historical backend design — superseded.** Use [the current simulation design](../SIMULATION.md) and [README](../../README.md). Retained for context; not an active implementation backlog.

# One system: problems, solutions and remaining limits

## Interaction contract

A chapter selects teaching content, not a new topology. Preserve workload, active faults, bucket ownership, installed protections, pending builds and traffic state. Only an explicit Reset lab clears them. Each component exposes build/operation controls and a **Faults** selector in the architecture. The on-canvas fault console injects, mitigates or recovers; the right guide explains effect, button location and trade-off. At most one active fault per component; protections accumulate across fault types and components. Recover clears the active fault but keeps protections.

Current implementation is an aggregate browser sketch with deliberately explicit constants. “Fixed” means a modeled mitigation is installed, not that Redis or PostgreSQL was changed. A mitigation may leave errors, lower capacity or stale-read risk elsewhere. Real implementations must satisfy the acceptance observations below.

## Cache

- **Stampede:** synchronized expiry turns many requests into repeated fills. Inject reduces assumed hit ratio to 5% and multiplies origin work by four. Coalescing plus TTL jitter removes the modeled amplification. Real acceptance: measure origin attempts per key/expiry window, lease owner tokens, bounded wait and behavior when the fill owner crashes. A process-local single-flight does not cover every API instance.
- **Stale fill:** a slow old read overwrites a more recent value after a write. The sketch marks 20% of cache-served reads stale. A version guard/invalidation removes the modeled mismatch. Real acceptance: compare stored versions under reordered fills; strict reads/reservations go to primary. A TTL bounds duration but does not prove freshness.
- **Penetration:** repeated nonexistent keys bypass positive caching. Inject assigns 30% of reads to missing IDs. Short negative caching absorbs an assumed 90% of repeated misses. Real acceptance: invalidate negatives on product creation; do not let unique random-key attacks fill the cache. A Bloom filter is an optional later experiment with explicit false-positive handling, rebuild/update semantics and memory cost.
- **Hot key:** one read key saturates one cache owner. The sketch caps that path at 3,000 reads/s; bounded, versioned read-only L1 copies raise the assumed ceiling to 60,000. Real acceptance: report per-key load, bounded staleness, invalidation and coalesced refresh. Additional Redis shards do not split one key; hot writes still serialize.
- **Crash:** cache hits vanish and origin demand rises. Bounded fallback admits at most 6,000 origin ops/s in the sketch, rejecting excess while Redis remains unavailable. Recover represents successful restart/warm-up. Real acceptance: timeout, breaker transitions, bounded queues, protected database and controlled warm-up. Do not call an empty restarted Redis “warm.”

## Database, API and routing

- **Single database / missing index:** show scanned rows, query plan and demand beyond service capacity; the sketch quarters the query capacity. Add a selective index and compare the same query/distribution. Real acceptance includes write cost, index build lifecycle and selectivity, not a universal speedup multiplier.
- **Primary failure:** fail → fence → inspect known lag/loss → promote only a valid replica → verify ledger → reroute. Without a replica, restart/WAL recovery requires verification before routing. Sketch buttons explain that no WAL or recovery actually occurred. Backup/PITR/corruption exercises remain specified in the full curriculum.
- **API crash:** lose an instance, route around the unhealthy process, add capacity or recover. Health routing cannot restore the lost CPU capacity. Real acceptance: readiness versus liveness, draining, connection budgets and idempotent retries.
- **Retry storm:** client/router retries amplify downstream attempts; the sketch doubles work. Install deadline-aware capped retries and jitter. Real acceptance: attempts versus original requests, overall deadline, queue bounds and unknown write-outcome reconciliation.

## Replication

- **Lag:** stale replica versions become visible. Pin freshness-sensitive reads to the primary or wait for a verified version. The sketch conservatively moves reads back to primary; demand increases there. Real acceptance: read-your-writes token, replay position, explicit timeout and source/version per read.
- **Replica crash:** reads sent to that endpoint fail. Primary fallback consumes spare write-owner capacity; it does not add capacity. Recover must mean reseed plus catch-up, not only process up. Replication is not backup; destructive changes can replicate.

## Shards and expansion

Display up to six distinct owners and their logical row counts. The primary card is S1; the cluster bank includes it for accounting and does not represent a duplicate database. Sixteen buckets, each 62,500 logical records, initially belong to S1. Adding a ready empty node does not increase effective capacity. Capacity is bounded by the largest owner share, so three nodes do not imply a perfect three-way split of 16 buckets.

Migration protocol and current buttons:

1. **Pause writes:** choose a target distribution, pause/drain only moving buckets. Old owners keep serving reads; modeled moving-bucket writes are rejected.
2. **Copy buckets:** produce a destination copy while old ownership remains authoritative.
3. **Verify copy:** the sketch exposes an explicit gate. Real mode must check counts, checksums and reservation/idempotency invariants; a click is not proof.
4. **Switch owners:** atomically advance the map epoch, update all clients, resume writes and animate new routes. Keep old copies through a defined rollback window, then retire them.
5. Cancel before switch: discard pending migration state and preserve existing ownership. Crash during copy must not produce two writable owners.

Problems:

- **Naive modulo resizing:** changing `hash(key) % N` without moving records routes requests to the wrong node. Restore stable virtual-bucket ownership; migrate before switching its epoch. Real acceptance: stale-epoch requests reject/refresh; one writer per bucket.
- **Hot tenant:** 80% of demand belongs to one owner. More nodes cannot split it under tenant sharding. Isolate admission to protect other tenants; redesign the domain partition only after demonstrating the need. Do not claim rebalance fixes a single hot row.
- **Shard crash:** fail only the affected owner group; healthy routes remain. Isolation reduces collateral damage but does not restore lost data. Recovery requires an actual shard replica or verified backup in the real implementation.

## Deployment

Build green → readiness → explicit router switch → drain blue → retain rollback. Inject a bad release to expose failures or block the readiness gate; rollback returns traffic to compatible blue. Recovery represents a corrected, ready release. Real acceptance: persist idempotency across versions, verify schema compatibility, and disable rollback after incompatible contract migrations. Blue/green does not undo database writes.

## Required real evidence

For each mechanism ship a reproducible seed/config, explicit fault interval, before/after workload match, component and end-to-end request counters, version/ownership observations, recovery ledger and remaining bottleneck. Do not invent latency/throughput/RTO from animation speed. The browser sketch currently uses aggregate capacity, not event queues or trace sampling; port interaction semantics, not its formula, to the Java simulation engine.
