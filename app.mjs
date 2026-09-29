import { initialState, transition, metrics } from "./model.mjs";
let state = initialState();
let chapter = 0;
let running = false;
let reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
let pinned = null;
let events = [];
let buildTimers = [];
let routeTimer;
let routeNotice =
  "Build directly on the map. Select a component to inspect it.";
const $ = (id) => document.getElementById(id);
const fmt = (n) => Math.round(n).toLocaleString("en-GB");
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
        "Raise offered load to 300 req/s. Watch demand exceed the database’s illustrative capacity.",
        "Send 300 req/s",
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
        "Use the same 300 req/s workload before and after the change.",
        "Send 300 req/s",
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
        "Send 300 req/s",
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
      "A second primary can own a different set of tenants. This preview assumes a perfectly balanced split.",
    steps: [
      [
        "Increase demand",
        "Expose the capacity limit at 300 offered req/s.",
        "Send 300 req/s",
        "burst",
      ],
      [
        "Add a second shard",
        "Build the new shard on the map. After readiness, this preview applies a balanced split; real migration needs verification.",
        "Add shard",
        "shard",
      ],
      [
        "Remove the API limit",
        "With more database capacity, the single API becomes the next bottleneck.",
        "Add instance",
        "scale",
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
    "Model capacity: 220 req/s per instance. This is an arbitrary teaching constant.",
    "Virtual threads and more instances do not create more database connections or CPU capacity.",
  ],
  cache: [
    "Redis cache",
    "Disposable read acceleration",
    "Serves repeated eventual stock reads; misses continue to storage.",
    "Assumed warm hit rate: 80% of reads when enabled. TTL/races are not modelled here.",
    "Reservations always validate authoritative stock. Version guards, TTL jitter and bounded fallback are future lessons.",
  ],
  db: [
    "Primary database",
    "Authoritative write store",
    "Owns stock, reservations and persistent idempotency records.",
    "Model capacity: 120 operations/s per balanced shard, not a PostgreSQL benchmark.",
    "Atomic conditional updates prevent overselling. Async failover can lose acknowledged writes; recovery needs a write ledger.",
  ],
  replica: [
    "Read replica",
    "Asynchronous copy",
    "Serves eligible eventual reads while following primary WAL.",
    "Model capacity: 120 reads/s. Replay lag is explained, not simulated in this preview.",
    "Session-consistent reads should use primary or a verified caught-up replica. A replica is not a backup.",
  ],
  shard: [
    "Shard 02",
    "Independent data owner",
    "Owns a subset of tenant buckets on a separate database primary.",
    "Sketch assumes balanced demand after an illustrative provisioning/readiness sequence.",
    "Real migration must pause writes, drain, copy, verify and switch ownership epoch. A hot tenant remains hot.",
  ],
};
descriptions.api2 = [
  "Extra API instance",
  "Horizontal service capacity",
  "Receives part of the traffic from the router after readiness checks complete.",
  "Adds 220 illustrative req/s of API capacity. Database capacity stays unchanged.",
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
  return action === "run"
    ? running
    : action === "burst"
      ? state.rps >= 300
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
function inspect(id) {
  pinned = id;
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
  const active =
    id === "green"
      ? state.greenReady
      : id === "api2"
        ? state.instances > 1
        : id === "cache"
          ? state.cache
          : id === "replica"
            ? state.replica
            : id === "shard"
              ? state.shards > 1
              : id === "db"
                ? !state.primaryDown
                : true;
  $("inspect-content").innerHTML =
    `<span class="guide-kicker">COMPONENT INSPECTOR</span><h3>${d[0]}</h3><span class="inspect-role">${d[1]}</span><dl class="inspect-list"><div><dt>Current state</dt><dd>${active ? "Available in the model" : "Inactive / unavailable"}${id === "proxy" ? " · " + (state.green ? "green v2" : "blue v1") : ""}</dd></div><div><dt>What it does</dt><dd>${d[2]}</dd></div><div><dt>Model assumption</dt><dd>${d[3]}</dd></div></dl><div class="tradeoff"><span class="tiny-label">WHAT TO REMEMBER</span><p>${d[4]}</p></div><p class="inspect-hint">All values are illustrative. Select another architecture component to inspect its role.</p><button class="secondary" id="back-guide">← Back to walkthrough</button>`;
  $("back-guide").onclick = () => selectPanel(false);
}
const actionLocations = {
  run: "Workload → Start traffic",
  burst: "Workload → Send 300 req/s",
  scale: "Extra API instance → Build instance",
  cache: "Read cache → Build Redis",
  replica: "Read replica → Build replica",
  fail: "Primary database → Fail primary",
  fence: "Primary database → Fence primary",
  promote: "Read replica → Promote replica",
  shard: "Shard 02 → Build shard",
  deploy: "Green deployment → Build green; then Router → Switch to green",
  "inspect-cache": "Select the Redis component",
  "inspect-replica": "Select the replica component",
  "inspect-proxy": "Select the router component",
};
function renderGuide() {
  const c = chapters[chapter];
  $("guide-content").innerHTML =
    `<span class="guide-kicker">GUIDED EXPERIMENT ${String(chapter + 1).padStart(2, "0")}</span><h3>${c.heading}</h3><p class="guide-intro">${c.intro}</p><div class="guide-map-note">All operations happen on the architecture. Follow the labelled controls below each component.</div>${c.steps.map((s, i) => `<div class="step ${done(s[3]) ? "done" : ""}"><div class="step-heading"><span class="step-num">${done(s[3]) ? "✓" : i + 1}</span>${s[0]}</div><p>${s[1]}</p><span class="location-cue">↗ ${actionLocations[s[3]]}</span></div>`).join("")}<div class="tradeoff"><span class="tiny-label">THE TRADE-OFF</span><p>${c.cost}</p></div>`;
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
        action === "prepare-green"
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
    button("run", running ? "Flow running" : "Start traffic", running) +
    button("burst", "Send 300 req/s");
  $("proxy-actions").innerHTML = button(
    "deploy",
    state.green ? "Roll back to blue" : "Switch to green",
    busy || !state.greenReady,
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
      ? `<span class="map-fact">Fenced · promote the replica</span>`
      : button("fence", "Fence primary", busy)
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
function render() {
  const m = metrics(state);
  $("load").value = state.rps;
  $("load-value").innerHTML = `${state.rps} <small>req/s</small>`;
  $("client-meta").textContent = `${state.rps} offered req/s`;
  $("proxy-meta").textContent = state.green
    ? "green pool · v2"
    : "blue pool · v1";
  $("api-meta").textContent =
    `${state.instances} instance${state.instances > 1 ? "s" : ""} · blue v1`;

  $("cache-meta").textContent = state.cache
    ? "80% assumed read hits"
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
    state.shards > 1 ? "assumed 50% tenant share" : "not provisioned";
  for (const [id, active] of [
    ["cache", state.cache],
    ["replica", state.replica],
    ["shard", state.shards > 1],
  ]) {
    document
      .querySelector(`[data-node=${id}]`)
      .classList.toggle("inactive", !active);
    $(`edge-${id}`).classList.toggle("enabled", active);
  }
  $("edge-replica").classList.toggle(
    "enabled",
    state.replica && !state.primaryDown,
  );
  $("edge-replica-read").classList.toggle("enabled", state.replica);
  $("edge-db").setAttribute(
    "d",
    state.green ? "M324 54 V26 H948 V334 H934" : "M622 334 H758",
  );
  $("edge-db-read").setAttribute(
    "d",
    state.green ? "M412 88 H736 V304 H758" : "M622 304 H758",
  );
  $("edge-cache").setAttribute(
    "d",
    state.green ? "M412 106 H758" : "M550 264 V224 H696 V106 H758",
  );
  $("edge-replica-read").setAttribute(
    "d",
    state.green ? "M412 142 H676 V526 H758" : "M622 350 H696 V526 H758",
  );
  $("edge-shard").setAttribute(
    "d",
    state.green ? "M324 220 V452 H534 V474" : "M534 430 V474",
  );
  $("edge-db").classList.toggle("failed", state.primaryDown);
  $("edge-db-read").classList.toggle(
    "muted-route",
    state.replica || state.primaryDown,
  );
  $("edge-db-read").classList.toggle("cache-remainder", state.cache);
  $("edge-api").classList.toggle("muted-route", state.green);
  $("edge-api2").classList.toggle(
    "enabled",
    state.instances > 1 && !state.green,
  );
  $("edge-api2-db").classList.toggle(
    "enabled",
    state.instances > 1 && !state.green && !state.primaryDown,
  );
  $("edge-green").classList.toggle("enabled", state.green);

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
  $("wal-label").style.opacity = state.replica ? 1 : 0.2;
  $("cache-label").style.opacity = state.cache ? 1 : 0.2;
  const db = document.querySelector("[data-node=db]");
  db.classList.toggle("stressed", m.pressure > 1 && !state.primaryDown);
  db.classList.toggle("failed", state.primaryDown);
  $("offered").innerHTML = `${fmt(state.rps)}<small>req/s</small>`;
  $("completed").innerHTML = `${fmt(m.completed)}<small>req/s</small>`;
  $("db-demand").innerHTML =
    `${fmt(m.dbDemand)}<small>/ ${state.primaryDown ? "0" : m.dbCapacity} ops/s</small>`;
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
  $("motion").textContent = `Motion: ${reduced ? "off" : "on"}`;
  $("motion").setAttribute("aria-pressed", String(reduced));
  renderActions();
  renderGuide();
  if (pinned) renderInspector();
}
function act(action) {
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
    state = transition(state, "load", 300);
    running = true;
    log("Offered load set to 300 req/s in the capacity model.");
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
  buildTimers.forEach(clearTimeout);
  buildTimers = [];
  clearTimeout(routeTimer);
  routeNotice = "Build directly on the map. Select a component to inspect it.";
  chapter = i;
  state = initialState();
  running = false;
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
  log(`${c.name}: baseline configuration restored.`);
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
  state = transition(state, "load", e.target.value);
  render();
};
$("load").onchange = () => log(`Offered load changed to ${state.rps} req/s.`);
$("run").onclick = () => {
  running = !running;
  log(
    running
      ? "Illustrative flow started. Metrics remain model projections."
      : "Flow paused. Values remain projections for the selected configuration.",
  );
  render();
};
$("reset").onclick = () => selectChapter(chapter);
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
for (const n of document.querySelectorAll(".node")) {
  const show = () => {
    $("hover-card").textContent = descriptions[n.dataset.node][2];
    $("hover-card").hidden = false;
  };
  const hide = () => {
    $("hover-card").hidden = true;
  };
  n.onmouseenter = show;
  n.onmouseleave = hide;
  n.onfocus = show;
  n.onblur = hide;
  n.onclick = () => {
    hide();
    inspect(n.dataset.node);
  };
}
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    $("hover-card").hidden = true;
    selectPanel(false);
  }
});
log("Baseline ready: one API, one database. Select a component to explore.");
render();
renderInspector();
const graphViewport = document.querySelector(".graph-scroll");
new ResizeObserver(() => {
  const scale = Math.max(0.65, Math.min(1, graphViewport.clientWidth / 960));
  $("graph").style.transform = `scale(${scale})`;
  graphViewport.style.height = `${670 * scale}px`;
  document.querySelector(".graph-stage").style.width = `${960 * scale}px`;
  document.querySelector(".graph-stage").style.height = `${670 * scale}px`;
}).observe(graphViewport);
