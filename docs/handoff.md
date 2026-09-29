# Developer handoff

## Start here

For the owner's current Cursor/Codex workflow, begin with [CURSOR](../CURSOR.md). The [task cards](implementation/README.md) split the remaining work into individual implementation requests, and [validation gates](implementation/VALIDATION.md) define when to ask Codex to review. Task status belongs in [STATE](implementation/STATE.md), not in this narrative handoff.

1. Read README prerequisites, configure the existing local database, then use `make run`; use `make preview` for the Java-served sketch without setup. Read dataset-and-scale.md for the real import boundary, product.md and design/ux.md for intended versus implemented simulation behavior.
2. Read the owner-directed continuation in ADR 001 and the implemented read-only slice in java-baseline.md. PLAN.md tracks the remaining Phase 1 work; licence and hosting decisions remain open.
3. Implement inventory correctness before scaling. Use the contracts and fixture from contracts.md; maintain test evidence and journal.
4. Build authoritative Java simulation, then port UI to React using the same run/command/event contracts. The prototype's aggregate model is disposable.
5. Add real infrastructure one lesson at a time with operations.md budgets and experiments.md acceptance criteria.

## Proposed eventual layout

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
