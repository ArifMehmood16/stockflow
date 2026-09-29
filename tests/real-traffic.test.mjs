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
  assert.deepEqual(calls.map(call => call.path), [
    "/lab/traffic",
    "/lab/traffic/start?rate=10&seconds=30&concurrency=2",
    "/lab/traffic/stop",
  ]);
  assert.throws(() => client.start(250000), RangeError);
  assert.equal(calls.length, 3);
});
