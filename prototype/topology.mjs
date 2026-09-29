import { metrics, shardRecords } from "./model.mjs";

export const scene = { width: 1240, height: 660 };
const card = (x, y, width = 176) => ({ x, y, width, height: 120 });
export const nodes = {
  client: card(40, 270), proxy: card(310, 270), api: card(580, 270),
  green: card(310, 50), api2: card(580, 50), cache: card(1020, 50),
  db: card(1020, 270), replica: card(1020, 490), shard: card(440, 490, 386),
};

export function routes(state) {
  const m = metrics(state);
  const source = state.green ? "green" : "api";
  const replicaReads = state.replica && state.faults.replica !== "crash" &&
    !(state.faults.replica === "lag" && state.protections.includes("replica:lag"));
  const edge = (id, kind, from, to, path, active, text = "", x = 0, y = 0) =>
    ({ id: `edge-${id}`, kind, from, to, path, active, label: { text, x, y } });
  return [
    edge("client", "read", "client", "proxy", "M216 310 H310", true),
    edge("api", "read", "proxy", "api", "M486 310 H580", !state.green),
    edge("db", "write", source, "db", state.green ? "M310 80 H250 V210 H780 V292 H1020" : "M756 292 H1020", !state.primaryDown, "PRIMARY WRITE", 825, 282),
    edge("db-read", "read", source, "db", state.green ? "M486 100 H530 V240 H950 V326 H1020" : "M756 326 H1020", !state.primaryDown && !replicaReads, "PRIMARY READ", state.green ? 800 : 875, state.green ? 232 : 318),
    edge("cache", "read", source, "cache", state.green ? "M486 80 H520 V20 H950 V100 H1020" : "M668 270 V230 H900 V110 H1020", m.cacheAvailable, "CACHE GET / SET", 904, state.green ? 90 : 100),
    edge("replica", "replication", "db", "replica", "M1108 390 V490", state.replica && !state.primaryDown && state.faults.replica !== "crash", "ASYNC WAL", 1120, 443),
    edge("replica-read", "read", source, "replica", state.green ? "M486 130 H550 V440 H870 V520 H1020" : "M756 350 H870 V520 H1020", replicaReads, "REPLICA READ", 894, 510),
    edge("shard", "write", source, "shard", state.green ? "M310 150 H270 V450 H620 V490" : "M668 390 V490", !state.primaryDown && shardRecords(state).slice(1).some(n => n > 0), "SHARD WRITE", state.green ? 475 : 678, state.green ? 441 : 445),
    edge("api2", "read", "proxy", "api2", "M486 300 H530 V110 H580", state.instances > 1 && !state.green),
    edge("api2-db", "write", "api2", "db", "M756 110 H930 V292 H1020", state.instances > 1 && !state.green && !state.primaryDown, "PRIMARY WRITE", 777, 100),
    edge("green", "read", "proxy", "green", "M398 270 V170", state.green, "V2 ROUTE", 410, 204),
  ];
}

export function fitCamera(viewport) {
  const scale = Math.max(0.01, Math.min(1, (viewport.width - 24) / scene.width, (viewport.height - 24) / scene.height));
  return { scale, x: (viewport.width - scene.width * scale) / 2, y: (viewport.height - scene.height * scale) / 2 };
}
const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
export function panCamera(camera, dx, dy, viewport) {
  return { ...camera,
    x: clamp(camera.x + dx, 48 - scene.width * camera.scale, viewport.width - 48),
    y: clamp(camera.y + dy, 48 - scene.height * camera.scale, viewport.height - 48),
  };
}
export function zoomCamera(camera, factor, origin, viewport) {
  const scale = clamp(camera.scale * factor, 0.15, 2.5);
  return panCamera({ scale,
    x: origin.x - (origin.x - camera.x) * scale / camera.scale,
    y: origin.y - (origin.y - camera.y) * scale / camera.scale,
  }, 0, 0, viewport);
}
