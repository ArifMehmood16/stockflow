import {
  initialState,
  transition,
  metrics,
  shardRecords,
  faultCatalog,
  componentAvailable,
  loadSteps,
} from "./model.mjs";
import { placeHover, componentSnapshot } from "./hover.mjs";
import { nodes, scene, routes, fitCamera, zoomCamera, panCamera } from "./topology.mjs";
let state = initialState();
let traffic;
let realMode = false;
let realStatus = null;
let realRate = 2;
let realError = "";
let chapter = 0;
let faultNode = "cache";
let faultKind = "stampede";
let running = false;
let reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
let pinned = null;
let pinnedShard = null;
let events = [];
let buildTimers = [];
let routeTimer;
let hoverNode = null;
let hoverTimer;
let hoverPoint = null;
let hoverFrame;
let buildMessage = "";
let routeNotice =
  "Build directly on the map. Select a component to inspect it.";
const $ = (id) => document.getElementById(id);
const fmt = (n) => Math.round(n).toLocaleString("en-GB");
for (const [id, box] of Object.entries(nodes)) {
  const shell = $(`component-${id}`);
  shell.style.left = `${box.x}px`;
  shell.style.top = `${box.y}px`;
  shell.style.width = `${box.width}px`;
  shell.style.height = `${box.height}px`;
}
for (const element of [$("graph"), document.querySelector(".edges")]) {
  element.style.width = `${scene.width}px`;
  element.style.height = `${scene.height}px`;
}
const svgNS = "http://www.w3.org/2000/svg";
for (const [kind, color] of Object.entries({ read: "#58dbb1", write: "#a6b8ff", replication: "#eac57f" })) {
  const marker = document.createElementNS(svgNS, "marker");
  marker.id = `arrow-${kind}`;
  for (const [name, value] of Object.entries({ viewBox: "0 0 10 10", refX: "9", refY: "5", markerWidth: "5", markerHeight: "5", orient: "auto" })) marker.setAttribute(name, value);
  const head = document.createElementNS(svgNS, "path");
  head.setAttribute("d", "M0 0 L10 5 L0 10 Z");
  head.setAttribute("fill", color);
  marker.append(head);
  document.querySelector(".edges defs").append(marker);
}
function renderRoutes() {
  for (const route of routes(state)) {
    let path = $(route.id);
    if (!path) {
      path = document.createElementNS(svgNS, "path");
      path.id = route.id;
      const label = document.createElementNS(svgNS, "text");
      label.id = `${route.id}-label`;
      $("route-layer").append(path, label);
    }
    path.setAttribute("d", route.path);
    path.setAttribute("class", `edge ${route.kind} optional${route.active ? " enabled" : ""}`);
    path.setAttribute("marker-end", `url(#arrow-${route.kind})`);
    path.dataset.from = route.from;
    path.dataset.to = route.to;
    path.dataset.kind = route.kind;
    const label = $(`${route.id}-label`);
    label.textContent = route.label.text;
    label.setAttribute("x", route.label.x);
    label.setAttribute("y", route.label.y);
    label.setAttribute("class", `route-label ${route.kind}`);
    label.style.opacity = route.active ? 1 : 0;
  }
}
const chapters = [
  {
    name: "Single database",
    sub: "Find the first bottleneck",
    kicker: "THE BASELINE",
    title: "Every system has a limit.",
    subtitle: "Follow a stock request. Then turn up the pressure.",
    heading: "Find the bottleneck.",
    intro:
      "One service. One database. A perfectly reasonable place to start. What happens when demand grows?",
    steps: [
      [
        "Start the flow",
        "Requests travel from the workload through the API to PostgreSQL.",
        "Start traffic",
        "run",
      ],
      [
        "Turn up the pressure",
        "Raise offered load to 30,000 req/s. Watch demand exceed the database’s illustrative capacity.",
        "Send 30,000 req/s",
        "burst",
      ],
      [
        "Scale the service",
        "Add an API instance. The shared database is still the bottleneck.",
        "Add instance",
        "scale",
      ],
    ],
    cost: "More application instances also open more database connections. Scaling the wrong layer adds cost without removing the limit.",
    question:
      "Why did adding a service instance leave the database bottleneck unchanged?",
  },
  {
    name: "Caching",
    sub: "Faster reads, new problems",
    kicker: "THE READ PATH",
    title: "Don’t ask twice.",
    subtitle: "Let frequent reads take a shorter route. Keep writes correct.",
    heading: "Give reads a shortcut.",
    intro:
      "Inventory pages are read much more often than stock changes. A warm cache can take repeated reads off the database.",
    steps: [
      [
        "Create a busy baseline",
        "Use the same 30,000 req/s workload before and after the change.",
        "Send 30,000 req/s",
        "burst",
      ],
      [
        "Enable cache-aside",
        "Redis serves an assumed 80% of eligible reads in this preview. Writes still reach PostgreSQL.",
        "Enable Redis",
        "cache",
      ],
      [
        "Inspect the trade-off",
        "Select Redis to see its role, model assumptions and consistency limits.",
        "Inspect Redis",
        "inspect-cache",
      ],
    ],
    cost: "A cached stock count can be stale. Reservations must validate stock atomically in PostgreSQL. TTL alone does not solve every invalidation race.",
    question:
      "When is a stale stock display acceptable—and when is it dangerous?",
  },
  {
    name: "Read replicas",
    sub: "Separate reads from writes",
    kicker: "REPLICATION",
    title: "One writer. More readers.",
    subtitle:
      "Move eligible reads away from the primary. Watch the consistency cost.",
    heading: "Make room for writes.",
    intro:
      "A read replica follows the primary’s write-ahead log. Eventual reads can use it; reservations stay with the writer.",
    steps: [
      [
        "Apply a read-heavy load",
        "This preview uses 90% reads and 10% writes.",
        "Send 30,000 req/s",
        "burst",
      ],
      [
        "Provision a replica",
        "Eligible reads move to the standby. A single replica also has finite capacity.",
        "Add replica",
        "replica",
      ],
      [
        "Follow the read route",
        "Inspect the replica. Real replay delay and session consistency are planned experiments.",
        "Inspect replica",
        "inspect-replica",
      ],
    ],
    cost: "Replication is not instant. A just-written value may be absent on a replica. Extra readers do not add primary write capacity.",
    question: "Where should the first read after a successful reservation go?",
  },
  {
    name: "Failure recovery",
    sub: "Fence, promote, verify",
    kicker: "FAILURE & RECOVERY",
    title: "Failure is part of the design.",
    subtitle: "Make a dependency disappear. Recover deliberately.",
    heading: "Recover the writer.",
    intro:
      "A replica is useful only if the recovery process is safe. This model requires fencing before promotion.",
    steps: [
      [
        "Prepare the standby",
        "Provision a read replica before taking the primary down.",
        "Add replica",
        "replica",
      ],
      [
        "Fail the primary",
        "Writes become unavailable. Replica reads can still be served.",
        "Fail primary",
        "fail",
      ],
      [
        "Fence the old writer",
        "In the real lab, this must verify that the old owned process cannot accept writes.",
        "Fence primary",
        "fence",
      ],
      [
        "Promote the replica",
        "This is illustrative promotion. Real mode must inspect lag, acknowledge possible loss and verify recovered data.",
        "Promote replica",
        "promote",
      ],
    ],
    cost: "Asynchronous replication can lose acknowledged writes during failover. A healthy process does not prove that every committed reservation survived.",
    question: "How do you prove the old primary cannot accept writes?",
  },
  {
    name: "Sharding",
    sub: "Distribute data ownership",
    kicker: "DATA DISTRIBUTION",
    title: "Give data a second home.",
    subtitle:
      "Spread tenants across independent databases. Keep ownership explicit.",
    heading: "Split the working set.",
    intro:
      "Add empty PostgreSQL nodes to this same system. Move buckets explicitly; watch row counts and routes change only at cutover.",
    steps: [
      [
        "Increase demand",
        "Expose the capacity limit at 30,000 offered req/s.",
        "Send 30,000 req/s",
        "burst",
      ],
      [
        "Provision the next shard",
        "Add up to six nodes. A ready empty node has no data or traffic. Then use Pause writes → Copy → Verify → Switch owners on the cluster.",
        "Add shard",
        "shard",
      ],
      [
        "Move ownership safely",
        "Complete the migration on the shard cluster. Watch counts sum to one million, then inject naive modulo routing or a hot tenant.",
        "Switch owners",
        "switch-ownership",
      ],
    ],
    cost: "A hot tenant still belongs to one shard. Cross-shard queries and migrations get harder. Partitions in one database are not independent shards.",
    question:
      "Does adding a shard help if one SKU receives almost all requests?",
  },
  {
    name: "Blue / green",
    sub: "Change versions safely",
    kicker: "DEPLOYMENT",
    title: "Change the engine in motion.",
    subtitle:
      "Keep a known-good version available while introducing the next one.",
    heading: "Switch with a way back.",
    intro:
      "Blue and green share a compatible schema. The final lab will check readiness, switch routes and drain old requests.",
    steps: [
      [
        "Keep requests flowing",
        "Use traffic to make the active route visible.",
        "Start traffic",
        "run",
      ],
      [
        "Switch to green",
        "Preview the route/version change. Build green on the map, watch its readiness checks, then switch traffic at the router.",
        "Build green, then switch",
        "deploy",
      ],
      [
        "Inspect the active route",
        "Check which version receives traffic. Use the switch again to preview rollback.",
        "Inspect router",
        "inspect-proxy",
      ],
    ],
    cost: "Blue/green temporarily doubles application capacity and connections. Destructive schema changes can remove rollback safety.",
    question:
      "Can version 1 still run after version 2 changes the database schema?",
  },
];
const descriptions = {
  client: [
    "Workload generator",
    "Traffic source",
    "Schedules synthetic stock reads and reservation writes.",
    "90% reads / 10% writes; selected load is offered demand.",
    "The preview generates no HTTP load. The real runner will enforce rate, duration and concurrency caps.",
  ],
  proxy: [
    "Routing proxy",
    "Request routing",
    "Sends requests to the selected inventory version and instances.",
    "Build green first; the router enables switching only after model readiness.",
    "Real rollout requires readiness, bounded draining and compatible database changes.",
  ],
  api: [
    "Inventory API",
    "Java business service",
    "Reads stock and reserves/releases inventory through atomic database operations.",
    "Model capacity: 22,000 req/s per instance. This is an arbitrary teaching constant.",
    "Virtual threads and more instances do not create more database connections or CPU capacity.",
  ],
  cache: [
    "Redis cache",
    "Disposable read acceleration",
    "Serves repeated eventual stock reads; misses continue to storage.",
    "Warm hits assume 80%. Inject expiry, stale-fill, missing-key, hot-key and crash scenarios on this component.",
    "Reservations validate authoritative stock. Fixes persist across experiments; restoring service does not remove installed protections.",
  ],
  db: [
    "Primary database",
    "Authoritative write store",
    "Owns stock, reservations and persistent idempotency records.",
    "Model capacity: 12,000 operations/s per balanced shard, not a PostgreSQL benchmark.",
    "Atomic conditional updates prevent overselling. Async failover can lose acknowledged writes; recovery needs a write ledger.",
  ],
  replica: [
    "Read replica",
    "Asynchronous copy",
    "Serves eligible eventual reads while following primary WAL.",
    "Model capacity: 12,000 reads/s. Lag scenarios mark stale reads; pinning moves that demand back to primary.",
    "Session-consistent reads should use primary or a verified caught-up replica. A replica is not a backup.",
  ],
  shard: [
    "Shard cluster",
    "Independent data owner",
    "Owns a subset of tenant buckets on a separate database primary.",
    "16 virtual buckets hold 1,000,000 logical records. An added shard stays empty until ownership switches.",
    "Real migration must pause writes, drain, copy, verify and switch ownership epoch. A hot tenant remains hot.",
  ],
};
descriptions.api2 = [
  "Extra API instance",
  "Horizontal service capacity",
  "Receives part of the traffic from the router after readiness checks complete.",
  "Adds 22,000 illustrative req/s of API capacity. Database capacity stays unchanged.",
  "Each real instance adds connections and memory. A database bottleneck remains a database bottleneck.",
];
descriptions.green = [
  "Green deployment",
  "Prepared service version",
  "Version 2 starts separately. It receives traffic only after an explicit switch at the router.",
  "Readiness is an illustrative timed state, not a running Spring process.",
  "Both versions must be compatible with the same schema. Rollback does not undo database changes.",
];
function log(text) {
  events.unshift({ at: new Date().toLocaleTimeString("en-GB"), text });
  events = events.slice(0, 6);
  $("event-log").replaceChildren(
    ...events.map((e) => {
      const li = document.createElement("li");
      const t = document.createElement("span");
      t.textContent = e.at;
      const b = document.createElement("b");
      b.textContent = e.text;
      li.append(t, b);
      return li;
    }),
  );
  $("announcement").textContent = text;
}
function done(action) {
  if (action === "switch-ownership") return state.epoch > 1;
  return action === "run"
    ? running
    : action === "burst"
      ? state.rps >= 30000
      : action === "cache"
        ? state.cache
        : action === "replica"
          ? state.replica || state.promoted
          : action === "fail"
            ? state.primaryDown || state.promoted
            : action === "fence"
              ? state.fenced
              : action === "promote"
                ? state.promoted
                : action === "scale"
                  ? state.instances > 1
                  : action === "shard"
                    ? state.shards > 1
                    : action === "deploy"
                      ? state.green
                      : action.startsWith("inspect-")
                        ? pinned === action.slice(8)
                        : false;
}
function selectPanel(inspect) {
  $("guide-content").hidden = inspect;
  $("inspect-content").hidden = !inspect;
  $("guide-tab").setAttribute("aria-selected", String(!inspect));
  $("inspect-tab").setAttribute("aria-selected", String(inspect));
  $("guide-tab").tabIndex = inspect ? -1 : 0;
  $("inspect-tab").tabIndex = inspect ? 0 : -1;
}
function inspect(id, shardIndex = null) {
  pinned = id;
  pinnedShard = shardIndex;
  selectPanel(true);
  renderInspector();
  document
    .querySelectorAll(".node")
    .forEach((n) => n.classList.toggle("pinned", n.dataset.node === id));
  renderGuide();
}
function renderInspector() {
  const id = pinned || "api";
  const d = descriptions[id];
  const snapshot = componentSnapshot(state, id, running, pinnedShard);
  $("inspect-content").innerHTML =
    `<span class="guide-kicker">COMPONENT INSPECTOR</span><h3>${d[0]}</h3><span class="inspect-role">${d[1]}</span><dl class="inspect-list"><div><dt>Current state</dt><dd>${snapshot.status}</dd></div><div><dt>Current model behavior</dt><dd>${snapshot.detail}</dd></div><div><dt>What it does</dt><dd>${d[2]}</dd></div><div><dt>Model assumption</dt><dd>${d[3]}</dd></div></dl><div class="tradeoff"><span class="tiny-label">WHAT TO REMEMBER</span><p>${d[4]}</p></div><p class="inspect-hint">All values are illustrative. Select another architecture component to inspect its role.</p><button class="secondary" id="back-guide">← Back to walkthrough</button>`;
  $("back-guide").onclick = () => selectPanel(false);
  if (id === "shard" && pinnedShard !== null && pinnedShard < state.shards)
    $("inspect-content").querySelector("h3").textContent = `Shard S${pinnedShard + 1}`;
}
const actionLocations = {
  run: "Workload → Start traffic",
  burst: "Workload → Send 30,000 req/s",
  scale: "Extra API instance → Build instance",
  cache: "Read cache → Build Redis",
  replica: "Read replica → Build replica",
  fail: "Primary database → Fail primary",
  fence: "Primary database → Fence primary",
  promote: "Read replica → Promote replica",
  shard: "Shard cluster → Add shard",
  "switch-ownership":
    "Shard cluster → Pause writes → Copy → Verify → Switch owners",
  deploy: "Green deployment → Build green; then Router → Switch to green",
  "inspect-cache": "Select the Redis component",
  "inspect-replica": "Select the replica component",
  "inspect-proxy": "Select the router component",
};
function renderGuide() {
  const c = chapters[chapter];
  if (realMode && chapter === 0) {
    const s = realStatus;
    $("guide-content").innerHTML = `<span class="guide-kicker">REAL INVENTORY RUN</span><h3>Follow a committed stock cycle.</h3><p class="guide-intro">Each cycle reads stock, reserves one unit, releases it, then reads the new version from PostgreSQL.</p><div class="step"><div class="step-heading"><span class="step-num">1</span>Choose 1–50 cycles/s</div><p>Use the workload slider inside the canvas. One cycle makes four local API requests.</p></div><div class="step"><div class="step-heading"><span class="step-num">2</span>Start a 30-second run</div><p>Select Start real traffic on the workload. You can stop it at any time.</p></div><div class="step"><div class="step-heading"><span class="step-num">3</span>Inspect what happened</div><p id="real-guide-result"></p></div><div class="tradeoff"><span class="tiny-label">WHAT TO NOTICE</span><p>The stock quantity returns after release while its version advances. Completed, failed and dropped are measured cycles. The other architecture controls are still model lessons.</p></div>`;
    $("real-guide-result").textContent = s?.offered
      ? `${s.offered} offered · ${s.completed} completed · ${s.failed} failed · ${s.dropped} dropped. Last path: ${s.lastPath || "in progress"}. Stock ${s.available}, version ${s.version}.`
      : "Watch offered and completed cycles, the latest elapsed time, stock and version in the workload dock.";
    $("reflection").textContent = "Why does the stock version rise even when the released quantity returns?";
    return;
  }
  const issue = state.faults[faultNode];
  const scenario = faultCatalog[faultNode][issue || faultKind];
  const protectedIssue =
    issue && state.protections.includes(`${faultNode}:${issue}`);
  const experiment = `<div class="experiment-explanation"><span class="tiny-label">ON-MAP FAULT LAB · ${faultNode.toUpperCase()}</span><b>${scenario.label}</b><p>${issue ? (protectedIssue ? scenario.result : scenario.effect) : "Select Inject in the canvas to expose this problem. Then Apply fix and compare the rates and routes."}</p><small>${state.protections.length} protection${state.protections.length === 1 ? "" : "s"} installed · retained between chapters</small></div>`;
  $("guide-content").innerHTML =
    `<span class="guide-kicker">GUIDED EXPERIMENT ${String(chapter + 1).padStart(2, "0")}</span><h3>${c.heading}</h3><p class="guide-intro">${c.intro}</p>${experiment}<div class="guide-map-note">All operations happen on the architecture. Follow the labelled controls below each component.</div>${c.steps.map((s, i) => `<div class="step ${done(s[3]) ? "done" : ""}"><div class="step-heading"><span class="step-num">${done(s[3]) ? "✓" : i + 1}</span>${s[0]}</div><p>${s[1]}</p><span class="location-cue">↗ ${actionLocations[s[3]]}</span></div>`).join("")}<div class="tradeoff"><span class="tiny-label">THE TRADE-OFF</span><p>${c.cost}</p></div>`;
  $("reflection").textContent = c.question;
}
function buildNode(action) {
  return {
    cache: "cache",
    replica: "replica",
    shard: "shard",
    scale: "api2",
    "prepare-green": "green",
  }[action];
}
function beginBuild(action) {
  const next = transition(state, "begin-build", action);
  if (!next.build || state.build) return;
  state = next;
  running = true;
  const name = descriptions[buildNode(action)][0];
  log(`${name}: provisioning. Existing traffic keeps its current route.`);
  render();
  buildTimers.push(
    setTimeout(() => {
      state = transition(state, "advance-build");
      log(`${name}: checking readiness. No new traffic admitted yet.`);
      render();
    }, 1400),
  );
  buildTimers.push(
    setTimeout(() => {
      state = transition(state, "finish-build");
      buildTimers = [];
      routeNotice =
        action === "shard"
          ? "Empty shard ready. Pause writes, copy, verify and switch owners on the cluster."
          : action === "prepare-green"
            ? "Green is ready. Switch traffic using the control on the router."
            : `${name} is ready. New routes are active; inspect the highlighted paths.`;
      log(routeNotice);
      render();
      highlightRoutes();
    }, 2900),
  );
}
function highlightRoutes() {
  clearTimeout(routeTimer);
  $("graph").classList.remove("routes-changed");
  requestAnimationFrame(() => $("graph").classList.add("routes-changed"));
  routeTimer = setTimeout(
    () => $("graph").classList.remove("routes-changed"),
    2200,
  );
}
function renderActions() {
  const busy = Boolean(state.build);
  const focusedAction = document.activeElement?.getAttribute("data-action");
  const focusedNode = document.activeElement?.closest(".component-shell")?.id;
  const button = (action, label, disabled = false) =>
    `<button class="map-action" data-action="${action}" ${disabled ? "disabled" : ""}>${label}</button>`;
  $("client-actions").innerHTML =
    button("run", realMode ? (running ? "Real flow running" : "Start real traffic")
      : (running ? "Flow running" : "Start traffic"), running) +
    button("burst", realMode ? "Set 10 cycles/s" : "Send 30,000 req/s", realMode && running);
  $("proxy-actions").innerHTML = button(
    "deploy",
    state.green ? "Roll back to blue" : "Switch to green",
    busy || !state.greenReady || (!state.green && Boolean(state.faults.green)),
  );
  $("api-actions").innerHTML =
    `<span class="map-fact">${state.instances} instance${state.instances > 1 ? "s" : ""} · ${state.green ? "blue on standby" : "receiving traffic"}</span>`;
  for (const [id, action, active, label] of [
    ["cache", "cache", state.cache, "Build Redis"],
    ["replica", "replica", state.replica, "Build replica"],
    ["shard", "shard", state.shards > 1, "Build shard"],
    ["api2", "scale", state.instances > 1, "Build instance"],
    ["green", "prepare-green", state.greenReady, "Build green"],
  ]) {
    const pending = state.build?.action === action;
    const status = pending
      ? state.build.stage === "provisioning"
        ? "PROVISIONING"
        : "CHECKING"
      : active
        ? "READY"
        : "NOT BUILT";
    const shell = $(`component-${id}`);
    shell.dataset.buildStage = pending ? state.build.stage : "";
    shell.classList.toggle("building", pending);
    shell.classList.toggle("planned", !active && !pending);
    shell.classList.toggle("ready", active);
    $(`${id}-status`).textContent = status;
    shell
      .querySelector(".node")
      .classList.toggle("inactive", !active && !pending);
    let controls = pending
      ? `<span class="map-fact">${state.build.stage === "provisioning" ? "Creating component…" : "Verifying readiness…"}</span>`
      : button(
          action,
          active ? "Ready" : `+ ${label}`,
          busy ||
            active ||
            ((id === "replica" || id === "shard") && state.primaryDown),
        );
    if (id === "shard") {
      const nextAction =
        {
          paused: "copy-buckets",
          copied: "verify-buckets",
          verified: "switch-ownership",
        }[state.migration?.stage] || "start-migration";
      const nextLabel =
        {
          paused: "Copy buckets",
          copied: "Verify copy",
          verified: "Switch owners",
        }[state.migration?.stage] || "Pause writes";
      controls =
        button(
          "shard",
          pending ? "Building…" : "+ Add shard",
          busy ||
            state.shards >= 6 ||
            Boolean(state.migration) ||
            state.primaryDown,
        ) +
        button(
          nextAction,
          nextLabel,
          busy || state.shards < 2 || state.primaryDown,
        ) +
        (state.migration ? button("cancel-migration", "Cancel") : "");
    }
    if (id === "cache" && active)
      controls = button("cache", "Disable cache", busy);
    if (id === "replica" && state.primaryDown && active)
      controls = button("promote", "Promote replica", busy || !state.fenced);
    if (id === "api2" && active)
      controls = button(
        "scale",
        state.instances < 3 ? "+ Add third instance" : "3 instances ready",
        busy || state.instances >= 3,
      );
    if (id === "green" && active)
      controls = `<span class="map-fact">${state.green ? "Receiving traffic · v2" : "Ready · switch at router"}</span>`;
    $(`${id}-actions`).innerHTML = controls;
  }
  $("db-actions").innerHTML = state.primaryDown
    ? state.fenced
      ? button("restart-primary", "Restore + verify", busy)
      : button("fence", "Fence primary", busy) +
        button("restart-primary", "Restore", busy)
    : button("fail", "Fail primary", busy);
  $("db-status").textContent = state.primaryDown
    ? state.fenced
      ? "FENCED"
      : "UNAVAILABLE"
    : "READY";
  $("build-status").classList.toggle("is-building", busy);
  $("build-message").textContent = busy
    ? `${descriptions[buildNode(state.build.action)][0]} · ${state.build.stage === "provisioning" ? "Provisioning component" : "Checking readiness"} · existing routes stay active`
    : routeNotice;
  for (const id of Object.keys(faultCatalog)) {
    const control = document.createElement("button");
    control.className = "fault-open";
    control.textContent = "⚡ Faults";
    control.setAttribute(
      "aria-label",
      `Explore ${descriptions[id][0]} failures`,
    );
    control.onclick = () => {
      faultNode = id;
      faultKind = state.faults[id] || Object.keys(faultCatalog[id])[0];
      selectPanel(false);
      $("fault-console").open = true;
      renderFaultConsole();
      renderGuide();
      $("fault-select").focus({ preventScroll: true });
    };
    $(`component-${id}`).querySelector(".fault-open")?.remove();
    $(`component-${id}`).append(control);
  }
  renderFaultConsole();
  document
    .querySelectorAll(".map-action")
    .forEach((b) => (b.onclick = () => act(b.dataset.action)));
  if (focusedAction && focusedNode) {
    const replacement = [
      ...$(focusedNode).querySelectorAll(".map-action"),
    ].find((b) => b.dataset.action === focusedAction && !b.disabled);
    (replacement || $(focusedNode).querySelector(".node")).focus({
      preventScroll: true,
    });
  }
}
function renderFaultConsole() {
  const issue = state.faults[faultNode];
  const scenario = faultCatalog[faultNode][faultKind];
  $("fault-target").textContent = descriptions[faultNode][0];
  $("fault-select").innerHTML = Object.entries(faultCatalog[faultNode])
    .map(
      ([key, item]) =>
        `<option value="${key}" ${key === faultKind ? "selected" : ""}>${item.label}</option>`,
    )
    .join("");
  $("inject-fault").disabled =
    !componentAvailable(state, faultNode) ||
    Boolean(state.build) ||
    Boolean(state.migration);
  $("fix-fault").disabled =
    !issue || state.protections.includes(`${faultNode}:${issue}`);
  $("fix-fault").textContent = issue
    ? faultCatalog[faultNode][issue].fix
    : scenario.fix;
  $("recover-fault").disabled = !issue;
  $("fault-outcome").textContent = !componentAvailable(state, faultNode)
    ? "Build this component first. Choose ⚡ Faults on any component."
    : issue
      ? `${faultCatalog[faultNode][issue].label} · ${state.protections.includes(`${faultNode}:${issue}`) ? "mitigation installed; fault remains until recovery" : "fault active — inspect the impact"}`
      : "Select a problem → Inject → Apply a fix → Recover. Architecture and fixes persist.";
}
function render() {
  const m = metrics(state);
  $("load").value = state.rps;
  $("load-value").innerHTML = `${fmt(state.rps)} <small>req/s</small>`;
  $("load-down").disabled = state.rps <= loadSteps.min;
  $("load-up").disabled = state.rps >= loadSteps.max;
  $("client-meta").textContent = `${state.rps} offered req/s`;
  $("proxy-meta").textContent = state.green
    ? "green pool · v2"
    : "blue pool · v1";
  $("api-meta").textContent =
    `${state.instances} instance${state.instances > 1 ? "s" : ""} · blue v1`;

  $("cache-meta").textContent = state.cache
    ? `${Math.round(m.hitRatio * 100)}% assumed hits · ${state.faults.cache || "healthy"}`
    : "not enabled";
  $("db-name").textContent = state.promoted ? "New primary DB" : "Primary DB";
  $("db-meta").textContent = state.primaryDown
    ? state.fenced
      ? "fenced · writes stopped"
      : "failed · writes stopped"
    : state.shards > 1
      ? "shard 01 · tenant buckets"
      : state.replica
        ? "authoritative writes"
        : "reads + writes";
  $("replica-meta").textContent = state.replica
    ? "eventual reads · async WAL"
    : state.promoted
      ? "promoted · reseed pending"
      : "not provisioned";
  $("shard-meta").textContent =
    `${state.shards} nodes · epoch ${state.epoch} · ${state.migration?.stage || "stable ownership"}`;
  const counts = shardRecords(state);
  const bank = $("shard-bank");
  while (bank.children.length > counts.length) bank.lastElementChild.remove();
  counts.forEach((count, i) => {
    let cell = bank.children[i];
    if (!cell) {
      cell = document.createElement("button");
      cell.type = "button";
      cell.className = "shard-cell new-shard";
      cell.dataset.node = "shard";
      cell.dataset.shard = String(i);
      cell.innerHTML = `<img src="/assets/postgresql.svg" alt=""/><b>S${i + 1}</b><small></small>`;
      cell.onanimationend = () => cell.classList.remove("new-shard", "moved-buckets");
      bindComponentHover(cell);
      bank.append(cell);
    } else if (Number(cell.dataset.records) !== count && !reduced) {
      cell.classList.add("moved-buckets");
    }
    cell.dataset.records = String(count);
    cell.querySelector("small").textContent = fmt(count);
    cell.setAttribute("aria-label", `Inspect shard S${i + 1}: ${fmt(count)} records`);
    cell.classList.toggle("owns-data", count > 0);
    cell.classList.toggle("empty-shard", count === 0);
    cell.classList.toggle("shard-down", state.faults.shard === "crash" && i === counts.findLastIndex(n => n > 0));
    if (reduced) cell.classList.remove("new-shard", "moved-buckets");
  });
  $("record-count").textContent =
    `${fmt(state.records)} logical records · ${state.shards} shard${state.shards > 1 ? "s" : ""}`;
  $("quality").textContent =
    `${fmt(m.staleReads)} stale reads/s · ${state.protections.length} protections`;
  for (const id of Object.keys(faultCatalog))
    $(`component-${id}`).classList.toggle("faulted", Boolean(state.faults[id]));
  for (const [id, active] of [
    ["cache", m.cacheAvailable],
    ["replica", state.replica],
    ["shard", counts.slice(1).some((n) => n > 0)],
  ]) {
    document
      .querySelector(`[data-node=${id}]`)
      .classList.toggle("inactive", !active);
  }
  renderRoutes();

  $("component-api").classList.toggle("standby", state.green);
  $("component-green").classList.toggle("serving", state.green);
  $("api2-meta").textContent =
    state.instances > 1
      ? `${state.instances - 1} extra instance${state.instances > 2 ? "s" : ""}`
      : "not built";
  $("green-meta").textContent = state.green
    ? "active route · v2"
    : state.greenReady
      ? "ready · awaiting switch"
      : "not built";
  const db = document.querySelector("[data-node=db]");
  db.classList.toggle("stressed", m.pressure > 1 && !state.primaryDown);
  db.classList.toggle("failed", state.primaryDown);
  $("completed").innerHTML = `${fmt(m.completed)}<small>req/s</small>`;
  $("db-demand").innerHTML =
    `${fmt(m.dbDemand)}<small>/ ${state.primaryDown ? "0" : fmt(m.dbCapacity)} ops/s capacity</small>`;
  $("rejected").innerHTML = `${fmt(m.rejected)}<small>req/s</small>`;
  $("db-pressure").textContent = state.primaryDown
    ? "Primary writes unavailable"
    : m.pressure > 1
      ? "Demand exceeds capacity"
      : "Within modelled capacity";
  $("rejected").parentElement.classList.toggle("alert", m.rejected > 0);
  $("graph").classList.toggle("running", running);
  $("graph").classList.toggle("reduced", reduced);
  $("run").textContent = running ? "Ⅱ Pause traffic" : "▶ Start traffic";
  $("run-state").innerHTML =
    `<i></i> ${running ? "Model flow active" : "Traffic paused"}`;
  $("run-state").classList.toggle("active", running);
  paintReal();
  $("motion").textContent = `Motion: ${reduced ? "off" : "on"}`;
  $("motion").setAttribute("aria-pressed", String(reduced));
  renderActions();
  buildMessage = $("build-message").textContent;
  renderHover();
  renderGuide();
  if (pinned) renderInspector();
}
function act(action) {
  if (realMode && action === "run") {
    toggleReal();
    return;
  }
  if (realMode && action === "burst") {
    realRate = 10;
    log("Real load set to 10 cycles/s for the next run.");
    render();
    return;
  }
  if (
    [
      "start-migration",
      "copy-buckets",
      "verify-buckets",
      "switch-ownership",
      "cancel-migration",
    ].includes(action)
  ) {
    state = transition(state, action);
    routeNotice = {
      "start-migration":
        "Moving buckets: writes paused and drained; reads keep the old owner.",
      "copy-buckets":
        "Copy complete in the sketch. Old owner still authoritative; verify before switching.",
      "verify-buckets":
        "Model verification gate passed. Real lab must compare counts, checksums and reservation ledger.",
      "switch-ownership": `Ownership epoch ${state.epoch}: routes switched, writes resumed. One million logical records conserved.`,
      "cancel-migration":
        "Migration cancelled. Original ownership and routing preserved.",
    }[action];
    log(routeNotice);
    render();
    if (action === "switch-ownership") highlightRoutes();
    return;
  }
  if (
    ["replica", "shard", "scale", "prepare-green"].includes(action) ||
    (action === "cache" && !state.cache)
  ) {
    beginBuild(action);
    return;
  }
  if (action.startsWith("inspect-")) {
    inspect(action.slice(8));
    return;
  }
  if (action === "run") {
    running = true;
    log("Illustrative request flow started. No network traffic is generated.");
  } else if (action === "burst") {
    state = transition(state, "load", 30000);
    running = true;
    log("Offered load set to 30,000 req/s in the capacity model.");
  } else {
    state = transition(state, action);
    const messages = {
      cache: state.cache
        ? "Redis enabled: warm-cache assumption removes 80% of database reads."
        : "Redis disabled: reads return to storage.",
      replica:
        "Read replica provisioned in the model. Writes remain on primary.",
      fail: "Primary failed: writes unavailable. Remaining reads depend on cache / replica.",
      fence: "Old writer fenced in the model. Real mode must verify isolation.",
      promote:
        "Replica promoted in the model. Real recovery must quantify potential data loss.",
      scale: `API scaled to ${state.instances} instances. Database capacity is unchanged.`,
      shard: "Second shard added with an assumed balanced tenant split.",
      "restart-primary":
        "Primary restored in the sketch. Real WAL replay and ledger verification are required before serving.",
      deploy: state.green
        ? "Route switched to green v2. Green passed the illustrative readiness sequence; new requests use v2."
        : "Route rolled back to blue v1.",
    };
    log(messages[action] || "Model configuration changed.");
  }
  render();
  if (["deploy", "cache", "promote"].includes(action)) highlightRoutes();
}
function selectChapter(i) {
  chapter = i;
  state = transition(state, "lesson", i);
  faultNode = ["db", "cache", "replica", "db", "shard", "green"][i];
  faultKind =
    state.faults[faultNode] || Object.keys(faultCatalog[faultNode])[0];
  pinned = null;
  selectPanel(false);
  const c = chapters[i];
  $("chapter-number").textContent =
    `EXPERIMENT ${String(i + 1).padStart(2, "0")} / ${c.kicker}`;
  $("lesson-title").textContent = c.title;
  $("lesson-subtitle").textContent = c.subtitle;
  document.querySelectorAll(".chapter").forEach((b, n) => {
    b.classList.toggle("selected", n === i);
    b.setAttribute("aria-current", n === i ? "step" : "false");
  });
  document
    .querySelectorAll(".node")
    .forEach((n) => n.classList.remove("pinned"));
  log(
    `${c.name}: continuing the same system. Topology, load and fixes retained.`,
  );
  render();
}
$("chapters").innerHTML = chapters
  .map(
    (c, i) =>
      `<button class="chapter ${i === 0 ? "selected" : ""}" data-chapter="${i}" aria-current="${i === 0 ? "step" : "false"}"><span class="number">${String(i + 1).padStart(2, "0")}</span><span><b>${c.name}</b><small>${c.sub}</small></span></button>`,
  )
  .join("");
document
  .querySelectorAll("[data-chapter]")
  .forEach((b) => (b.onclick = () => selectChapter(Number(b.dataset.chapter))));
$("load").oninput = (e) => {
  if (realMode) {
    realRate = Number(e.target.value);
    render();
    return;
  }
  state = transition(state, "load", e.target.value);
  render();
};
$("load").onchange = () => log(realMode
  ? `Real load set to ${realRate} cycles/s for the next run.`
  : `Offered load changed to ${state.rps} req/s.`);
for (const [id, direction] of [["load-down", -1], ["load-up", 1]]) {
  $(id).onclick = () => {
    if (realMode) {
      realRate = Math.max(1, Math.min(50, realRate + direction));
      render();
      return;
    }
    state = transition(state, "load", state.rps + direction * loadSteps.interval);
    log(`Offered load changed to ${fmt(state.rps)} req/s.`);
    render();
  };
}
$("run").onclick = () => {
  if (realMode) {
    toggleReal();
    return;
  }
  running = !running;
  log(
    running
      ? "Illustrative flow started. Metrics remain model projections."
      : "Flow paused. Values remain projections for the selected configuration.",
  );
  render();
};
$("reset").onclick = () => {
  if (realMode && realStatus?.running) traffic.stop().then(updateReal).catch(showRealError);
  buildTimers.forEach(clearTimeout);
  buildTimers = [];
  clearTimeout(routeTimer);
  state = transition(state, "reset");
  running = false;
  routeNotice =
    "Fresh million-record baseline. All components and protections reset.";
  selectChapter(chapter);
  log(routeNotice);
};
function paintReal() {
  if (!realMode) return;
  const s = realStatus;
  $("telemetry-mode").textContent = "REAL";
  $("load-label").textContent = "Offered cycles/s";
  $("client-meta").textContent = `${realRate} selected cycles/s · Java fixture`;
  $("load").value = realRate;
  $("load").disabled = Boolean(s?.running);
  $("load-value").textContent = `${realRate} cycles/s`;
  $("load-down").disabled = Boolean(s?.running) || realRate <= 1;
  $("load-up").disabled = Boolean(s?.running) || realRate >= 50;
  $("load-step-note").textContent = "1–50 cycles/s · 30s · max 2 concurrent";
  $("traffic-mix").textContent = "Read → reserve → release → read";
  $("completed-label").textContent = "Completed";
  $("db-demand-label").textContent = "Offered";
  $("rejected-label").textContent = "Failed / dropped";
  $("completed").textContent = String(s?.completed ?? 0);
  $("db-demand").textContent = String(s?.offered ?? 0);
  $("rejected").textContent = `${s?.failed ?? 0} / ${s?.dropped ?? 0}`;
  $("db-pressure").textContent = `${s?.inFlight ?? 0} in flight`;
  document.querySelector(".canvas-telemetry").setAttribute("aria-label", "Real inventory workload and cycle counts");
  document.querySelector(".canvas-telemetry .metrics").setAttribute("aria-label", "Measured inventory cycles");
  $("run").textContent = s?.running ? "Ⅱ Stop real traffic" : "▶ Start real traffic";
  $("run-state").textContent = s?.running ? "Real traffic active" : "Real traffic ready";
  $("run-state").classList.toggle("active", Boolean(s?.running));
  $("real-detail").hidden = false;
  $("real-detail").textContent = realError || (s?.lastError
    ? `Last error: ${s.lastError}`
    : s?.completed
      ? `Last cycle ${s.lastElapsedMillis} ms · stock ${s.available} · version ${s.version}`
      : "No completed cycle yet · stock and version pending");
}
function updateReal(status) {
  const wasRunning = running;
  realStatus = status;
  realError = "";
  running = Boolean(status.running);
  paintReal();
  renderGuide();
  $("graph").classList.toggle("running", running);
  if (running !== wasRunning) renderActions();
}
function showRealError(error) {
  realError = error.message || "Real traffic control is unavailable.";
  paintReal();
  log(realError);
}
async function toggleReal() {
  $("run").disabled = true;
  try {
    updateReal(realStatus?.running ? await traffic.stop() : await traffic.start(realRate));
    log(realStatus.running ? "Real inventory cycles started on the owned fixture."
      : "Real inventory traffic stopped; committed stock is preserved.");
  } catch (error) {
    showRealError(error);
  } finally {
    $("run").disabled = false;
  }
}
async function initReal() {
  try {
    // Older running preview servers may not serve this optional asset yet.
    // Initialize the workbench first so that a missing asset cannot blank it.
    const { realTraffic } = await import("./real-traffic.mjs");
    traffic = realTraffic();
    const status = await traffic.status();
    realMode = true;
    $("preview-mode").textContent = "Owned Java run";
    $("preview-detail").textContent = "Real inventory cycles enabled. Other components remain illustrative.";
    $("scale-notice").textContent = "The workload dock shows real Java → PostgreSQL cycles. The million-row map and unbuilt solutions remain illustrative.";
    $("load").min = 1;
    $("load").max = 50;
    $("load").step = 1;
    $("load-down").setAttribute("aria-label", "Decrease real load by one cycle per second");
    $("load-up").setAttribute("aria-label", "Increase real load by one cycle per second");
    $("load").setAttribute("aria-describedby", "load-step-note");
    updateReal(status);
    render();
    setInterval(async () => {
      try { updateReal(await traffic.status()); }
      catch (error) { showRealError(error); }
    }, 750);
  } catch {
    // A preview without a selected run keeps the labelled illustrative model.
  }
}
$("fault-select").onchange = (e) => {
  faultKind = e.target.value;
  renderFaultConsole();
  renderGuide();
};
for (const [id, action] of [
  ["inject-fault", "inject"],
  ["fix-fault", "mitigate"],
  ["recover-fault", "recover"],
]) {
  $(id).onclick = () => {
    const kind = action === "inject" ? faultKind : state.faults[faultNode];
    state = transition(
      state,
      action,
      action === "inject" ? { node: faultNode, kind } : faultNode,
    );
    routeNotice = `${descriptions[faultNode][0]}: ${action === "inject" ? faultCatalog[faultNode][kind].effect : action === "mitigate" ? faultCatalog[faultNode][kind].result : "Recovery complete in the model; installed protections retained."}`;
    log(routeNotice);
    selectPanel(false);
    render();
    highlightRoutes();
  };
}
$("focus-map").onclick = () => {
  const focused = document.body.classList.toggle("focus-map");
  $("focus-map").textContent = focused ? "Show learning path" : "Focus canvas";
  $("focus-map").setAttribute("aria-pressed", String(focused));
};
$("motion").onclick = () => {
  reduced = !reduced;
  render();
};
$("guide-tab").onclick = () => selectPanel(false);
$("inspect-tab").onclick = () => {
  selectPanel(true);
  renderInspector();
};
for (const id of ["guide-tab", "inspect-tab"])
  $(id).onkeydown = (e) => {
    if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
      const target = id === "guide-tab" ? "inspect-tab" : "guide-tab";
      $(target).click();
      $(target).focus();
    }
  };
function hideHover() {
  clearTimeout(hoverTimer);
  cancelAnimationFrame(hoverFrame);
  hoverNode?.removeAttribute("aria-describedby");
  hoverNode?.classList.remove("hovered");
  hoverNode = null;
  hoverPoint = null;
  $("hover-card").hidden = true;
  $("build-message").textContent = buildMessage;
}
function renderHover() {
  if (!hoverNode) return;
  if (!hoverNode.isConnected) {
    hideHover();
    return;
  }
  const id = hoverNode.dataset.node;
  const shardIndex = hoverNode.dataset.shard === undefined ? null : Number(hoverNode.dataset.shard);
  const snapshot = componentSnapshot(state, id, running, shardIndex);
  const tip = $("hover-card");
  $("hover-title").textContent = shardIndex === null ? descriptions[id][0] : `Shard S${shardIndex + 1}`;
  $("hover-state").textContent = snapshot.status;
  $("hover-detail").textContent = snapshot.detail;
  tip.hidden = false;
  const rect = tip.getBoundingClientRect();
  const bounds = { left: 0, top: 0, right: document.documentElement.clientWidth, bottom: innerHeight };
  const placement = placeHover(hoverNode.closest(".component-shell").getBoundingClientRect(), rect, bounds, hoverPoint);
  if (placement) {
    tip.style.left = `${placement.left}px`;
    tip.style.top = `${placement.top}px`;
    hoverNode.setAttribute("aria-describedby", "hover-card");
    $("build-message").textContent = buildMessage;
  } else {
    // Small screens may have no safe overlay space. Reuse the status strip.
    tip.hidden = true;
    hoverNode.setAttribute("aria-describedby", "build-message");
    $("build-message").textContent = `${descriptions[id][0]} · ${snapshot.status} · ${snapshot.detail}`;
  }
}
function bindComponentHover(n) {
  const show = (point) => {
    hideHover();
    hoverPoint = point;
    const reveal = () => {
      hoverNode = n;
      n.classList.add("hovered");
      renderHover();
    };
    if (point) hoverTimer = setTimeout(reveal, 180);
    else reveal();
  };
  n.onpointerenter = e => { if (e.pointerType !== "touch") show({ x: e.clientX, y: e.clientY }); };
  n.onpointermove = e => {
    if (e.pointerType === "touch") return;
    hoverPoint = { x: e.clientX, y: e.clientY };
    cancelAnimationFrame(hoverFrame);
    hoverFrame = requestAnimationFrame(renderHover);
  };
  n.onpointerleave = hideHover;
  n.onfocus = () => { if (n.matches(":focus-visible")) show(null); };
  n.onblur = hideHover;
  n.onclick = () => {
    hideHover();
    inspect(n.dataset.node, n.dataset.shard === undefined ? null : Number(n.dataset.shard));
  };
}
document.querySelectorAll(".node").forEach(bindComponentHover);
window.addEventListener("scroll", hideHover, true);
window.addEventListener("resize", hideHover);
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    hideHover();
    selectPanel(false);
  }
});
log("Baseline ready: one API, one database. Select a component to explore.");
render();
renderInspector();
const graphViewport = document.querySelector(".graph-scroll");
const viewportSize = () => ({ width: graphViewport.clientWidth, height: graphViewport.clientHeight });
const dockInsets = () => graphViewport.clientWidth > 760
  ? { left: 174, right: $("fault-console").open ? 194 : 48 }
  : { left: 0, right: 0 };
let camera = fitCamera(viewportSize(), dockInsets());
let fitted = true;
let drag = null;
let previousSize = viewportSize();
function paintCamera() {
  $("graph").style.transform = `translate(${camera.x}px, ${camera.y}px) scale(${camera.scale})`;
  $("zoom-level").textContent = `${Math.round(camera.scale * 100)}%`;
  $("zoom-out").disabled = camera.scale <= 0.15;
  $("zoom-in").disabled = camera.scale >= 2.5;
  hideHover();
}
function fitView() {
  fitted = true;
  camera = fitCamera(viewportSize(), dockInsets());
  paintCamera();
}
function zoomView(factor, point) {
  const size = viewportSize();
  fitted = false;
  camera = zoomCamera(camera, factor, point || { x: size.width / 2, y: size.height / 2 }, size);
  paintCamera();
}
$("zoom-in").onclick = () => zoomView(1.25);
$("zoom-out").onclick = () => zoomView(1 / 1.25);
$("fit-system").onclick = fitView;
$("fault-console").addEventListener("toggle", () => { if (fitted) fitView(); });
graphViewport.addEventListener("focusin", event => {
  const card = event.target.closest(".component-shell");
  if (!card) return;
  const box = card.getBoundingClientRect(), frame = graphViewport.getBoundingClientRect();
  const offset = (start, end, low, high) => end - start > high - low - 24
    ? (low + high - start - end) / 2
    : start < low + 12 ? low + 12 - start : end > high - 12 ? high - 12 - end : 0;
  const insets = dockInsets();
  const dx = offset(box.left, box.right, frame.left + insets.left, frame.right - insets.right);
  const dy = offset(box.top, box.bottom, frame.top, frame.bottom);
  if (!dx && !dy) return;
  fitted = false;
  camera = panCamera(camera, dx, dy, viewportSize());
  paintCamera();
  if (event.target.matches(".node:focus-visible,.shard-cell:focus-visible")) {
    hoverNode = event.target;
    hoverNode.classList.add("hovered");
    renderHover();
  }
});
graphViewport.addEventListener("wheel", event => {
  if (event.target.closest(".canvas-dock")) return;
  event.preventDefault();
  const rect = graphViewport.getBoundingClientRect();
  const delta = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? rect.height : 1);
  zoomView(Math.exp(-Math.max(-300, Math.min(300, delta)) * 0.002), { x: event.clientX - rect.left, y: event.clientY - rect.top });
}, { passive: false });
graphViewport.addEventListener("pointerdown", event => {
  if (drag || event.button !== 0 || event.target.closest("button, a, input, select, .canvas-dock")) return;
  drag = { id: event.pointerId, x: event.clientX, y: event.clientY };
  graphViewport.setPointerCapture(event.pointerId);
  graphViewport.classList.add("panning");
  graphViewport.focus({ preventScroll: true });
  hideHover();
});
graphViewport.addEventListener("pointermove", event => {
  if (!drag || drag.id !== event.pointerId) return;
  fitted = false;
  camera = panCamera(camera, event.clientX - drag.x, event.clientY - drag.y, viewportSize());
  drag.x = event.clientX;
  drag.y = event.clientY;
  paintCamera();
});
function endPan() {
  const id = drag?.id;
  drag = null;
  if (id !== undefined && graphViewport.hasPointerCapture(id)) graphViewport.releasePointerCapture(id);
  graphViewport.classList.remove("panning");
}
graphViewport.addEventListener("pointerup", endPan);
graphViewport.addEventListener("pointercancel", endPan);
graphViewport.addEventListener("lostpointercapture", endPan);
window.addEventListener("blur", endPan);
graphViewport.addEventListener("keydown", event => {
  if (event.target !== graphViewport) return;
  const shifts = { ArrowLeft: [40, 0], ArrowRight: [-40, 0], ArrowUp: [0, 40], ArrowDown: [0, -40] };
  if (shifts[event.key]) {
    fitted = false;
    camera = panCamera(camera, ...shifts[event.key], viewportSize());
    paintCamera();
  } else if (["+", "="].includes(event.key)) zoomView(1.25);
  else if (event.key === "-") zoomView(1 / 1.25);
  else if (event.key === "0") fitView();
  else return;
  event.preventDefault();
});
new ResizeObserver(() => {
  const size = viewportSize();
  camera = fitted ? fitCamera(size, dockInsets()) : panCamera(camera, (size.width - previousSize.width) / 2, (size.height - previousSize.height) / 2, size);
  previousSize = size;
  paintCamera();
}).observe(graphViewport);
initReal();
