# StockFlow engineering instructions

Scope: this repository only. Read README.md, PLAN.md, this file and the latest AI_DEVELOPMENT_LOG.md entry before work. Follow the first incomplete approved task; stop at each phase gate. Phase 0 is delivered. The owner subsequently authorized continuing with the real Java baseline; implement it incrementally without implying approval of all future infrastructure choices.

Human intent: showcase Java, databases and distributed systems with an understandable interactive lab. One normal inventory microservice first; add infrastructure only to explain a demonstrated bottleneck. The separate source repository's Python/RAG architecture does not apply here.

Before edits, restate task, acceptance criteria, expected files and material assumptions. The owner-directed Phase 1 continuation in ADR 001 authorizes the Java baseline. Ask only for material new choices beyond that scope. Do not add technologies, services or cloud costs merely to look enterprise-ready.

Use red-green-refactor for behaviour: smallest meaningful failing test, observe expected failure, minimal implementation, focused green, refactor, phase checks. Documentation-only edits do not need artificial tests. Test fakes at external boundaries and real domain collaborators. Keep default tests deterministic, offline and free of Docker requirements; integration tests are explicit.

Java domain/application code must not import Spring, JDBC, Redis or container APIs. Use records, explicit transactions and ports at real external boundaries. Enforce tenant and run scope, idempotency and atomic stock updates. Use strict TypeScript in the eventual frontend. Avoid speculative abstractions and broad catches.

Distinguish prototype illustration, deterministic simulation and measured real lab data in UI, events, exports and docs. Never invent observed latency, RPO, RTO or throughput. Replicas do not create more primary write capacity. Local table partitioning is not multi-node sharding. A recovered process is not evidence of recovered data.

Fault injection is allowlisted to owned lab resources and expires automatically. Never run arbitrary shell from browser input, expose Docker sockets to a web server, or accept arbitrary load target URLs. Bind local APIs to loopback. Do not log payloads, credentials or reservation tokens. Keep secrets ignored; do not reset outside the run namespace.

Keep docs and tests in the same change. Mark PLAN checkboxes only when evidence exists. Update the threat model for boundary changes and draft a factual AI log for human review. Review the complete diff. Do not commit/push unrelated work, rewrite history or change repository visibility without authorization. Initial publication is authorized by the initial user request.

Use a task branch such as codex/phase-1-java-inventory-baseline; readable imperative commit subjects. At checkpoints report plan task/status, changed files, red/green evidence, exact verification commands, security findings, docs, residual risks and suggested branch/commit. No automatic continuation to the next phase.
