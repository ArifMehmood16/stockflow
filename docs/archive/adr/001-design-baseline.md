> **Historical backend design — superseded.** Use [the current simulation design](../../SIMULATION.md) and [README](../../../README.md). Retained for context; not an active implementation backlog.

# ADR 001 — Proposed StockFlow baseline

Status: **proposed for human review**. User has authorized Java, microservice-based learning, frontend visualization, Make, Docker, repository creation and public visibility. Exact framework versions, inventory domain, runtime boundaries and algorithms below are design proposals; the prototype does not commit a production implementation to them.

## Decision proposal and trade-offs

- Use a tenant inventory service as the first and initially only business microservice. Reservations produce meaningful consistency requirements without a payment system. Alternative URL shortener has simpler writes but weak stock/consistency lessons; full commerce creates distracting business services.
- Use Java 25 LTS, Spring Boot 4.x MVC and JDBC with explicit SQL. Virtual threads can make blocking request handling easier, but do not enlarge CPU, connection or database capacity. Prefer JDBC clarity over ORM caching magic; introduce no reactive framework merely for throughput.
- Use PostgreSQL 17 and Redis 8 version lines, exact maintained patches/digests verified in Phase 1. PostgreSQL supports transactional inventory and native physical replication; Redis provides a real bounded cache. Review Redis distribution/licence before public image distribution. Alternatives are not needed to teach the first lessons.
- Separate lab control plane from inventory traffic. One `LabRuntime` boundary has simulation and local-process implementations. Java simulation provides meaningful Java work even without database infrastructure. Do not reproduce the authoritative simulation in the React frontend.
- Use React, strict TypeScript, Vite and native SVG graph with a fixed authored layout. The lesson diagrams have fewer than 15 nodes; a general graph editor would add bundle/accessibility cost without improving the core teaching. Reconsider React Flow only for user-editable topology later.
- Keep the load generator in the controller initially. Measure its limits; move to a separate runner only when saturation prevents valid experiments. Use lightweight bounded events/SSE first. Optional Micrometer/Prometheus/OpenTelemetry tooling comes when inspection needs it, not as mandatory local overhead.
- Use app-level tenant sharding with 16 virtual buckets and two independent PostgreSQL primaries. This makes routing and hot-tenant trade-offs visible. Local table partitioning is a separate lesson, not a sharding substitute.
- Use controlled/manual failover with verified fencing; no claim of automatic HA. Implement real failover only under an owned local supervisor. A container restart is not failover; a promoted async replica can lose acknowledged writes.
- Local Make runs host processes and native data services. Docker is a separate equivalent path, not a hidden Make prerequisite. No mandatory Kubernetes.

## Consequences

There are three deliverables over time: current UI prototype, deterministic Java simulator, and real local lab. Event and command contracts align them, but their values must not be conflated. The supervisor and real database topology need more engineering than a fake dashboard; implement in incremental phases and prove data invariants each time.

## Human review gate

Approve or amend domain, Java/framework/storage version lines, native-service support scope, licence choice and the 8–12 week core scope before Phase 1. Approval of this planning task does not mean that each proposal has already been reviewed. Public hosting platform and any spending remain undecided.

## Owner-directed bootstrap amendments — 2026-09-29

Accepted by explicit user instruction: operational scripts and backend use Java; Make reuses the existing local PostgreSQL database (the user's target is `postgres`); Docker creates an isolated PostgreSQL service; setup creates its dedicated schema and imports a public dataset; repeated startup skips a verified loaded dataset. The owner clarified DATASET_ROWS is a maximum bounded by available public products, not a request to manufacture extra products.

Java 25 bootstrap now uses pgJDBC 42.7.13 for PostgreSQL/COPY and univocity-parsers 2.9.1 for streaming quoted TSV/CSV. The standard library does not provide those two capabilities. Their jars are fetched from Maven Central with committed SHA-256 pins, not committed binaries. This small bootstrap uses the JDK compiler; Maven/Spring service implementation remains the subsequent reviewed phase. The temporarily introduced Python importer was rejected by the owner and removed before delivery.

## Owner-directed Phase 1 continuation — 2026-09-29

After being told the next concrete milestone was a Java API using the loaded PostgreSQL catalog, the owner requested “continue”. This authorizes beginning that existing baseline scope; it does not imply a public deployment, paid infrastructure, licence selection or wholesale approval of every future topology.

First slice uses the previously proposed Spring MVC/JDBC approach: Java 25, Spring Boot 4.0.8, Maven 3.9.11, pgJDBC 42.7.13. [Spring's requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) confirm the selected Java version is supported; Maven Central artifact availability was checked. A Java Maven bootstrap replaces the originally sketched shell wrapper to honor Java-only scripting. Spring supplies the web server, dependency configuration and bounded Hikari connection pool; JUnit supplies repeatable service tests. JDBC stays explicit. No ORM, controller service, cache or additional business service was added.

The imported catalog uses numeric tenant IDs and product codes, while the proposed reservation contract describes UUID tenants/warehouses and run isolation. Avoid inventing a migration or changing either contract in this slice: expose a separate read-only `/v1/catalog/{code}/stock` diagnostic endpoint over public/synthetic data. Implement authenticated run/warehouse reservations separately in 1.2/1.3. This endpoint is local-only, has no mutation capability, and is not proof of tenant isolation.
