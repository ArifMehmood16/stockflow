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
