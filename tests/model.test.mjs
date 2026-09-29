import test from "node:test";
import assert from "node:assert/strict";
import { initialState, transition, metrics } from "../prototype/model.mjs";

test("high load exposes a bottleneck and caching reduces database read demand", () => {
  const s = transition(initialState(), "load", 300);
  const before = metrics(s);
  const after = metrics(transition(s, "cache"));
  assert.ok(before.dbDemand > before.dbCapacity);
  assert.ok(after.dbDemand < before.dbDemand);
  assert.ok(after.completed > before.completed);
});
test("a failed primary rejects writes; only a replica can be promoted after fencing", () => {
  let s = transition(initialState(), "fail");
  assert.equal(metrics(s).writeAvailable, false);
  assert.equal(transition(s, "promote").primaryDown, true);
  s = transition(s, "replica");
  assert.equal(transition(s, "promote").primaryDown, true);
  s = transition(s, "fence");
  s = transition(s, "promote");
  assert.equal(metrics(s).writeAvailable, true);
  assert.equal(s.promoted, true);
});
test("reset restores the baseline and offered load is bounded", () => {
  assert.equal(transition(initialState(), "load", 99999).rps, 500);
  assert.equal(transition(initialState(), "load", -5).rps, 10);
  assert.deepEqual(
    transition(transition(initialState(), "cache"), "reset"),
    initialState(),
  );
});
test("replicas reduce reads on primary but never increase primary write capacity", () => {
  const s = transition(initialState(), "replica");
  assert.equal(metrics(s).writeCapacity, metrics(initialState()).writeCapacity);
  assert.ok(
    metrics(s).primaryReadDemand < metrics(initialState()).primaryReadDemand,
  );
});
test("model conserves offered requests across completed and rejected categories", () => {
  for (const action of [
    "cache",
    "replica",
    "fail",
    "shard",
    "scale",
    "deploy",
  ]) {
    const s = transition(transition(initialState(), "load", 500), action);
    const m = metrics(s);
    assert.ok(m.completed >= 0);
    assert.ok(Math.abs(m.completed + m.rejected - s.rps) < 0.001);
  }
});

test("a component has no routing or capacity effect until its build completes", () => {
  const baseline = transition(initialState(), "load", 300);
  const building = transition(baseline, "begin-build", "cache");
  assert.equal(building.build?.action, "cache");
  assert.equal(building.cache, false);
  assert.equal(metrics(building).dbDemand, metrics(baseline).dbDemand);
  const checking = transition(building, "advance-build");
  assert.equal(checking.build?.stage, "checking");
  assert.equal(checking.cache, false);
  const ready = transition(checking, "finish-build");
  assert.equal(ready.build, null);
  assert.equal(ready.cache, true);
  assert.ok(metrics(ready).dbDemand < metrics(baseline).dbDemand);
});

test("builds serialize and reset discards a pending topology change", () => {
  const building = transition(initialState(), "begin-build", "replica");
  assert.deepEqual(transition(building, "begin-build", "shard"), building);
  assert.equal(transition(building, "finish-build").replica, false);
  const reset = transition(building, "reset");
  assert.equal(transition(reset, "finish-build").replica, false);
  assert.deepEqual(
    transition(initialState(), "begin-build", "arbitrary-command"),
    initialState(),
  );
});

test("green readiness precedes an explicit route switch", () => {
  let s = transition(initialState(), "deploy");
  assert.equal(s.green, false);
  s = transition(s, "begin-build", "prepare-green");
  s = transition(s, "advance-build");
  s = transition(s, "finish-build");
  assert.equal(s.greenReady, true);
  assert.equal(s.green, false);
  assert.equal(transition(s, "deploy").green, true);
});
