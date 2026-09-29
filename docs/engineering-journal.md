# Engineering journal


## 2026-09-29 — Phase 0 design and prototype

Initial state: new directory and empty GitHub repository; no inherited application code. The existing RAG repository was read for instructions and left unchanged. Working branch: `codex/phase-0-stockflow-design-and-prototype`. Repository was first created as private `systems-lab`, then renamed to `stockflow` and made public at the user's explicit request before first push.

Observed host: 24 GiB RAM, 10 logical CPUs, Node v24.14.1, GNU Make 3.81, Docker CLI 29.7.2. `java -version` reported no Java runtime. No tools or databases were installed. Host specs informed proposed budgets, not performance claims.

### TDD evidence

`node --test tests/model.test.mjs` initially failed to import the not-yet-created model. After adding a minimal module scaffold, the same command produced **4 behavioural failures / 1 pass**: no overload effect, failed-primary still writable, missing workload clamp and missing replica read-routing effect. Implemented the aggregate teaching model; the same focused command then produced **5 passes / 0 failures**.

### Commands and observed results

- `make doctor`: Node/Make/Docker CLI present; Java unavailable.
- `docker compose config --quiet`: passed; Compose configuration parses.
- `make run`: first sandboxed attempt could not bind loopback (EPERM). Approved execution started the preview at `http://127.0.0.1:4173` successfully.
- `docker info --format '{{.ServerVersion}}'`: failed because the local Docker daemon socket does not exist. Docker image build and container startup remain **unverified**. No claim of a container smoke pass.
- Existing local Prettier executable formatted prototype/scripts/tests/Compose; no dependency was added to this project.
- Final `make verify`, HTTP checks, diff checks and screenshot evidence are recorded below after execution.

### Browser observations

In-app browser: baseline at 300 offered req/s showed 120 completed / 180 rejected in the illustrative model; adding an API instance kept completion at 120. In caching chapter, matched 300 offered req/s changed modelled database demand from 300 to 84 ops/s and completed capacity from 120 to 220. These are calculations from teaching constants, not measured infrastructure performance.

Recovery: promotion initially disabled; after replica + primary failure, 72 read req/s remained and 8 write req/s were rejected at offered 80; promotion stayed disabled until fencing; after promotion model completed all 80. Inspector exposed cache role and assumptions. Motion toggle changed its accessible pressed state. Browser error/warning log inspection returned no entries at that checkpoint. Some browser selectors were refreshed after a viewport change altered accessible chapter labels; no application exception was observed.

### Review and limits

Static server exposes only five allowlisted assets, no arbitrary file traversal, GET/HEAD only, loopback host default, CSP and no external scripts. Container spec is non-root, read-only, bounded, loopback-published, and has no Docker socket. Model assertions prove internal sketch rules only. Production control plane, Java application, real infrastructure, full accessibility audit and benchmarks are not implemented.

Architecture choices are proposed in ADR 001. Public visibility and incremental commits were explicitly requested. Human approval of the AI log and production design remains pending. Licence choice remains open.

### Final verification

- `make verify`: 5 tests passed; syntax checks passed for app/model/server/docs checker/doctor; 15 local documentation links resolved before adding screenshot links.
- `git diff --check`: passed.
- HTTP smoke (`curl` against loopback): GET `/` → 200, GET `/.env` → 404, POST `/` → 405.
- Responsive DOM checks at viewport widths 375, 768 and 1440: body widths 360, 753 and 1425 respectively (scrollbar excluded), no body horizontal overflow. Graph/lesson rail intentionally scroll within their own region on small screens. Browser viewport override was reset afterward.
- Captured normal-browser preview in `docs/design/screenshots/preview.png`. Oversized full-page captures from viewport emulation had rendering artefacts and were discarded; they are not evidence of pixel-perfect mobile validation.
- Keyboard check found Enter on a guide action moved focus to BODY after markup replacement. Preserved action focus during re-render; focused browser regression result recorded at the next checkpoint.
- Review corrected WAL edge activation so a failed primary does not continue illustrating replication. Replica read routing can remain available.
- Secret-pattern search returned only explanatory documentation terms; no credential-like literal was found. This is a targeted review, not a full secret-scanner certification.
- Focus regression after fix: Enter on **Send 300 req/s** retained `BUTTON` / `data-action=burst` instead of BODY. Final `make verify`: 5 passes, all syntax checks passed, 16 local links resolved; `git diff --check` passed.

## 2026-09-29 — Phase 0.4a occupied preview port

Cause: the preview left running by the agent conflicted with the user’s `make run`. Stopped that known execution session (exit 130 after Ctrl-C); did not search for and kill arbitrary port owners.

Regression: `node --test tests/serve.test.mjs` first failed because stderr contained the unhandled EADDRINUSE stack rather than recovery guidance. Added narrow server error handling: report occupied port, mention existing-preview URL conditionally, offer a different PORT, exit 1. Existing listener survives and remains responsive. Focused rerun passed (1/1). `make verify` passed (6 tests, syntax, 16 links); `git diff --check` passed. Default tests now include one local ephemeral loopback listener but still use no public network or paid services.

Reviewed the change: no process-killing behaviour, no widened binding, no new dependency; unrelated errors remain visible. README documents the alternatives. RESUME and PLAN record the maintenance checkpoint. Java implementation remains behind the design-review gate.

## 2026-09-29 — Phase 0.4b workbench revision

User-assigned scope: retain learning path, move operations onto architecture cards, animate building/readiness/rerouting, use technology logos, dark background with light contrast, and add make stop. No Java implementation phase started.

Red: `node --test tests/model.test.mjs` produced two new failures for missing build state and green switching before readiness. `node --test tests/process.test.mjs` produced a behavioural stop failure plus missing-registry scaffold error. Green: focused suites passed after implementation. `make verify` passed 11 tests, syntax checks, and 18 local doc links. `docker compose config --quiet` and `git diff --check` passed.

Started isolated `PORT=4175 make run` for verification, leaving any user listener on 4173 untouched. Browser observed local logo loading, on-map controls, readiness gating, old-route muting/new-route activation, and immediate reset of an in-flight build. Logs and visual state explicitly call this lifecycle illustrative. Final native run/stop smoke and screenshots follow below.

Reviewed safety: local registry checks start/command identity before signalling, no port-owner killing, no force kill, no HTTP stop endpoint. SVG assets are local and allowlisted with retained attribution. No licence selected for StockFlow's own code. Node UI model remains a teaching sketch.

### Final revision verification

- `make verify`: 11 tests passed, syntax checks passed, 18 local links resolved.
- `docker compose config --quiet`: passed. Docker daemon/build/run remain unverified as previously recorded.
- `git diff --check`: passed.
- Browser: Redis card showed PROVISIONING while DB demand stayed at baseline; after READY its route became enabled and demand decreased. Guide contained zero action buttons. Green route switch was disabled during provisioning, enabled on readiness, then selected green and muted blue. Reset cancelled the in-flight cache build and it remained unbuilt after timers would have completed. Keyboard inspection and reduced-motion control worked; console error/warning log was empty.
- At viewport 375, body width was 360 with the scrollbar excluded; graph scroll height and client height both 441 after adjusting scrollbar space. No body horizontal overflow or clipped vertical graph scroll. Viewport override reset afterward.
- Dark screenshot updated in `docs/design/screenshots/preview.png`; removed an unused graph path during final review.
- Native smoke: `PORT=4175 make run` started the registered isolated preview; `PORT=4175 make stop` reported it stopped, and the run process exited 0. Repeating stop reported no registered preview and exited 0. No user process on port 4173 was touched.
- Commit 543c683 pushed successfully; remote includes the prior port-conflict fix. Final documentation checkpoint follows this record.

## 2026-09-29 — Cumulative scale and Java catalog bootstrap

User-directed PLAN 0.4c/0.4d revision. Model tests first showed five behavioral failures for the new rate limit, persistent lesson selection, empty new shards, migration state and cache faults. Fourteen frontend tests now pass. The UI keeps one topology, fits its viewport, exposes six shard owners and explicit pause/copy/verify/switch, and preserves installed cache/system mitigations.

The owner rejected an initially introduced Python importer and specified Java for all operational scripts/backend. That uncommitted Python implementation was removed. Java replaced the Node preview/stop/doctor/doc checker too; only frontend JavaScript/tests still use Node. Downloaded a project-local Temurin 25.0.4.1 JDK from the official vendor and verified SHA-256 `61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d`. Jars are checksum-pinned in Build.java. No global JDK installation was performed.

Java red/green: initial URL config assertion failed against the stub, then passed. JDBC integration initially reproduced the quoted CSV failure (SQLSTATE 22P04); forced quoting/escaping passed the same fixture. Repeat-import detection failed against its stub, then passed. Safe-stop, stale identity, occupied-port and HTTP allowlist checks migrated to Java; the occupied-port fixture was corrected to bind the same IPv4 address on macOS. Current Java suite passes 24 assertions.

Observed dataset evidence:

- Official compressed export: 1,275,171,186 bytes; pinned S3 version in Dataset.SOURCE.
- First import: 1,000,000 catalog and inventory rows successfully committed in the user's existing `postgres` database, under `stockflow` only.
- The owner clarified DATASET_ROWS=10,000,000 is an upper bound, not synthetic expansion. Clean source EOF selected **4,532,480 valid unique products**.
- Full Java COPY found unquoted embedded quotes at projected row 35,623; the failed transaction rolled back. A synthetic row reproduced the same format family. The cached projection was normalized without a second source download.
- Corrected full subset: SHA-256 `f095ea1db1dbce9976d00763e040ea4e85d7f6e3e226d43d84295b2e918a48e7`, 466 MiB reported by du; metadata records source_complete=true.
- Full Java import committed **4,532,480 catalog rows and 4,532,480 inventory rows**. Sixteen actual buckets range from 282,542 to 284,368 rows. Existing quantities were preserved.
- Subsequent `PORT=4175 make run` printed **“Matching catalog already loaded; skipping download, COPY and inventory writes.”** It then started the Java preview successfully. `PORT=4175 make stop` successfully stopped the previous owned Java preview before this check.

Commands observed: `make setup`, `DATASET_ROWS=10 make data-fetch` (fresh Java HTTPS projection), `make test-java`, `make test-integration`, `make verify`, `docker compose config --quiet`, `git diff --check`. At this checkpoint verify passed 24 Java assertions, 14 frontend tests, syntax checks and 23 local links. Additional final checks follow below. Docker config parsed; `docker info` failed because the daemon socket was absent, so container build/run remains unverified. No Java inventory API, actual load generator, Redis, multi-node shard, or benchmark is claimed.

Browser on Java server: 30,000 offered modeled requests retained across chapter change/build; stampede raised modeled DB demand to 105,600 and mitigation reduced it to 8,400; stale scenario showed 4,320 stale reads/s and version guard reduced it to zero. Two protections remained after chapter change. Shard counts remained 1,000,000/0 until ownership switch, then became 500,000/500,000. These are illustrative model observations, not PostgreSQL performance measurements.

### Final validation

`make verify` passed 24 Java assertions, 14 frontend model tests and 24 local documentation links. The extended JDBC integration passed embedded quotes/empty fields, repeat skip, complete-source exhaustion below a requested maximum, detection/repair of missing inventory, quantity preservation and rollback; it removed its owned test database. `docker compose config --quiet` and `git diff --check` passed.

Java-served browser checks observed a third shard remain empty until migration, then ownership counts of 375,000 / 312,500 / 312,500; three installed protections persisted. Naive-modulo routing mitigation worked through the on-map console. No browser error/warning logs were recorded. At 1280×720 the graph scroll/client dimensions both measured 796×274; at 375×812 both measured 346×284 with no page horizontal overflow. Viewport override is reset for the final screenshot. Mobile labels are small because the entire topology remains visible; inspector/focus mode provide detail. A full accessibility audit remains future work.

## 2026-09-29 — Phase 1 read-only Java service foundation

Owner requested continuation after the status report. Started `codex/phase-1-java-inventory-baseline`, retained local PostgreSQL/import behavior and introduced only the previously planned Spring MVC/JDBC service.

Observed red: known-code lookup returned empty and invalid codes were accepted; four HTTP checks returned 404 before endpoint implementation; API occupied-port CLI test lacked its error; artifact-isolation test showed replacement of the build output changed the running artifact. Implemented the corresponding behaviors. Intermediate Spring @Repository proxying of a final adapter failed startup; @Component correctly uses the adapter's explicit SQL exception translation.

Observed checks so far:

- Java-launched Maven service tests: 10 pass (3 use case, 6 HTTP, 1 architecture).
- `make test-api-integration`: passed real PostgreSQL stock reads, leading zeroes, parameter binding, missing row, schema incompatibility and unchanged stock/version. Owned temporary database removed.
- `make verify`: passed 28 Java tool assertions, 10 service tests, 14 frontend model tests and 26 documentation links.
- `API_PORT=8085 PORT=4176 make run`: skipped the verified 4,532,480-row import and started UI/API on isolated ports.
- Initial packaged `make api-smoke` timed out with a runtime class-loading error; archive integrity check passed. Stopped only the isolated processes, restarted the same artifact, and observed stock/health smoke success. Repackaging identical application output while running also passed. Original cause was not established. Added a per-launch artifact copy with a failing/passing regression to prevent builds from modifying a running artifact; final clean-rebuild/lifecycle checks follow.
- `docker compose config --quiet`: passed before the later documentation changes. `docker info` confirmed daemon socket absent; no runtime/build claim.

The UI remains illustrative and unchanged in this checkpoint. The public/synthetic catalog endpoint is read-only; tenant authorization and reservation writes remain planned. Final diff, formatting, lifecycle and publication evidence follow below.

### Service artifact checkpoint

Formatted the Java changes with the existing local Google Java Format tool. The new launch snapshot passed `API_PORT=8085 make api-smoke` both before and after a full Java-launched Maven `clean package` while the API stayed running. Clean package passed all 10 service tests. This validates real stock access and rebuild isolation; it does not establish the original transient class-loading failure's cause. Code/diff review checked local-only binding, fixed queries, credential handling, response safety and separation from modeled UI metrics. Checkpoint commit follows; final stop/integration repeat covers the final source state.

### Final Phase 1 read-only checkpoint

- Implementation commit `e13cd91` pushed to `codex/phase-1-java-inventory-baseline`.
- Final `make verify`: 28 Java tool assertions, 10 service/architecture tests, 14 model tests, syntax and 26 local links passed (additional handoff links added afterward).
- Final `make test-api-integration`: passed and removed its owned temporary database.
- `docker compose config --quiet` and `git diff --check`: passed.
- `API_PORT=8085 PORT=4176 make stop`: stopped owned UI and its child API. Repeated stop was harmless. The foreground make run terminal ended with signal status 143, expected when its JVM receives the requested SIGTERM. No user process on 4173/8081 or PostgreSQL was stopped.
- No frontend source/layout changes in this service checkpoint. Database stock reads and real process lifecycle were tested; Docker execution, reservation writes, tenant credentials, real load and measured animation are still pending.

Follow-up documentation/CLI wording commit records this checkpoint. Future work resumes from PLAN 1.1/1.2, not from a claim that the whole Phase 1 gate has passed.

## 2026-09-29 — StockFlow favicon

User-requested PLAN 0.4e: added a mint/navy SF vector monogram, its HTML icon link and an explicit Java static route. `PORT=4177 make preview` served the asset with HTTP 200 and image/svg+xml; fetched HTML contained the icon link. `xmllint --noout prototype/favicon.svg`, `make test-java` (28 assertions), and `git diff --check` passed. No dependencies or CSP expansion. Existing Docker COPY includes the asset; container execution and browser-tab appearance were not tested for this change.

## 2026-09-29 — Cursor implementation and Codex checkpoint plan

Documentation-only owner request: Cursor implements one card per request, Codex validates named milestones. Added 43 task cards A01–I04, dependency order, ten stop gates V0–V9, state ledger, implementation/review prompts and review template. All implementation task statuses remain todo; all checkpoint verdicts remain not_requested. A01 is a contract-only decision task before any writable schema/service work.

Planning was grounded in current code/contracts, including the read-only HTTP filter/pool and numeric imported schema. Documented the existing/future command distinction, inherited constraints, small-context reading protocol and code-commit-based review evidence. Runtime code, database and process state were not changed. Link/structure/diff checks and publication status are recorded after validation below; no runtime suite is claimed for this documentation-only change.

## 2026-09-29 — A01 writable inventory contract

Documentation-only decision. No migration, credential, or runtime change. ADR 002 keeps the numeric diagnostic catalog immutable and puts UUID tenant/warehouse stock in a run schema. The worked example uses tenants North and South, runs R1 and R2, shared SKU `00000000000101`, and a lost-response retry of key `retry-1`.

Static inspection of the ADR SQL sketches: two fenced SQL blocks, parenthesis balance 0, required tables and primary keys present. The example schema `sf_run_aaaaaaaaaaaa4aaa8aaaaaaaaaaaaaa1` is 32 hex characters and matches run UUID `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1` with hyphens removed. HTTP examples for the replayed 201 and the stored 409 were read in place and not sent to a server.

Commands:

- `java` on `PATH` failed: no system Java runtime.
- `.lab/jdk/Contents/Home/bin/java tools/Build.java check-docs` passed: `Checked 200 local documentation links.`
- `git diff --check` passed.
- Migrations were not applied. `make verify`, Docker, and database checks were not run for this contract-only card.

### Handoff validation

The Java documentation checker passed **195 local links**. An ignored, one-off Java structure check verified **43 indexed cards**, every required section, sequential predecessor references, **ten matching review gates**, and all future task/gate statuses still unstarted. `git diff --check` passed; inspection confirmed this handoff changes documentation only. No runtime tests were rerun because no runtime code changed. The handoff was saved as `46bcbe6`; this follow-up records the observed validation without rewriting that commit. Start Cursor at A01 and request V0 before writable implementation.

## 2026-09-29 — 0.4f layout, cursor hover and runtime additions

Owner-directed prototype repair, independent of Cursor A01/V0. Browser measurements reproduced overflowing content in all nine card headers and in the workload action row. Reserved header/action grid rows, corrected shard spacing, separated topology rows and adjusted green routes to use gutters around other cards.

The owner clarified that surrounding content may overlap and hover should stay near the cursor. Replaced the initial all-control avoidance approach with cursor-relative positioning, viewport flipping and clearance around only the inspected card. Overlays ignore pointer input. Hover and Inspector share dynamic build/fault/mitigation summaries. New shard buttons preserve identity across renders, animate only on arrival/ownership change and expose per-shard records/buckets/epoch. Build effects stay inside fixed slots and respect Motion off.

Red evidence: the browser baseline overflow checks failed for all nine headers. New tests initially lacked the helper module; behavioral assertions then caught incorrect workload/failed-primary states and generic Ready instead of Empty shard. Green evidence: `make verify` passed 28 Java tooling assertions, the API suite, 20 model/hover tests, syntax checks and 202 local documentation links. Existing Maven/JDK warnings remain. The final disconnected-tooltip guard received a syntax/focused test check afterward.

Browser checks covered desktop, Focus canvas, 375×812, pointer movement, keyboard focus/Escape, automatic Provisioning-to-Ready updates, stale-cache fault/mitigation, six-shard additions and migration controls, per-shard Inspector updates, reduced motion, cancel and reset. Final narrow S6 hover fit the viewport without covering its cluster card. No measured database traffic or Docker execution is claimed. Reproduction steps and observed results: [UI interaction verification](ui-interaction-verification.md).

Diff review: no new dependency, credential, database operation or external-input boundary; the Java server only adds the exact `/hover.mjs` static route. Existing contract commits and V0 state are preserved. Restart an older Java preview before refreshing, so it serves the new module. Phone maps still shrink labels; use Inspector for readable detail.

## 2026-09-29 — Independent V0 review

Reviewed A01 documentation at `7772cbe1e9c8a339238e102fa46c08bc7b41e5f9` against base `9eb2e03` and the V0 packet. Confirmed the range changes documentation only; compared the imported schema and actual read-only service configuration with the proposed run boundary. The separately saved UI repair `ff62408` is outside this review.

Verdict: changes_requested. R1: uniqueness-error replay cannot continue in an aborted PostgreSQL transaction. R2: the required reservation session token has no authorized issuer/interface. R3: the retention worker has no DELETE authority, and the schema lacks a terminal-retention timestamp. Smaller-than-100 catalog behavior is a non-blocking clarification. PostgreSQL 17 transaction and INSERT documentation independently support R1; sources are linked in the packet.

Checks: Git diff scope, line-by-line ADR/contract/schema review, Java documentation check (203 local links before adding review links), and whitespace check passed. No migration or database write was run. Runtime tests from the UI task are not evidence that this proposed writable contract works. Updated the V0 packet and STATE; A02 remains blocked pending corrections and a requested recheck.

## 2026-09-29 — V0 corrections R1–R3

Documentation-only. No migration, credential, or runtime change. The non-blocking smaller-catalog note was left unchanged.

- R1: a duplicate idempotency key uses `INSERT ... ON CONFLICT DO NOTHING` and a `SELECT` in that same open transaction. A unique-violation error is specified as unusable because it aborts the transaction before replay.
- R2: `FixtureCredentialIssuer` remains the tenant CLI issuer. `SessionTokenIssuer` in the inventory service mints the reserve `sessionToken` once, with `minVersion` and `exp` taken from the committed response and the idempotency `expires_at`. Replay returns the stored token.
- R3: `reservation.terminal_at` is the retention clock for terminal rows. Role `sf_c_<32 hex>` may delete only expired idempotency rows and reservations past that clock. The request writer still has no `DELETE`.

Static SQL inspection of the ADR sketches: parenthesis balance 0. Migrations were not applied. `.lab/jdk/Contents/Home/bin/java tools/Build.java check-docs` passed: `Checked 204 local documentation links.` `git diff --check` passed.

## 2026-09-29 — 0.4g readable routes and navigable canvas

Owner requested correcting the apparent write-to-replica arrow, more space and pan/zoom. The old WRITE label sat next to the replica-read segment, and route highlighting replaced semantic colors. Centralized source/destination/kind/label/path definitions, separated primary writes, replica reads and WAL, preserved type colors/arrowheads, and restored primary reads when a replica is unavailable. Widened gutters; route segments are tested against unrelated card interiors.

Added pointer drag, pointer-centered wheel zoom, bounded zoom buttons, keyboard navigation, offscreen focus reveal and Fit system. Model renders do not reset the camera. The new owner instruction supersedes the fit-only restriction, so README, UX and Cursor frontend acceptance text were updated without changing task/gate status.

Red/green: new module initially absent; route-clearance regression then found a replica-read line crossing the shard card, corrected to the right gutter. Browser offscreen-focus check failed before removing an unreliable focus-visible gate and passed afterward. Final make verify passed 28 tooling assertions, the API suite and 26 frontend tests. Browser evidence includes blue/green routing, replica-crash fallback, hover, mouse drag/wheel zoom, build camera preservation, keyboard and narrow Fit. See docs/ui-interaction-verification.md for concrete measurements. Final syntax, focused tests, links and whitespace checks were repeated after cleanup.

No new dependency or external-input boundary. Exact static module route added to the Java allowlist. No database import, infrastructure fault or Docker run. Cursor's V0 corrections are preserved and not reviewed by this UI task. An existing Java preview must restart to serve the new module.

## 2026-09-29 — Canvas load and fault docks

Owner requested moving the fault bar and load/capacity metrics into the animation to free vertical space. Removed their full-width rows. The canvas now has an expanded compact vertical load/metrics dock and a collapsible vertical fault drawer. Fault buttons on components open the drawer with that component selected. Load changes use 10,000 req/s intervals and remain bounded from 10,000 to 250,000. Fit reserves dock edges on wider screens; panning and zooming ignore input on the docks. The prototype remains illustrative.

Red/green: a model test first failed on the prior 8,000 req/s baseline; it passed after quantizing load. A topology test first failed because Fit ignored the dock inset; it passed after inset-aware fitting. `make verify` passed 28 Java tooling assertions, Maven API tests, 28 frontend tests, JavaScript syntax and 207 local documentation links. Browser checks at 1280×720 and 375×812 observed contained docks, no horizontal page overflow, 30,000 req/s model rates and component-targeted drawer focus. Details are in [UI interaction verification](ui-interaction-verification.md). No real load, database change, or Docker check was made for this visual task.

## 2026-09-29 — V0 focused recheck

Reviewed correction head `44008c0` against the previous V0 findings, ADR 002, contracts, architecture and threat model. R1 and R2 are resolved. R3 now has a terminal retention clock and table/run-scoped cleanup role, but the role has unrestricted row deletion within its two history tables. The statement that the role can delete only expired or old terminal rows therefore exceeds the documented PostgreSQL grant. V0 remains changes_requested for this one distinction: define database row enforcement or describe the retention predicate as a trusted-worker policy consistently. The smaller-catalog note remains non-blocking.

Independent checks: `.lab/jdk/Contents/Home/bin/java tools/Build.java check-docs` passed with 203 links before the packet edit and 206 afterward; `git diff --check 426d4b2 44008c0` and `git diff --check 44008c0 HEAD` passed. Consulted official PostgreSQL 17 transaction-isolation, privilege and row-security documentation; links are in the V0 packet. No migration, database write, runtime tests or Docker run were part of this docs-only review. A02 was not started.

## 2026-09-29 — V0 finding R3a

Documentation-only, on branch `codex/a01-v0-retention-authority` from `main`. The cleanup role's `DELETE` grant is now described as table-wide on the two history tables. The 24-hour clocks stay in the worker's `DELETE` statements. No row-security policy is claimed, so an early delete is not described as a privilege failure. ADR 002, contracts, the threat model and the architecture summary use that same split. A02 was not started. `.lab/jdk/Contents/Home/bin/java tools/Build.java check-docs` passed: `Checked 208 local documentation links.` `git diff --check` passed. Migrations were not applied.
