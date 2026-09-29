# Distributed Systems Simulator engineering instructions

Scope: this repository only. The owner's current direction supersedes the historical Java/backend plan: this is a browser-only educational simulator. All services, database access, datasets and real load generation are out of scope. Node serves static files only; Docker optionally serves the same files. Preserve existing local PostgreSQL data and ignored legacy files unless explicitly asked to delete them.

Read README.md, PLAN.md, docs/SIMULATION.md and the latest relevant AI_DEVELOPMENT_LOG.md entry. Historical documents are labelled; do not resume their backend tasks. Implement the smallest requested improvement with short focused tests and regular readable commits/pushes. No long live testing, observability platform or framework rewrite.

Keep one cumulative architecture, the learning path, component-level actions, staged readiness, readable routes, pan/zoom and useful cursor-adjacent hover details. Make capacity assumptions, simulated faults and consistency trade-offs explicit. Never describe simulated values as measured throughput or real durability. Replicas do not add primary write capacity; empty shards do not add balanced capacity; recovery is not proof of data recovery.

Use a focused failing test before changing behavior, then minimal implementation and relevant checks. Prefer pure model tests and small interaction-boundary tests. Run make verify and a short browser smoke for visible changes. Do not introduce dependencies without a concrete need. No external requests from the simulation. Keep credentials and legacy .env/.lab contents ignored and never expose them from the static host.

Update current documentation and add a factual AI log entry. Preserve historical evidence without claiming old milestones passed. Use a dedicated codex/ branch and clear commit subjects. User-authorized repository metadata updates and normal progress pushes are allowed; do not rewrite history or rename/delete repositories without authorization.
