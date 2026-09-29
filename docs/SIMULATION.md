# Browser-only simulation design

This is the current architecture. Older documents describe the retired real-service project and are preserved as historical design work.

## Runtime

A static HTML page imports four local JavaScript modules. `model.mjs` owns pure state transitions and aggregate metrics. `app.mjs` renders the state and stages build animations. `topology.mjs` calculates routes and camera transforms; `hover.mjs` describes components and places tooltips. Node or Docker may serve these files, but no application backend exists. Network connections from browser scripts are blocked by the page's content-security policy (also sent as a header by the local host). The public GitHub Pages deployment serves the same static files.

## One evolving model

State includes offered requests/s, virtual record ownership, API count, cache/replica/deployment flags, readiness, active faults and installed protections. Switching lessons preserves state. Reset creates the initial state. A staged build becomes usable only after readiness; request routes are then redrawn. Faults and their mitigations alter the same model rather than adding disconnected examples.

The baseline has 10,000 requests/s, 90% reads, 10% writes, one API, a primary database, 16 ownership buckets and one million virtual records. No array of a million records is allocated.

## Capacity and constraints

- API capacity is 22,000 requests/s per simulated instance (up to three).
- Primary database capacity starts at 12,000 operations/s. A slow-query fault reduces it to 3,000 until mitigated.
- A healthy cache serves 80% of eligible reads; writes continue to the primary.
- A healthy replica serves read misses with an assumed 12,000 read operations/s capacity. It does not increase primary write capacity.
- Shard capacity depends on actual bucket ownership and the largest share, not merely the number of drawn nodes. Added shards start empty. Skew can still concentrate load.
- Completed requests are bounded by the tightest modeled capacity ratio. Offered requests equal completed plus rejected requests; stale reads are counted separately as a quality issue.
- Primary failure prevents writes. Fencing precedes promotion. Migration pauses writes to moving buckets while reads retain old ownership until switch.

At 30,000 requests/s the baseline completes 12,000. Adding a healthy cache lowers database demand to 8,400 operations/s and moves the bottleneck to the 22,000-request API. A second API then supports the full 30,000 under these assumptions. This scripted comparison is covered by a regression test.

## Fault lessons

The fault catalog is the source of available failures, their effects, mitigation descriptions and recovery actions. Cache stampedes amplify database work; negative caching addresses penetration; version-aware invalidation addresses stale reads; hot-key mitigation redistributes modeled demand. Crashes, replica lag, retry amplification, shard skew and invalid deployment routes have separate effects. Applying a fix does not necessarily recover a failed component. Hover details describe the active state.

## What the model does not claim

This is an aggregate teaching model, not a discrete-event network emulator or performance predictor. It has no real requests, service processes, database, transaction log, replication, durable writes or data-loss measurement. Capacity constants and fault multipliers are assumptions. Request particles are sampled illustration. Build timers are educational pacing, not provisioning measurements. A correct real-world implementation requires engineering beyond this model.

## Extending it

For a new lesson: write one focused state/metric test, add the minimal transition or fault effect, explain the trade-off in the guide/hover text, and verify routing after the change. Keep backend services, imports, telemetry infrastructure and long stress tests out of scope. Run `make verify`; browser-smoke the affected interaction.
