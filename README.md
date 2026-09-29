# Distributed Systems Simulator

**An interactive system-design playground: create a bottleneck, add a solution, and discover the next trade-off.**

The **StockFlow inventory scenario** lets you evolve one architecture from a single API and database into a system with caching, read replicas, multiple shards, failure recovery, and blue/green deployment. Build components directly on the map, follow animated request routes, inspect their behavior, and use the guided learning path to understand each change.

**Everything is simulated in the browser.** No backend, database, dataset, credentials, Java installation, or real load generator is required. Technology logos represent the architecture being taught—not running services.

## A two-minute walkthrough

1. Run `make run` and open [the simulator](http://127.0.0.1:4173).
2. Start traffic and set **30,000 requests/s**. The database becomes the modeled bottleneck: about **12,000 requests/s** complete.
3. Build **Redis** on the map. Repeated reads stop reaching the database; the next limit is the API at **22,000 requests/s**.
4. Add another **API instance**. After its simulated readiness check, routes change and the modeled system handles **30,000 requests/s**.
5. Inject a **cache stampede**, inspect its effect, and apply a fix. Compare what improves and what remains constrained.

These are reproducible teaching-model outputs, not measured throughput or recommended production sizing.

## What this portfolio project demonstrates

- **System-design reasoning:** read/write separation, shared bottlenecks, cache consistency, data ownership, readiness and recovery ordering.
- **Trade-offs rather than magic upgrades:** replicas do not increase primary write capacity; empty shards do not help until ownership moves; a cache introduces stale reads, stampedes and hot keys.
- **Interactive explanation:** animated builds and rerouting, component hover details, fault/fix controls, a persistent learning path, pan/zoom and reduced motion.
- **Implementation judgment:** small dependency-free browser modules, explicit state transitions, deterministic model tests, and clear boundaries between simulation and real systems.

This project demonstrates an educational model and frontend engineering. It does not claim a production distributed-system implementation or performance benchmark. An earlier Java/PostgreSQL experiment remains recoverable in Git history; it is no longer part of the app.

## Explore six connected lessons

- **Single database:** raise load, inspect capacity, and find the first bottleneck.
- **Caching:** add Redis; explore stampede, stale data, penetration, hot keys and cache crashes.
- **Read replicas:** move eligible reads while keeping writes on the primary; explore lag and fallback.
- **Failure recovery:** fence a failed primary before promotion; distinguish availability from consistency.
- **Sharding:** add up to six shards, migrate bucket ownership, and explore skew, interrupted migration and stale routing.
- **Blue/green deployment:** build a new version, wait for readiness, switch routes and roll back.

Chapters share one evolving system. Builds and failure recovery change the same architecture rather than opening disconnected demos.

## Run locally

Prerequisites: **Node.js 24+**, Make and a modern browser. `make stop` uses the macOS/Linux `ps` command. There are no npm dependencies or install step.

```sh
git clone https://github.com/ArifMehmood16/stockflow.git
cd stockflow
make run
# Open http://127.0.0.1:4173
```

```sh
make stop              # Stop this project's registered static preview
make verify            # Short model, routing, hover and static-host checks
PORT=4174 make run     # Use another port
PORT=4174 make stop
```

The Node helper serves static files only. All simulation state and behavior live in your browser; it exposes no application API. Reloading resets the scenario. Traffic animation continues until paused; it sends no network requests. `make run` ignores old `.env` files and never imports data or connects to PostgreSQL.

## Optional Docker run

Prerequisites: a running **Docker Engine/Desktop with Compose v2**. Node and Make are unnecessary on the host if you use Compose directly.

```sh
docker compose up --build -d
# Open http://127.0.0.1:4173
docker compose down
```

`make run-docker` and `make down` are shortcuts. The single container only serves static files—no database, seed job or inventory service. Docker runtime verification is separate from the local checks; this simplification does not claim it was tested here.

## Model assumptions and limitations

- **10,000–250,000 simulated requests/s** in 10,000-request steps; default mix is **90% reads / 10% writes**.
- One API assumes **22,000 requests/s**; the initial database assumes **12,000 operations/s**; a healthy cache assumes **80% eligible read hits**.
- **1,000,000 virtual records** are represented by ownership counts across 16 buckets. There is no allocated million-row dataset.
- Capacity is calculated using aggregate formulas. Animation shows a sample of the flow, not one particle per request.
- Build stages, faults, migration and recovery are illustrative. No real WAL, persistence, consensus, networking, CPU usage, latency distribution or durability is reproduced.
- Background-tab animation may be throttled by the browser. Values are scenario projections, not time-based benchmark results.

The app shows the current bottleneck and assumptions. See [the simulation design](docs/SIMULATION.md) for formulas, boundaries and extension points.

## Code map

- `prototype/model.mjs` — state transitions, capacity formulas, faults and explanations.
- `prototype/app.mjs` — lesson guidance, interactions, animation and rendering.
- `prototype/topology.mjs` — component geometry, routes and camera behavior.
- `prototype/hover.mjs` — contextual explanations and tooltip placement.
- `tests/` — quick model and interaction-boundary regressions.
- `scripts/serve.mjs` — optional local static-file preview and owned-process stop.

[Current plan](PLAN.md) · [Historical design archive](docs/archive/README.md) · [Asset attribution](THIRD_PARTY_NOTICES.md) · [Development record](AI_DEVELOPMENT_LOG.md)

A future Medium article will explain the walkthrough, model choices and trade-offs. No article or hosted demo has been published yet.
