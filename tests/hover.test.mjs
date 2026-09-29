import test from "node:test";
import assert from "node:assert/strict";
import { placeHover, componentSnapshot } from "../prototype/hover.mjs";
import { initialState, transition } from "../prototype/model.mjs";

const overlaps = (a, b) => a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
test("hover stays near the pointer while clearing the inspected card", () => {
  const anchor = { left: 300, right: 450, top: 240, bottom: 360 };
  const bounds = { left: 0, top: 0, right: 900, bottom: 600 };
  const p = placeHover(anchor, { width: 250, height: 150 }, bounds, { x: 440, y: 250 });
  assert.ok(p);
  assert.ok(p.left >= 8 && p.top >= 8 && p.right <= 892 && p.bottom <= 592);
  assert.equal(overlaps(p, anchor), false);
  assert.ok(p.left >= anchor.right);
  assert.ok(p.left - 440 <= 26, "tooltip should not be pushed to a distant free region");
});
test("hover repositions at viewport edges and falls back when no safe space exists", () => {
  const anchor = { left: 280, right: 370, top: 10, bottom: 110 };
  const p = placeHover(anchor, { width: 240, height: 130 }, { left: 0, top: 0, right: 375, bottom: 650 }, { x: 360, y: 30 });
  assert.ok(p && !overlaps(p, anchor));
  assert.equal(placeHover(anchor, { width: 240, height: 130 }, { left: 0, top: 0, right: 200, bottom: 100 }, { x: 360, y: 30 }), null);
});
test("component details track provisioning, readiness, faults and installed fixes", () => {
  let s = initialState();
  assert.equal(componentSnapshot(s, "cache", false).status, "Not built");
  s = transition(s, "begin-build", "cache");
  assert.equal(componentSnapshot(s, "cache", false).status, "Provisioning");
  s = transition(transition(s, "advance-build"), "finish-build");
  assert.equal(componentSnapshot(s, "cache", false).status, "Ready");
  s = { ...s, faults: { cache: "stale" } };
  assert.match(componentSnapshot(s, "cache", true).status, /Stale cache/);
  s = { ...s, protections: ["cache:stale"] };
  assert.match(componentSnapshot(s, "cache", true).status, /Mitigated/);
});
test("details reflect traffic pauses, primary failure and shard migration", () => {
  const s = initialState();
  assert.match(componentSnapshot(s, "client", false).detail, /paused/i);
  assert.match(componentSnapshot(transition(s, "load", 30000), "client", true).detail, /30,000/);
  assert.equal(componentSnapshot(transition(s, "fail"), "db", true).status, "Unavailable");
  const migration = transition(transition(s, "shard"), "start-migration");
  assert.match(componentSnapshot(migration, "shard", true).detail, /paused/);
});

test("all component summaries contain usable values before and after provisioning", () => {
  let s = initialState();
  for (const action of ["cache", "replica", "scale", "shard"]) s = transition(s, action);
  for (const state of [initialState(), s]) {
    for (const id of ["client", "proxy", "api", "api2", "cache", "db", "replica", "shard", "green"]) {
      const snapshot = componentSnapshot(state, id, true);
      assert.equal(typeof snapshot.detail, "string", id);
      assert.doesNotMatch(snapshot.detail, /NaN|undefined/, id);
    }
  }
});

test("new shard details change from empty to owned records after migration", () => {
  let s = transition(initialState(), "shard");
  assert.equal(componentSnapshot(s, "shard", true, 1).status, "Empty shard");
  assert.match(componentSnapshot(s, "shard", true, 1).detail, /0 logical records/);
  for (const action of ["start-migration", "copy-buckets", "verify-buckets", "switch-ownership"]) s = transition(s, action);
  assert.equal(componentSnapshot(s, "shard", true, 1).status, "Owning data");
  assert.match(componentSnapshot(s, "shard", true, 1).detail, /500,000 logical records/);
});
