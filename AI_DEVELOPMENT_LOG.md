# AI development log

## 001 — Design StockFlow and deliver an interactive prototype

Date: 2026-09-29. Tool: Codex. Phase: 0. Status: draft for human review.

### User request and human-owned decisions

Create a portfolio project plan showcasing Java and distributed systems, database scaling, replication, sharding, caching/Redis, failure recovery and blue/green deployment. Deliver architecture, UI/UX, wireframes, web view, flows and a complete developer handoff. Create a GitHub repository and a directory under the portfolio workspace. Use Make locally and provide Docker as an alternative; respect machine/service limits. User explicitly requested a more specific project name, public visibility and commits as work proceeds.

The agent proposed StockFlow, a tenant inventory teaching lab, plus exact framework/domain/runtime choices recorded as proposed in ADR 001. These proposals are not represented as already approved by the human. Licence and future hosting remain human decisions. No Java implementation phase began.

### AI contribution

Created product specification, ordered phase plan, 13 experiments, architecture/sequence diagrams, API/event/data contracts, Make/Docker operations design, resource budgets, testing/evaluation strategy, threat model, UX wireframes/flows, handoff and project governance. Built a six-chapter browser prototype with explicit illustrative-model labels, animated routes, inspection, guide actions, failure/fencing/promotion and reduced-motion control. Added Node standard-library preview server, test runner usage and documentation checks; no npm dependencies.

### Alternatives rejected or changed

- Generic Systems Lab name/private visibility changed to StockFlow/public following explicit human feedback.
- All services running at once rejected in favour of separate resource profiles.
- Full commerce service fleet, Kubernetes, broker and service mesh deferred: they obscure the first lessons and increase laptop cost.
- Fake latency/throughput presented as real telemetry rejected. Delivered arithmetic model is visibly illustrative; future simulation and measured real lab have distinct provenance.
- Automatic promotion on network failure rejected; explicit fencing/lag review/data-loss reporting required.
- A shared frontend/Java simulation algorithm rejected; browser prototype is disposable, authoritative simulation will be Java.

### Evidence

Tests: initial module-not-found scaffold failure, then four observed behavioural failures and one pass; final five model tests passed. Browser verified overload, unchanged DB bottleneck after API scale, cache demand reduction, inspector, blocked unfenced promotion, recovery and reduced motion. Exact commands, later verification and limitations in docs/engineering-journal.md. No real database benchmark was run.

### Publication and resume

Initial checkpoint commit `c1e964b` was pushed to `codex/phase-0-stockflow-design-and-prototype`. Subsequent verified checkpoint commits are discoverable with `git log`; do not invent hashes before committing. RESUME.md records the latest handoff state. Source RAG project remains unchanged. Public repository contains synthetic material and no generated credentials, local data stores or secrets.

### Follow-up

Human reviews this log and ADR 001. Run optional Docker smoke when daemon is available. Begin Phase 1 only after design review. No destructive or paid infrastructure actions were performed.

## 002 — Explain occupied preview ports without a Node crash

Date: 2026-09-29. Tool: Codex. Task: user-reported Phase 0 preview defect (PLAN 0.4a). Status: draft for human review.

The user’s `make run` failed because the agent-started preview still occupied port 4173. Stopped only the known agent-owned preview session. Added a local regression test that starts a temporary listener, launches the preview against its port, expects an actionable message and verifies the original listener still responds. The focused test initially failed on the unhandled EADDRINUSE stack trace; after adding a narrow EADDRINUSE handler it passed. Other unexpected server errors retain their existing failure behaviour.

Verification: `node --test tests/serve.test.mjs` passed (1 test); `make verify` passed (6 tests, syntax checks, 16 local links); `git diff --check` passed. The loopback test needed approved execution in this restricted environment; no public network or Docker involved. Updated README, PLAN, RESUME and engineering journal. No dependencies, automatic process termination, automatic port switching or production architecture changes. Human authorization: user reported the failure and had already requested checkpoint commits. Human review of this log remains pending.

## 003 — Move operations onto a dark architecture map and add make stop

Date: 2026-09-29. Tool: Codex. Task: user-assigned Phase 0.4b. Status: draft for human review; final browser/stop smoke checkpoint follows.

Human requested preserving the learning path, moving add-system/solution controls into the animation, showing components being built and traffic rerouting, using technology logos/names, adding `make stop`, and making the background dark with light/dark contrast.

Implemented dark workbench/light ready cards; on-component controls; timed provisioning and readiness states; no capacity/routing effect until completion; explicit green preparation before route switch; timer cancellation on reset. Guide retains explanatory steps and points to map controls. Vendored six Devicon v2.17.0 SVGs with upstream MIT notice and attribution; no runtime external requests. Python HTTPS failed local certificate validation; system curl succeeded with certificate verification enabled. No security verification was disabled.

Added tracked local preview lifecycle: per-port PID plus OS start-time/command identity in ignored .lab; stop sends SIGTERM only on a match, waits, does not force-kill. Docker leaves tracking disabled. Existing listeners are not killed by port. Test scaffolds first produced two lifecycle/readiness failures and one behavioural stop failure plus missing-registry scaffold error; implementations then passed. Current `make verify`: 11 passing tests, syntax checks, 18 local links. Compose configuration validates; Docker container run remains unverified.

Browser observed: all nine displayed logos loaded locally; no build buttons in guide; cache remains inactive during provisioning and becomes routed after ready; green route switch disabled during build and enabled after readiness; switch mutes old blue path and activates green; reset immediately returns cache to not built. Full build is still an illustrative browser lifecycle, not real provisioning. NGINX/logo labels identify proposed architecture, not a new running infrastructure dependency.

Previous local commit e41fb6a initially could not push because automatic approval review failed due to a usage limit. Further local work was preserved. Publication status is recorded in the final checkpoint rather than assumed.

### Entry 003 final checkpoint

Completed the browser checks, replaced the screenshot with the dark workbench, removed an unused edge, and fixed the narrow-screen scrollbar allowance. Final `make verify` passed 11 tests and 18 local links; Compose config and diff checks passed. The native isolated preview started on 4175 and exited 0 after `PORT=4175 make stop`; repeated stop was harmless. This preview was stopped deliberately to avoid another agent-owned port conflict. Updated PLAN 0.4b and RESUME. Checkpoint 543c683 pushed successfully, including the previously unpushed fix in its history. Production architecture review remains pending; no real Java/infrastructure claims added.

## 004 — Preserve a cumulative topology and bootstrap a real catalog in Java

Date: 2026-09-29. Tool: Codex. User-assigned PLAN 0.4c/0.4d. Draft for human review.

Human decisions: one persistent, single-view architecture; million-logical-record high-load examples; repeated multi-shard growth and fault/fix drills across components; local PostgreSQL for Make and a new isolated database for Docker; Java for all operational scripting/backend; skip already loaded data; DATASET_ROWS caps actual available public products rather than inventing duplicate products. Existing `postgres` database was explicitly selected. Local credentials are in ignored .env, not Git.

Implemented the cumulative model/UI, six-owner bucket migration, cache fault/mitigation catalog and component controls. Selected Open Food Facts with provenance/licence documentation. An initial Python shortcut was rejected by the owner and removed before this checkpoint. Replaced operational scripts with Java, using pinned pgJDBC and univocity; documented why standard JDK APIs alone are insufficient. Added schema creation, streaming selection, atomic JDBC COPY, count/receipt skip checks and safe ProcessHandle lifecycle. Frontend modeling remains JavaScript; the real service remains a future phase.

Verified 4,532,480 unique product and inventory rows in the stockflow schema. Full import caught a CSV interoperability defect at row 35,623. Reproduced it with synthetic embedded quotes (red), forced CSV quoting/escaping (green), reused and normalized the cached file, then observed full import success. Repeated make run skipped download/COPY and started Java HTTP serving. Tests and exact evidence are in the engineering journal. Removed only owned temporary integration databases and owned preview processes; did not change unrelated schemas or stop the user's database. Source/data/credential caches are ignored. Docker execution remains unverified because its daemon is stopped.

Remaining validation at this checkpoint: final broader browser/responsive checks, final diff/document review, checkpoint commit/push. The owner's rejection of Python and clarification of row limits supersede the initial shortcut and the briefly discussed synthetic-expansion approach; no ten-million-product duplication was performed.

### Entry 004 final validation

Final make verify passed 24 Java assertions, 14 frontend tests and 24 local links. Extended integration verified missing-row repair and complete-source exhaustion handling, in addition to the import/rollback checks. Full Make startup was observed to skip the 4,532,480-row import and serve the app through Java. Browser confirmed third-shard redistribution, persistent protections and canvas containment on desktop/narrow viewports with no console warnings/errors. Docker runtime verification remains unavailable; final screenshot, stop and publication status are recorded in the final checkpoint.

## 005 — Begin the real Java inventory baseline

Date: 2026-09-29. Tool: Codex. PLAN 1.1 read-only foundation, partial 1.4/1.5. Draft for human review.

Human authorization: following the status report identifying a Java API against the loaded catalog as the next milestone, the owner requested continuation. Java/Make/local PostgreSQL remain required. Proceeded with the already proposed Spring MVC/JDBC baseline and recorded the narrower continuation in ADR 001. No new licence, hosting provider, paid service or reservation schema decision was inferred.

Added pinned Java-launched Maven, one inventory-service module, framework-free stock lookup, a JDBC adapter, read-only HTTP/health contracts and bounded local service launch. The public/synthetic diagnostic endpoint is explicitly separate from the future tenant-authorized reservation API. Default UI remains modeled, not live telemetry.

Red evidence: lookup initially returned empty for known product and accepted malformed codes; HTTP tests initially returned 404 for the missing stock/health endpoints; the occupied-port command test initially lacked an error. Green evidence: 27 tooling assertions, 10 service/architecture tests and real JDBC integration passed. An intermediate Spring wiring error attempted to proxy a final @Repository; switched to @Component because the adapter already translates SQL exceptions explicitly. Removed unnecessary Mockito test dependencies/agent behavior; tests use a fake at the repository boundary.

Real integration creates/removes its own random database, verifies bound SQL, stock values, safe outage/schema mapping and unchanged inventory. Combined startup on isolated ports 4176/8085 skipped the 4,532,480-row import and started the real API and existing UI. Final smoke, lifecycle, diff, documentation and publication results follow at the checkpoint. Docker daemon is absent; no container execution claim.
