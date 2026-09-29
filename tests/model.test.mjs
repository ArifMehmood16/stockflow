import test from "node:test";
import assert from "node:assert/strict";
import {
  initialState,
  transition,
  metrics,
  shardRecords,
  faultCatalog,
} from "../prototype/model.mjs";

test("high load exposes a bottleneck and caching reduces database read demand", () => {
  const s = transition(initialState(), "load", 30000);
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
  assert.equal(transition(initialState(), "load", 999999).rps, 250000);
  assert.equal(transition(initialState(), "load", -5).rps, 1000);
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
    const s = transition(transition(initialState(), "load", 50000), action);
    const m = metrics(s);
    assert.ok(m.completed >= 0);
    assert.ok(Math.abs(m.completed + m.rejected - s.rps) < 0.001);
  }
});

test("a component has no routing or capacity effect until its build completes", () => {
  const baseline = transition(initialState(), "load", 30000);
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

test("changing lessons keeps the million-record system, load and in-flight build", () => {
  let s = transition(initialState(), "cache");
  s = transition(s, "load", 100000);
  s = transition(s, "begin-build", "replica");
  const next = transition(s, "lesson", 4);
  assert.equal(next.lesson, 4);
  assert.equal(next.records, 1000000);
  assert.deepEqual({ ...next, lesson: s.lesson }, s);
});

test("new shards are empty until verified ownership transfer; repeated expansion conserves records", () => {
  let s = initialState();
  for (let count = 2; count <= 6; count++) {
    const capacity = metrics(s).dbCapacity;
    s = transition(s, "shard");
    assert.equal(s.shards, count);
    assert.equal(shardRecords(s).at(-1), 0);
    assert.equal(metrics(s).dbCapacity, capacity);
    assert.deepEqual(transition(s, "switch-ownership"), s);
    for (const action of [
      "start-migration",
      "copy-buckets",
      "verify-buckets",
    ]) {
      s = transition(s, action);
      assert.equal(shardRecords(s).at(-1), 0);
    }
    s = transition(s, "switch-ownership");
    assert.ok(shardRecords(s).every((n) => n > 0));
    assert.equal(
      shardRecords(s).reduce((a, b) => a + b, 0),
      1000000,
    );
    assert.equal(s.epoch, count);
  }
  assert.equal(transition(s, "shard").shards, 6);
});

test("interrupted migration leaves original ownership intact", () => {
  let s = transition(initialState(), "shard");
  s = transition(s, "start-migration");
  s = transition(s, "copy-buckets");
  const before = s.owners;
  s = transition(s, "cancel-migration");
  assert.deepEqual(s.owners, before);
  assert.equal(s.migration, null);
});

test("cache faults show distinct effects and fixes stay installed across experiments", () => {
  const baseline = transition(
    transition(initialState(), "cache"),
    "load",
    50000,
  );
  for (const kind of ["stampede", "stale", "penetration", "hotkey", "crash"]) {
    const fault = transition(baseline, "inject", { node: "cache", kind });
    assert.equal(fault.faults.cache, kind);
    const fixed = transition(fault, "mitigate", "cache");
    assert.ok(fixed.protections.includes(`cache:${kind}`));
    const before = metrics(fault),
      after = metrics(fixed);
    if (kind === "stale") assert.ok(before.staleReads > after.staleReads);
    if (["stampede", "penetration"].includes(kind))
      assert.ok(before.dbDemand > after.dbDemand);
    if (kind === "hotkey") assert.ok(after.completed > before.completed);
    if (kind === "crash") {
      assert.equal(after.cacheAvailable, false);
      assert.ok(after.dbDemand < before.dbDemand);
      assert.ok(after.rejected > 0);
    }
    const recovered = transition(fixed, "recover", "cache");
    assert.equal(recovered.faults.cache, undefined);
    assert.ok(recovered.protections.includes(`cache:${kind}`));
  }
});

test("faults are allowlisted and unavailable components cannot fail", () => {
  assert.deepEqual(
    transition(initialState(), "inject", { node: "cache", kind: "crash" }),
    initialState(),
  );
  assert.deepEqual(
    transition(initialState(), "inject", { node: "db", kind: "shell" }),
    initialState(),
  );
});

test("faults across layers conserve requests; mitigations are not magical recovery", () => {
  let s = transition(transition(initialState(), "cache"), "replica");
  s = transition(s, "load", 200000);
  for (const node of Object.keys(faultCatalog)) {
    for (const kind of Object.keys(faultCatalog[node])) {
      const fault = transition(s, "inject", { node, kind });
      for (const version of [fault, transition(fault, "mitigate", node)]) {
        const m = metrics(version);
        assert.ok(m.completed >= 0 && m.completed <= s.rps);
        assert.ok(Math.abs(m.completed + m.rejected - s.rps) < 0.001);
        assert.ok(m.staleReads <= m.completed);
      }
    }
  }
});
