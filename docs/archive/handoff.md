> **Historical backend design — superseded.** Use [the current simulation design](../SIMULATION.md) and [README](../../README.md). Retained for context; not an active implementation backlog.

# Developer handoff

## Start here

For the owner's current Codex/ChatGPT workflow, begin with the [scope review](implementation/SCOPE_REVIEW.md), [task cards](implementation/README.md) and [state ledger](implementation/STATE.md). The revised [validation gates](implementation/VALIDATION.md) define when to review. Task status belongs in STATE, not in this narrative handoff.

1. Read README prerequisites, configure the existing local database, then use `make run`; use `make preview` for the Java-served sketch without setup. Read dataset-and-scale.md for the real import boundary, product.md and design/ux.md for intended versus implemented simulation behavior.
2. Review the implemented native inventory baseline at V1. It supports authenticated reads, reservations, release and expiry; fix blocking correctness issues before building on it.
3. Implement the small Java run/load worker, then connect the existing UI and guide to a real read/reserve/release journey using polling. Use TDD and commit each passing behavior.
4. Add the first real solution through the map: a second owned API instance and readiness-gated routing. Preserve the current frontend; observability, React migration, SSE and the full Java simulator are deferred.
5. Add Redis, replication, shards and deployment one working lesson at a time under the reduced scope review. Retain ownership/correctness checks and only the feedback needed to explain the interaction.

## Proposed eventual layout (not a scaffolding requirement)

```text
services/
  inventory-service/src/{main,test}/java/.../{domain,application,adapters}
  inventory-service/src/main/resources/db/migration/
  lab-controller/src/{main,test}/java/.../{domain,application,adapters}
frontend/src/features/{lab,workloads,guide,inspector,comparison,setup}/
contracts/{openapi,events,scenarios}/
scenarios/{baseline,cache,replication,sharding,deployment}/
infra/{compose,postgres,redis,proxy}/
scripts/{doctor,launch,stop,reset}/
evaluation/{fixtures,reports}/
docs/
```

Do not create empty abstractions to fill this tree. Contract module contains DTO/schema definitions only when needed; shared business logic belongs to its owning service. Service ports/config are injected; logs contain correlation and operation metadata, not bodies.

## Dependency rationale

Spring Boot: lifecycle/HTTP/config/health for ordinary Java services. PostgreSQL JDBC + migrations: visible SQL transactions and repeatable schema. Redis client: protocol/atomic scripts and bounded timeouts. Micrometer: consistent counters/histograms, only when real observation is built. React/TypeScript/Vite: frontend interaction/state/toolchain. JUnit/ArchUnit: behaviour/boundaries. Testcontainers: isolated optional infrastructure integration. Playwright: real user journeys. No additional broker, ORM, graph editor, orchestration platform or external metrics service until a phase demonstrates the need.

## Definition of ready

Task has observable acceptance behaviour, test fixture, limits, state/error contract, expected files and approved architecture. For faults, specify ownership/recovery. For UI, specify keyboard and unavailable states. For metrics, specify provenance/window/counts.

## Definition of done

Observed red then green; related tests and phase checks pass; diff reviewed for security/complexity; docs/plan/journal/log reflect actual work; no fabricated metrics; clean-clone commands validated for supported path. Human reviews material design decisions and AI log. Stop at the phase checkpoint.

## Questions reserved for owner review

Review future frontend changes, licence, and whether native PostgreSQL replication support on macOS must be first-class in the first release. Public simulation hosting target and any recurring cost are undecided. These questions do not block the delivered prototype/design package.
