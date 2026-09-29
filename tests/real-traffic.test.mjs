import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { realTraffic } from "../prototype/real-traffic.mjs";

test("workbench startup remains compatible with an already-running preview's asset routes", async () => {
  const app = await readFile(new URL("../prototype/app.mjs", import.meta.url), "utf8");
  const originalRoutes = new Set(["./model.mjs", "./hover.mjs", "./topology.mjs"]);
  for (const match of app.matchAll(/^import[\s\S]*?from\s+["']([^"']+)["'];/gm)) {
    assert.ok(originalRoutes.has(match[1]), `Optional ${match[1]} must not block workbench startup`);
  }
});

test("browser control uses only the fixed local path and bounded rate", async () => {
  const calls = [];
  const client = realTraffic(async (path, options) => {
    calls.push({ path, options });
    return { ok: true, json: async () => ({ running: true, offered: 1 }) };
  });
  await client.status();
  await client.start(10);
  await client.stop();
  await client.addInstance();
  await client.removeInstance();
  assert.deepEqual(calls.map(call => call.path), [
    "/lab/traffic",
    "/lab/traffic/start?rate=10&seconds=30&concurrency=2",
    "/lab/traffic/stop",
    "/lab/traffic/add-instance",
    "/lab/traffic/remove-instance",
  ]);
  assert.throws(() => client.start(250000), RangeError);
  assert.equal(calls.length, 5);
});

test("opening or changing the fault panel cannot enable model faults during real traffic", async () => {
  const { runInNewContext } = await import("node:vm");
  const app = await readFile(new URL("../prototype/app.mjs", import.meta.url), "utf8");
  const panel = app.slice(app.indexOf("function renderFaultConsole()"), app.indexOf("function render()"));
  const nodes = {};
  const node = id => nodes[id] ??= {};
  runInNewContext(`${panel}\nrenderFaultConsole();`, {
    realMode: true, $: node,
    state: { faults: {}, protections: [] },
    faultNode: "api", faultKind: "crash",
    faultCatalog: { api: { crash: { label: "Crash", fix: "Restart" } } },
    descriptions: { api: ["API"] }, componentAvailable: () => true,
  });
  for (const id of ["inject-fault", "fix-fault", "recover-fault"]) {
    assert.equal(node(id).disabled, true, `${id} must remain unavailable`);
  }
  assert.match(node("fault-outcome").textContent, /model preview/i);
});

test("real workload distinguishes request target, dispatched requests and completed cycles", async () => {
  const { runInNewContext } = await import("node:vm");
  const app = await readFile(new URL("../prototype/app.mjs", import.meta.url), "utf8");
  const paint = app.slice(app.indexOf("function paintReal()"), app.indexOf("function updateReal("));
  const nodes = {};
  const node = id => nodes[id] ??= { setAttribute() {}, classList: { toggle() {} } };
  runInNewContext(`${paint}\npaintReal();`, {
    realMode: true, realRate: 2, realError: "", $: node,
    realStatus: { completed: 1, offered: 2, primaryRequests: 3, secondaryRequests: 2 },
    document: { querySelector: node },
  });
  assert.match(node("load-value").textContent, /8.*req\/s/);
  assert.match(node("load-step-note").textContent, /4 requests/);
  assert.equal(node("db-demand").textContent, "5");
  assert.equal(node("db-demand-label").textContent, "Requests sent (total)");
  assert.equal(node("completed-label").textContent, "Cycles completed");
});

test("status polling does not erase a failed user action", async () => {
  const { runInNewContext } = await import("node:vm");
  const app = await readFile(new URL("../prototype/app.mjs", import.meta.url), "utf8");
  const update = app.slice(app.indexOf("function updateReal("), app.indexOf("function realSnapshot("));
  const context = {
    running: false, realStatus: null, realError: "Traffic control returned 403",
    state: { instances: 1 }, scalePending: false, pinned: false,
    paintReal() {}, renderGuide() {}, renderHover() {},
    $: () => ({ classList: { toggle() {} } }),
  };
  runInNewContext(`${update}\nupdateReal({running: false, instances: 1, inFlight: 0});`, context);
  assert.equal(context.realError, "Traffic control returned 403");
});
