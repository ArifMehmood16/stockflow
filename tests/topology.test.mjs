import test from "node:test";
import assert from "node:assert/strict";
import { routes, nodes, scene, fitCamera, zoomCamera, panCamera } from "../prototype/topology.mjs";
import { initialState, transition } from "../prototype/model.mjs";

test("fit leaves room for controls docked inside the canvas", () => {
  const fitted = fitCamera({ width: 1000, height: 600 }, { left: 174, right: 48 });
  assert.ok(fitted.x >= 174);
  assert.ok(fitted.x + scene.width * fitted.scale <= 1000 - 48);
});

test("replica receives read requests and WAL, never client writes, in blue and green", () => {
  for (const green of [false, true]) {
    const edges = routes({ ...initialState(), replica: true, green, greenReady: true });
    assert.deepEqual(edges.filter(e => e.to === "replica").map(e => e.kind).sort(), ["read", "replication"]);
    assert.ok(edges.filter(e => e.kind === "write").every(e => ["db", "shard"].includes(e.to)));
    assert.equal(edges.find(e => e.id === "edge-replica-read").label.text, "REPLICA READ");
    assert.equal(edges.find(e => e.id === "edge-replica").label.text, "ASYNC WAL");
  }
});
test("failed and lag-protected replicas route reads back to primary", () => {
  const base = { ...initialState(), replica: true };
  for (const state of [base, { ...base, faults: { replica: "crash" } }, { ...base, faults: { replica: "lag" }, protections: ["replica:lag"] }]) {
    const edges = routes(state);
    assert.equal(edges.find(e => e.id === "edge-db-read").active, state !== base);
    assert.equal(edges.find(e => e.id === "edge-replica-read").active, state === base);
  }
});
test("component gutters leave room for arrows and labels", () => {
  assert.ok(nodes.db.y - nodes.cache.y - nodes.cache.height >= 80);
  assert.ok(nodes.replica.y - nodes.db.y - nodes.db.height >= 80);
  assert.ok(nodes.db.x - nodes.api.x - nodes.api.width >= 200);
});
test("route segments never pass through an unrelated component", () => {
  for (const green of [false, true]) for (const route of routes({ ...initialState(), green })) {
    let point;
    for (const command of route.path.match(/[MHV][^MHV]*/g)) {
      const values = command.slice(1).trim().split(/\s+/).map(Number);
      const next = command[0] === "M" ? {x:values[0],y:values[1]}
        : command[0] === "H" ? {x:values[0],y:point.y} : {x:point.x,y:values[0]};
      if (point) for (const [id, box] of Object.entries(nodes)) {
        if ([route.from, route.to].includes(id)) continue;
        const crosses = point.x === next.x
          ? point.x > box.x && point.x < box.x+box.width && Math.max(point.y,next.y)>box.y && Math.min(point.y,next.y)<box.y+box.height
          : point.y > box.y && point.y < box.y+box.height && Math.max(point.x,next.x)>box.x && Math.min(point.x,next.x)<box.x+box.width;
        assert.equal(crosses, false, `${route.id} crosses ${id} in ${green ? "green" : "blue"}`);
      }
      point = next;
    }
  }
});
test("fit contains the whole scene and zoom keeps the pointed world location fixed", () => {
  for (const viewport of [{ width: 800, height: 340 }, { width: 346, height: 300 }]) {
    const camera = fitCamera(viewport);
    assert.ok(camera.x >= 0 && camera.y >= 0);
    assert.ok(camera.x + scene.width * camera.scale <= viewport.width);
    assert.ok(camera.y + scene.height * camera.scale <= viewport.height);
    const origin = { x: viewport.width / 2, y: viewport.height / 2 };
    const zoomed = zoomCamera(camera, 1.5, origin, viewport);
    assert.ok(Math.abs((origin.x-camera.x)/camera.scale - (origin.x-zoomed.x)/zoomed.scale) < 0.001);
    assert.ok(Math.abs((origin.y-camera.y)/camera.scale - (origin.y-zoomed.y)/zoomed.scale) < 0.001);
  }
});
test("pan is bounded, zoom has limits, and model additions do not mutate the camera", () => {
  const viewport = { width: 800, height: 340 };
  const camera = fitCamera(viewport), original = { ...camera };
  const panned = panCamera(camera, 99999, -99999, viewport);
  assert.ok(panned.x <= viewport.width - 48);
  assert.ok(panned.y + scene.height * camera.scale >= 48);
  assert.equal(zoomCamera(camera, 100, {x:400,y:170}, viewport).scale, 2.5);
  assert.equal(zoomCamera(camera, 0.001, {x:400,y:170}, viewport).scale, 0.15);
  routes(transition(initialState(), "shard"));
  assert.deepEqual(camera, original);
});
