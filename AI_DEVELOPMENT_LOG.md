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

### Entry 005 final checkpoint

Implementation `e13cd91` pushed on the dedicated Phase 1 branch. Final verify passed 28 tool assertions, 10 service tests and 14 frontend tests; real JDBC integration passed and removed its database. The packaged API matched the loaded catalog both before and after a clean rebuild. Startup uses an owned JAR copy to isolate a running JVM from build output changes. Initial class-loading failure did not recur in the final checks; its original cause remains unestablished and is recorded in the journal. Safe stop and repeated stop passed on the agent's isolated ports; PostgreSQL was left running. Docker config passed; daemon execution, full Phase 1 correctness/coverage/CI and measured frontend integration remain pending. Documentation and resume handoff updated for this exact boundary.

## 006 — Add the StockFlow favicon

Date: 2026-09-29. Tool: Codex. User-requested PLAN 0.4e. Draft for human review.

Added an original mint/navy SF monogram as a small, font-independent SVG, linked it from the page head and included its exact path in the Java preview allowlist. The existing Docker image copies the prototype directory, so no container-specific asset configuration is needed. No new dependency, external image, script or network origin was introduced.

Verification: local preview returned HTTP 200 with image/svg+xml and the page contained the favicon link; xmllint accepted the SVG; make test-java passed all 28 existing assertions; git diff --check passed. This small static-asset change did not require additional tests. Container execution and browser-tab rendering were not separately tested.

## 007 — Hand implementation to Cursor with scoped Codex reviews

Date: 2026-09-29. Tool: Codex. Documentation-only owner request.

The owner requested a detailed plan for Cursor to implement item by item, with checkpoints where they ask Codex to validate, to reduce repeated token use. Created CURSOR.md with reusable prompts, 43 individually readable task cards mapped to PLAN phases, one status ledger, ten validation gates V0–V9 and a compact review-packet template. Each card specifies prerequisites, inputs, deliverable, behavioral/direct validation, acceptance, verification, evidence and excluded scope. Agents stop after one item; Cursor cannot accept its own checkpoint.

Inspected the existing plan/contracts, Java read-only boundary and pool configuration, imported schema, lifecycle tooling and UI/failure specifications. First task resolves numeric imported tenants versus UUID reservation/run examples, writable fixture size/isolation and credential/role decisions before any mutation code. Marked real/simulated evidence, existing versus future commands and Docker's unverified status explicitly. Preserved Java tooling/backend, Make reuse of local PostgreSQL, Docker isolation, min(requested, available) dataset selection, one fitted cumulative map and all requested cache/shard/recovery/deployment lessons.

Updated AGENTS/PLAN/README/backlog/handoff/resume to point to the same workflow and reduce unnecessary full-document reads. No new implementation, dependency, migration, dataset import or architecture decision was executed. Review gates reflect the owner's explicit coordination request; they are not an invented approval requirement. Verification and publication evidence follows in the journal. This log remains a draft for human review.

## 008 — Freeze the writable inventory contract

Date: 2026-09-29. Tool: Cursor. Task: A01. Status: draft for human review. Checkpoint V0 is pending and is not accepted by this entry.

The owner asked to start at task A and continue. A01 is the first card and ends in a review stop, so A02 was not started.

Recommended one decision per open issue: diagnostic integer tenants stay on the immutable public catalog; lesson stock uses UUID tenants and warehouses in a run-owned schema; version starts at 0 and increments only with available stock; expiry and release credit stock once; idempotency is a same-transaction unique-key claim with stored insufficient-stock replay; the fixture CLI issues HMAC tenant tokens before a controller exists; catalog reads and run writes use separate database roles. The small fixture is 400 rows. An explicit `FIXTURE_ROWS` value may use up to the catalog rows actually present, after a disk check, and must record `actual_rows`. The old 100,000-row cap is withdrawn. Future shards copy a local catalog snapshot and do not foreign-key back to the bootstrap database.

No runtime code, migration, credential, or database write. Link check and static SQL inspection are in the engineering journal. Licence, hosting, and owner acceptance of this recommendation remain open.

### Entry 007 validation

Observed: 195 local Markdown links passed; a one-off Java structure check passed all 43 cards, required sections, predecessor/gate consistency and untouched future statuses; diff whitespace check passed. Existing commit `46bcbe6` contains the handoff; preserved it and added only this verification record. No future task or checkpoint is represented as implemented/accepted, and no application/database work was performed for this documentation request.

## 009 — Repair topology layout and runtime interactions

Date: 2026-09-29. Tool: Codex. Owner-requested PLAN 0.4f; draft for human review.

Reproduced card/header overlap, repaired padding/grid rows, separated topology rows and adjusted route geometry. The owner refined hover behavior during implementation: allow surrounding overlap and follow the cursor. Replaced the initial conservative placement accordingly; the inspected card remains clear and the overlay never captures pointer input. Added current lifecycle/fault/mitigation summaries shared with Inspector, keyboard dismissal and viewport handling.

Runtime additions now preserve existing shard elements, animate new arrivals and ownership changes, expose per-shard details and respect reduced motion. Build animation stays inside reserved component slots. Added six hover/summary tests, wired them into Make and registered the exact Java static module route. No new dependency, database work or infrastructure feature.

Verification: browser red showed all nine headers overflowing; behavioral tests exposed incorrect availability and missing empty-shard detail before fixes. Full verification passed 28 Java tooling assertions, API tests, all 20 model/hover tests, syntax and 202 local documentation links. Browser evidence includes six shards, migration, new-tile animation, per-shard inspection, pointer movement, keyboard dismissal, automatic readiness update, cache fault/fix, narrow layout, motion off, cancel and reset. Details and residual phone readability constraints are in docs/ui-interaction-verification.md and the journal.

Preserved concurrent Cursor contract/review commits. This repair neither accepts V0 nor starts A02. The human owns visual acceptance. No production readiness or measured-performance claim was made.

## 010 — Review checkpoint V0 independently

Date: 2026-09-29. Tool: Codex. Owner explicitly requested checkpoint V0 review; draft for human review.

Reviewed only A01's docs range `9eb2e03..7772cbe`, its V0 packet and relevant baseline/schema/contracts. Confirmed diagnostic preservation and run ownership are described, but requested three corrections: recover correctly from duplicate-key transaction failure before replay; define session-token issuance separately from tenant credential issuance; align retention permissions and timestamps with the promised cleanup. Also noted the small-fixture assumption of at least 100 catalog products. Verified PostgreSQL behavior against official version-17 transaction/INSERT docs rather than executing a migration.

Recorded changes_requested in the packet and STATE. Java link check and diff whitespace check passed; no runtime code, credential or database was changed for this review. Did not approve the proposed contract or begin A02. Cursor should correct A01 and the owner should request V0 recheck.

## 011 — Correct V0 findings R1–R3

Date: 2026-09-29. Tool: Cursor. Task: A01 recheck preparation. Status: draft for human review. This entry does not accept V0.

The owner asked to fix findings R1–R3 only. ADR 002 now uses conflict-safe idempotency insertion so a committed duplicate can be read without an aborted transaction. The inventory service issues session tokens into the stored reserve body; the fixture CLI still issues only tenant credentials. Retention deletes belong to a per-run cleanup role, and reservation retention starts at `terminal_at`. The smaller-catalog note was not changed. No runtime code, migration, or credential was added. A02 stays blocked until a recheck accepts V0.

## 012 — Separate replica flows and add canvas pan/zoom

Date: 2026-09-29. Tool: Codex. Owner-requested PLAN 0.4g; draft for human review.

Fixed ambiguous write labeling beside the replica-read line and route highlights that erased flow colors. Added typed route definitions, distinct primary-write/replica-read/WAL labels and arrowheads, replica-failure read fallback and wider component gutters. Added bounded drag/wheel/button/keyboard navigation, focus reveal and Fit system while preserving camera state during builds. Updated the fit-only product/handoff wording because the owner now explicitly requests pan/zoom.

Six topology/camera regressions join the existing suite. A line-through-shard failure and an offscreen-focus browser failure were corrected before completion. Full verification passed 28 Java tooling assertions, API tests and 26 frontend tests; browser checks covered semantic routing in blue/green, failure fallback, mouse/keyboard navigation, narrow Fit, hover and build camera stability. Exact evidence is in the journal and UI verification document. No dependency, database operation or V0 review was introduced; concurrent Cursor documentation remains intact.

## 013 — Recheck V0 contract corrections

Date: 2026-09-29. Tool: Codex. Owner-requested V0 recheck; draft for human review.

Reviewed A01 correction head `44008c0` against the previous three findings, relevant contract documents and baseline scope. R1's conflict-safe claim and separate replay read, R2's session issuer and stored replay token, and R3's terminal retention clock and per-run table privileges are specified. The cleanup role's table-level `DELETE` still permits removal of active reservations and unexpired idempotency rows, although contracts claim the role itself can delete only retained rows. Requested a focused correction that either enforces row predicates in PostgreSQL or accurately assigns them to a trusted worker and updates all role-level claims. PostgreSQL 17 privilege and row-security documentation supports the distinction.

## 013 — Correct V0 finding R3a

Date: 2026-09-29. Tool: Cursor. Task: A01 focused recheck. Status: draft for human review. This entry does not accept V0.

The owner asked to continue and to create the required branch. A02 stays blocked. Branch `codex/a01-v0-retention-authority` was created from `main`. The retention clocks are now trusted-worker `DELETE` predicates. The cleanup role grant remains table-wide on the two history tables and is no longer described as rejecting an early delete. No row-security policy, migration, or runtime code was added.

Independent Java documentation link check passed 203 links before the packet edit and 206 afterward; both reviewed Git ranges passed whitespace checks. No migration, live database, or runtime verification was run for this documentation gate. Recorded changes_requested in V0 and STATE. A02 remains blocked.

## 014 — Consolidate StockFlow history on main

Date: 2026-09-29. Tool: Codex. Owner requested merging the existing branches into `main` and removing obsolete branches; draft for human review.

The three branch tips formed a linear history: Phase 0 was an ancestor of Phase 1, which was an ancestor of the Cursor handoff. No open pull requests existed. Updated the resume instructions, Cursor branch guidance, state ledger and public experiment link before creating `main` at the complete history. Set `main` as the GitHub default, then deleted the three old local and remote branch names. Their commits remain reachable on `main`; no history was rewritten.

`make verify` passed after retrying with loopback access: 28 Java tooling assertions, Maven API tests, 26 frontend tests, JavaScript syntax and 207 documentation links. The first sandboxed run could not bind a local test port; it did not indicate a product failure. V0 remains changes_requested and A02 remains unstarted.

## 015 — Move controls into the animation canvas

Date: 2026-09-29. Tool: Codex. Owner-requested Phase 0 workbench refinement; draft for human review.

Moved the full-width workload, capacity and fault rows into two compact canvas-edge docks. Load and rate statistics stay expanded vertically; the fault drawer starts collapsed and opens from each component’s Faults button. Added 10,000 req/s load steps, inset-aware Fit behavior on wider screens, and pointer handling so dock actions do not pan the map. No service or database contract changed.

Behavioral tests first failed for the old 8,000 req/s baseline and missing Fit inset, then passed after the changes. Full `make verify` passed 28 tooling assertions, Maven API tests, 28 frontend tests, syntax and 207 documentation links. Browser checks covered desktop and phone widths, load rates, drawer target/focus, and dock bounds. The rates remain illustrative. V0 and A02 status are unchanged.

## 016 — Accept the corrected V0 contract

Date: 2026-09-29. Tool: Codex. Owner requested review of completed work and continuation; draft for human review.

Reviewed the already-merged canvas change and the separate A01 retention correction. The canvas remained illustrative, and `make verify` passed. The A01 contract now states its cleanup role has table-wide DELETE on two history tables and gives the 24-hour predicates to a trusted worker. This resolves the earlier false claim of database-enforced row retention. V0 was accepted on correction head `c5e8599`; V1 must test worker predicates and keep the cleanup credential out of HTTP handlers. The smaller-catalog fixture wording remains a non-blocking follow-up.

No migration, runtime write, or Docker fault was performed in this documentation review. The owner requested that Codex and ChatGPT continue development without Cursor; the workflow documents will be updated separately.

## 017 — Reproduce the Java build and add CI

Date: 2026-09-29. Tool: Codex. Task: A02; draft for human review.

The owner switched implementation from Cursor to Codex/ChatGPT. Updated the handoff, then pinned official multi-architecture Java/PostgreSQL image indexes, added Java/Maven toolchain enforcement, compiler warnings, JaCoCo core-policy thresholds, `make lint`, and a read-only commit-pinned CI workflow. Kept the one-module Spring service and separate manual database integration path. The clean-room build without installed Maven succeeded; cached offline verification and `make verify` passed. The compiled architecture guard and coverage threshold were deliberately exercised with temporary failing probes, then returned to green. Exact evidence, measured package coverage, warning inventory and unavailable Docker daemon are in the engineering journal. CI has not yet run on GitHub at this log entry. No database data or credentials were changed.

## 018 — Redirect effort to functional backend scenarios

Date: 2026-09-29. Tool: Codex. Owner instruction; draft for human review.

The owner clarified that UI-visible scenarios need real backend and resource behavior, and that CI/CD and Docker polish are not priorities. The A02 GitHub job had passed, but its workflow was removed from the pull request. Local Java build checks and the verified image references remain. Work now continues with A03's database-backed writable fixture before moving through reservation and resource scenarios. No CI/CD completion is claimed.

## 019 — Add real run-owned inventory fixtures

Date: 2026-09-29. Tool: Codex. Task: A03; draft for human review.

Following the owner's backend-first direction, added PostgreSQL run migrations and the explicit Java fixture command. Kept the existing public catalog/diagnostic inventory unchanged and created one small and one million-row run-owned fixture on local PostgreSQL. The migrations carry SHA-256 checksums; the registry records READY only after stock, role grants and credential files are prepared. Tests exercised checksum mismatch, interruption rollback/retry, distinct-code capping, repeat preservation, run ownership checks and real role denial across schemas. The source importer and read-only API were not rewritten. Exact commands and observed counts are in the engineering journal. The next backend work is authenticated run reads and then durable reservation writes; the current UI remains illustrative until those services are connected.

The A03 review exposed a remaining owner-login dependency in native API launch. Added a failing configuration test, moved native launch to the catalog-reader credential, and retained owner access only for trusted fixture/isolated integration tooling. A second local API on port 8083 passed live, ready and stock smoke, then was stopped by its owned registry. This correction is part of A03, not a claim that authenticated run HTTP routes exist.

## 020 — Authenticate run-owned stock reads

Date: 2026-09-29. Tool: Codex. Task: A04; draft for human review.

Added a local fixture tenant-token issuer, strict HMAC verification and run-scoped primary stock/reservation GET routes. Native API launch uses a run writer login only when `RUN_ID` selects an owned fixture; the diagnostic catalog reader remains separate. The service derives the schema from the verified configured UUID and binds all stock/reservation keys in prepared SQL. The blanket write deny remains until reservation routes are implemented. HTTP tests covered missing, forged and wrong-run tokens, scoped missing resources, session token verification and unavailable eventual reads. `make test-api`, `make test-api-integration` and `make test-java` passed. A live port-8083 API read returned the fixture's 1000 available/version 0; missing credentials returned 401 and eventual returned 409. The small fixture contains matching SKU/warehouse rows for both tenants, so those reads correctly return each tenant's own stock; a reservation ownership check follows A05. The temporary API was stopped. OpenAPI and a full live two-run denial matrix remain to be completed at A07.

## 021 — Reserve stock with a stored idempotent response

Date: 2026-09-29. Tool: Codex. Task: A05; draft for human review.

The focused HTTP test first failed with 405. Opened only the authenticated reservation POST, then added one PostgreSQL transaction for claim, conditional stock decrement, reservation, session token and stored response. Integration against a disposable database showed a 10-unit row becoming 3 after a 7-unit reserve, unchanged after replay and changed-payload conflict, and then 0 after one of two concurrent 3-unit requests succeeded. Two reservations and version 2 remained. `make test-api-integration` and `make test-api` passed. A live port-8083 API returned 201, identical replay bytes, and 404 for the other tenant's reservation; it was stopped after the check. UI data remains illustrative. Expiry and release are the next task.

## 022 — Release and expire stock exactly once

Date: 2026-09-29. Tool: Codex. Task: A06; draft for human review.

The focused release HTTP test failed with 405 before the route was added. The first live bodyless release exposed a 413 filter bug; a raw-HTTP regression test reproduced it, then passed after the route-specific body rule was corrected. Release and expiry now compete on one conditional ACTIVE-to-terminal row update, crediting stock and incrementing its version in that transaction only for the winner. A bounded scheduled scan resumes overdue rows after service restart. Disposable PostgreSQL integration covered duplicate release, two expiry workers, restart and release versus expiry; the final ledger returned to 10 available with one increment per terminal transition. `make test-api-integration` and focused HTTP tests passed. A second live port-8083 reserve/release returned 201/200 and RELEASED, then the temporary API was stopped. The complete A07 gates remain pending.

## 023 — Check the native Phase A baseline

Date: 2026-09-29. Tool: Codex. Task: A07, partial; draft for human review.

Added selected-run readiness, a bounded run writer connection pool, fixed malformed-request and Origin errors, and an OpenAPI description of implemented routes. Focused tests first showed readiness 200 on a down run and missing malformed-request envelope, then passed. `make verify`, `make lint`, `make test-integration`, `make test-api-integration`, `make api-smoke` and `docker compose config --quiet` passed with the results in the engineering journal. The native API refused an occupied port, stopped cleanly, restarted, and replayed the same stored reservation body. Docker runtime smoke was not run because `docker info` could not reach the daemon. No V1 acceptance or Docker equivalence is claimed. The owner prioritized a simple native backend over Docker polish and CI/CD; this record does not treat that priority as a passing container test.

## 024 — Reduce the complete development backlog to working features

Date: 2026-09-29. Tool: Codex; draft for human review.

The owner requested review of all development items, minimum implementation, no observability at this stage, TDD and regular commits. Reviewed all 43 task cards, V0–V9 and related architecture, contracts, evaluation and handoff references. Added a per-card scope review and changed the execution order to a bounded Java run worker, existing browser integration, then a real second API instance. Retained the requested cache/failure, replication/recovery, multi-shard migration and blue/green lessons, with minimum behavior per feature. Deferred simulator parity, React migration, SSE, telemetry, advanced reporting and Docker equivalence proof until needed/release. Updated instructions and stale handoff claims; historical evidence and checkpoint verdicts were not rewritten. This is documentation-only work: no runtime code, test thresholds or passing/acceptance claims changed, and no artificial TDD test was added.

Verification: the Java documentation checker passed 269 local links; comparing review entries with task filenames found all 43 IDs exactly once; `git diff --check` passed. Application tests were not rerun for this documentation-only change.
## 025 — Start bounded real inventory traffic

Date: 2026-09-29. Tool: Codex; draft for human review.

The owner asked to start implementing the reduced functional plan. Added a Java arrival scheduler with rate 1–50 cycles/s, duration 1–30 seconds and concurrency 1–8. Excess arrivals are counted as dropped, manual stop interrupts outstanding work, and scheduled expiry lets accepted work drain. A fixed loopback target performs read, reserve, release and final read against the selected owned run. The Java preview exposes local start/stop/status while the browser remains illustrative. No arbitrary target URL or modeled 250,000 req/s path was introduced.

Tests failed first for missing scheduler, target and control, then passed. A live fixture check initially failed because PostgreSQL-normalized JSON includes spaces around `id`; a regression test reproduced that failure before the parser was corrected. A second live issue counted expiry interruption as a failed cycle; a focused test led to graceful scheduled drain. The final local check on the small owned fixture at 1 cycle/s for 2 seconds reported offered 2, completed 2, failed 0, dropped 0 and final stock 1000/version 4. The temporary API and preview were stopped. Docker and browser integration were not exercised. This is a partial B01/D01 delivery under the scope review; V1 remains pending.
## 026 — Connect the first guided browser run

Date: 2026-09-29. Tool: Codex; draft for human review.

The owner requested continued functional implementation. Connected the existing dark workbench's workload dock and first right-hand guide to the bounded Java traffic control when an owned `RUN_ID` is selected. The dock switches to 1–50 real cycles/s, Start/Stop, offered/completed/failed/dropped and in-flight counts, latest cycle time, stock and version. The first guide explains the four-request cycle. The million-row topology and other solution controls remain labelled as illustrative. A preview without a selected run retains the original model behavior.

A new browser-client test first failed because the fixed-path client module was missing, then passed. Node model tests passed. In the in-app browser on a separate local port, starting the selected small fixture produced rising measured counts and final 15 offered/15 completed/0 failed/0 dropped, stock 1000/version 30; Stop worked through the visible button. A second local preview without `RUN_ID` showed MODEL and the original 10,000 modeled req/s controls. Temporary API and preview processes were stopped. This is a partial C02/D03 delivery; other architecture controls and V1/V3 review remain pending.
## 027 — Keep optional traffic loading from breaking the workbench

The owner reported a distorted page. Browser inspection showed empty chapters and guide with an unfitted diagram; the local server was temporarily unreachable during inspection. The new traffic client was a mandatory static import, which can prevent all startup on an older preview process whose asset allowlist lacks that module. Made that import optional after the base UI initializes. A compatibility regression failed on the static dependency before passing with the change. All 30 frontend tests and `make lint` passed. Once the existing local preview was reachable again, browser inspection confirmed six chapters, guide content and a fitted diagram; Zoom changed 44% to 55% and Fit restored 44%. No claim is made that the original missing-asset HTTP response was captured. Added restart guidance; no database or CSS changes.
## 028 — Add one real API instance from the workbench

Owner direction: continue quickly with minimum functional work. Added a small owned API pool using the existing Java launcher, adjacent port and readiness check. Requests alternate between ready processes; removal requires stopped/drained traffic and closes only the extra owned child. The first guide and extra API card now build/remove the real process, animate provisioning/rerouting and show cumulative dispatched requests. Real inspectors describe the running API/dispatcher/database. Unimplemented resource mutations are disabled in real mode.

TDD: the pool test failed before the class existed; the browser client test failed before add/remove methods existed. Focused tests then passed, followed by `make verify` (53 Java tooling assertions, API/coverage checks, 30 frontend tests and lint). `RUN_ID=4646aad0-3c39-499c-92c8-7ca4cd96ca81 API_PORT=8083 make test-scaling` started two ready JVMs, verified identical durable reservation replay across them, restored stock after release, split four requests 2/2, removed the extra JVM and retained the primary route. Browser testing on a separate owned preview verified provisioning, both cards at 18 dispatched requests, Stop/removal and the live inspector. Temporary processes were stopped. No throughput improvement, Docker equivalence or independent checkpoint acceptance is claimed. Draft for human review.

Final UI regression: reopening/changing the fault panel could re-enable modeled fault injection in real mode. A focused test reproduced that behavior, then passed after the panel itself enforced the mode restriction. All 31 frontend tests, JavaScript syntax and whitespace checks passed after this correction.

## 029 — Honor the saved run selection for plain make run

The owner reported the Browser only message after `make run`. Inspection found no selected RUN_ID and that the settings allowlist and API/preview launchers ignored a run saved in `.env`. Added RUN_ID to the shared settings loader and used it in both launchers. Regression first failed because the saved selection was absent, then all 55 Java assertions passed, including shell override precedence. Documented selection and the explicit model-mode override. Saved the existing small fixture ID in the ignored local `.env`; no catalog import or new fixture was created. A separate API/preview on 8083/4185 returned real `/lab/traffic` status with one instance and zero offered requests without a shell RUN_ID. Temporary processes were stopped; the user's current preview was left for restart. Draft for human review.

## 030 — Clarify request units and fix localhost traffic control

The owner reported cycle units and a brief 403 on Start. Reproduced HTTP 403 in a local controller regression using the localhost browser Origin; the controller previously allowed only 127.0.0.1. Allowed both exact local names on the preview port while retaining foreign-origin rejection and adding a wrong-port rejection check. Changed the displayed load to target requests/s (four per cycle), showed actual cumulative dispatches, and explicitly labelled completed/failed/dropped as cycles. Regression tests also reproduced status polling erasing an action error; errors now remain until a successful action. The change retains the existing bounded scheduler rather than claiming exact request-rate pacing. No capture of the owner's original browser request was made. Draft for human review.

Verification: `make test-java` passed 56 behavior assertions, `node --test tests/*.test.mjs` passed 33 tests, and `make lint` passed. The localhost acceptance test failed with 403 before the fix; request-display and persistent-error regressions failed before their UI changes. Reviewed the diff; no authentication credentials or load limits were changed.

## 031 — Replace short cycles with sustained request load

Owner direction: demonstrate substantial request rates for at least five minutes or until stopped, with visible/demo-friendly service limits. Replaced browser load cycles with one inventory GET per arrival, raised the server cap to 10,000 requests/s, and made duration zero mean until stopped. The 10 ms scheduler accounts for elapsed-time arrivals, admits at most 256 in-flight virtual-thread requests and counts excess as unsent drops. Existing finite durations still drain. Added elapsed time and run-average sent/completed rates, authoritative target after reload, and a trusted startup run-pool setting (1–16, default 4) displayed in the dock. API thread/connection limits remain unchanged and are documented. Reads target one owned SKU; this is not broad catalog sampling or a production benchmark.

Red/green: the 10,000/s five-minute configuration failed under the old limits; the updated single-request target assertion replaces the former four-request contract. Browser-client/rate assertions failed before the request UI changes. Added invalid-pool tests and corrected a reload showing a stale default target after reproducing it. `make verify` passed; after the final target-status/UI corrections, 57 Java tooling assertions, 33 frontend tests and `make lint` passed. `make test-api-integration` passed with the updated JDBC constructor. An initial unprivileged API build failed because the sandbox denied loopback binding; the authorized build passed. Draft for human review; live sustained-run evidence follows when completed.

Live check stopped at the owner's request after 241.583 seconds, rather than waiting for five minutes. With one database connection, it offered 2,415,826 reads, dispatched 1,558,509 and completed 1,537,862 before the stop snapshot; 20,391 had failed, 857,317 were dropped and 256 remained in flight during cancellation. That is about 6,366 completed reads/s on this shared local machine, not a promised capacity figure. The sampled stock remained 1000/version 0. Temporary API and preview were stopped. Browser inspection showed the request controls, pool limit, real counters and continuing workload; it also exposed the stale target-on-reload issue fixed by the authoritative target regression. The full five-minute live check was intentionally not completed. Two-instance smoke was not repeated in this change; its target assertions now call four individual reads to retain the existing routing check.

## 032 — Pivot to a browser-only educational simulator

The owner explicitly removed the real-backend requirement and requested an independent mock simulation. They also requested a recruiter-readable app name, README and repository topics; a Medium article is future work. Renamed the UI to Distributed Systems Simulator, retaining StockFlow as the inventory scenario. Removed tracked Java/Maven services/tooling, SQL/import infrastructure, live-traffic client/tests and backend containers. Restored the existing cumulative browser model, staged component builds, fault/fix lessons and routing; added a model explanation that follows the database-to-API bottleneck shift. Replaced startup with a small static-only Node host and optional one-container Compose setup. No simulation API, database, dataset or network load remains.

Preserved the existing documentation with historical/superseded labels and added an active simulation design. Updated current instructions and the README with assumptions, code map, a two-minute walkthrough and honest portfolio scope. Initial broad documentation removal was rejected by automatic approval review; restored those documents and used the narrower preserve-and-label approach. Local PostgreSQL data and ignored legacy files were not deleted. Stopped the registered old preview before migration.

TDD: the no-backend regression failed on the live client; the static host test failed before the helper existed; the new bottleneck explanation test failed before the helper existed. The existing 30k → cache → second API behavior already passed and now has an explicit regression. `make verify` passed 32 tests plus syntax/whitespace checks. Static-host tests reject application API paths, private files and POST. Browser inspection confirmed the new branding, browser-only labels, full learning path, enabled model controls, and 12,000 then 22,000 modeled completions after adding Redis. `make stop` stopped the registered static preview. Docker runtime was not tested. GitHub description and ten relevant topics were updated successfully; repository URL is unchanged. Draft for human review.

Final short verification: the browser reached 30,000 modeled completions after the second API finished building and reported headroom. A final `make verify` passed all 32 tests; `docker compose config --quiet` accepted the static-only configuration (no Docker runtime claim). Commit 62b4c63 was pushed to `codex/browser-only-educational-simulator`; the default branch has not been merged as part of this change.

## 033 — Consolidate the simulator onto main

The owner authorized merging to main, removing redundant code, refreshing documentation and deleting unnecessary branches. Reviewed both remaining feature branches: the simulator branch contains the authenticated-backend branch, and origin/main is its ancestor. Removed the unused brand-dot style and duplicate Cursor pointer, corrected stale future-backend wording, replaced the active plan with the current simulator scope, and moved retired plans/contracts/reviews into docs/archive. Historical content remains available; the archive explains that retired source references may no longer resolve. Updated active links, dependency notices and resume instructions for main.

Verification before merge: `make verify` passed 32 tests and syntax/whitespace checks; a focused documentation check validated 17 current local links. No runtime behavior changed, so existing regression tests were sufficient. No local database data or ignored legacy caches were removed. Merge/push and ancestry-checked branch deletion follow this checkpoint; completion will be reported only after those operations succeed. Draft for human review.

## 034 — Publish the free demo and prepare the portfolio article

The owner requested a static demo, current README screenshots and a Medium article, with the lowest hosting cost/free hosting. Selected GitHub Pages for this existing public repository using the free github.io address; confirmed public-repository availability in GitHub's documentation. Added relative asset/home paths and a page-level CSP for hosted parity; a regression failed on root-absolute paths before passing. Added .nojekyll and published only the prototype subtree to the required gh-pages deployment branch. GitHub automatically enabled Pages from that branch; the subsequent create request returned already-enabled, and inspection confirmed the correct source and a successful build. No paid service, domain or custom workflow was introduced.

Live demo: https://arifmehmood16.github.io/stockflow/. Verified in-browser startup, six chapters, loaded logos, and the 12,000 → 22,000 → 30,000 modeled completion walkthrough. Captured three unedited screenshots from the public demo for README and article use. Added the repository website URL, hosting/update instructions, and a finished Medium Markdown draft explaining the walkthrough, design decisions, AI assistance and simulation boundaries. No Medium account was accessed and the article was not published there.

Verification: make verify passed 33 tests plus syntax/whitespace checks. A focused check passed 26 current documentation/image links. Pages reported built with no error. The gh-pages branch contains only public app assets; private local settings, caches and article drafts are outside that deployment. Main publication follows this entry; the hosting branch must remain. Draft for human review.

## 035 — Focus the teaching article and simulator on database scaling

The owner requested a professional teaching article with screenshots and reproducible flows, then explicitly asked for a database-led title and removal of blue-green deployment as a separate future topic. Rewrote the draft as “From One Database to a Distributed System: Bottlenecks, Caches and Shards”. Added explicit workload arithmetic, counterexamples, cache failure experiments, replica freshness and fencing considerations, shard ownership steps, model boundaries, and primary AWS/PostgreSQL references. Updated publishing notes with English/editorial guidance and author review steps. No Medium publication occurred.

Removed the deployment chapter, component, state/actions, fault, alternate routes, hover descriptions and unused styles. Kept five cumulative lessons and the existing database/cache/shard behavior. Updated current README, plan and simulation documentation; historical records remain untouched. Captured six screenshots of the updated local interface, replacing the three earlier images and adding stampede/empty-shard/ownership-transfer images.

TDD: the new deployment-exclusion regression failed because the green node existed, then passed after removal. Retired the obsolete deployment-readiness test and simplified route coverage while retaining read/write separation assertions. Final `make verify` passed all 33 tests and syntax/whitespace checks; a focused check validated 22 local documentation/image links. The first sandboxed run could not bind the static-host test; the authorized run passed. Starting another preview found port 4173 already occupied; the existing preview served the updated files and was used without terminating it.

Browser checks confirmed five chapters, the 12,000 → 22,000 → 30,000 walkthrough, stampede mitigation returning to 30,000, and a two-shard transfer producing 500,000 records per owner. Reviewed the code diff and screenshots. No dependencies, backend services, credentials or new network permissions were added. The article describes production considerations as questions/requirements, not implemented guarantees. Main merge and static demo publication follow this entry. Draft for human review.

Publication follow-up: main and the static subtree were pushed; Pages reported built. The existing browser then received the updated HTML but did not render lesson controls, while a direct public app-asset comparison matched the committed file. Added a shared revision query to CSS, the entry module and module imports to bypass stale cached assets. The revision regression failed on unversioned styles before the fix; `make verify` then passed 34 tests. Local browser startup with the revision showed lesson content and workload controls. Also verified in the browser that promotion was disabled before fencing and succeeded afterward. Documented the manual revision convention in HOSTING.md. Final hosted startup is checked after this follow-up publication.
