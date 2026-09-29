# StockFlow

**See an inventory service reach its limits. Change the architecture. Understand the trade-off.**

StockFlow is a portfolio project for Java engineering and distributed systems design. A guided visual lab starts with one inventory API and one PostgreSQL database. Users generate traffic, inspect requests, introduce failures, and apply scaling patterns one at a time.

## Current delivery

This repository contains the **Phase 0 design package and interactive browser prototype**. The prototype runs locally today. Its numbers are an explicitly labelled, deterministic teaching model, not Java, Redis or PostgreSQL benchmarks. Java services, real load generation, database replication and deployment automation are **planned, not implemented**.

- [Build plan and acceptance gates](PLAN.md)
- [Product scope and learning journey](docs/product.md)
- [Architecture, component boundaries and diagrams](docs/architecture.md)
- [Detailed experiment curriculum](docs/experiments.md)
- [UX, wireframes, screen states and interaction flows](docs/design/ux.md)
- [Clickable web prototype](prototype/index.html)
- [API, events, data and simulation contracts](docs/contracts.md)
- [Local Make and Docker operations, budgets and limits](docs/operations.md)
- [Testing and evaluation](docs/evaluation.md)
- [Threat model](docs/threat-model.md)
- [Proposed architectural decisions](docs/adr/001-design-baseline.md)
- [Developer handoff](docs/handoff.md)
- [Checkpoint evidence](docs/engineering-journal.md)

## Run the prototype now

Prerequisite: Node.js 24. No npm install, Java, database or Docker required.

```sh
make run
# open http://127.0.0.1:4173
```

`make test` runs the deterministic model tests. `make verify` also checks JavaScript syntax and local documentation links. `make doctor` reports tools without installing them. Ctrl-C stops the local preview. `PORT=4174 make run` changes the port.

Docker alternative, without Make:

```sh
docker compose up --build -d
# open http://127.0.0.1:4173
docker compose down
```

Or `make run-docker` / `make down`. This Docker configuration serves the **same prototype only**. Future Java/infrastructure commands are specified in [operations](docs/operations.md), and are deliberately not exposed as working commands yet. Docker build/run verification status is in the journal.

## Five-minute prototype walkthrough

1. Select **Single database**. Start traffic; raise offered load to 300 req/s.
2. Inspect the database to see demand exceed its modelled capacity.
3. Select **Caching** and enable Redis. Observe lower database demand and the cache-read path.
4. Select **Read replicas**; add a replica and inspect the read/write routes.
5. Select **Failure recovery**. Fail the primary, fence it, then promote the replica. The guide explains potential asynchronous data loss.
6. Try **Sharding** and **Blue / green**. These show intended interactions; advanced correctness experiments are specified in the curriculum.

All graph nodes support mouse hover, keyboard focus and click-to-pin. Motion can be disabled. The right panel explains the next operation and its cost. The activity log and metric breakdown remain useful without animation.

## Proposed build

Java 25 LTS + Spring Boot 4.x; PostgreSQL 17; Redis 8; React + strict TypeScript + Vite; Maven Wrapper; JUnit, Testcontainers, Playwright. Version lines are proposals; resolve supported exact patches, licence obligations, checksums and image digests during Phase 1. No Kubernetes or message broker required for the core journey.

The workload is a multi-tenant warehouse inventory API: browse stock, reserve units, release a reservation. Correctness means no overselling, idempotent retries and tenant isolation. Services are ordinary HTTP services; lesson controls and observation live in a separate control plane.

## Scope and honesty

The laptop topology shares a host, disk and network. Multiple containers do not prove multi-machine resilience or production throughput. Public deployment initially offers simulation only. Infrastructure controls are local, bounded and unavailable to anonymous visitors. No real customer data or real payments.

No open-source licence has been selected yet; public visibility alone does not grant reuse rights. AI-assisted work and pending human decisions are recorded in [AI_DEVELOPMENT_LOG.md](AI_DEVELOPMENT_LOG.md).
