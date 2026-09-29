import { metrics, faultCatalog, componentAvailable, shardRecords } from "./model.mjs";

const fmt = (n) => Math.round(n).toLocaleString("en-GB");

// Coordinates are viewport pixels, independent of the scaled architecture SVG.
export function placeHover(anchor, size, bounds, pointer) {
  const gap = 12;
  const minX = bounds.left + 8, maxX = bounds.right - size.width - 8;
  const minY = bounds.top + 8, maxY = bounds.bottom - size.height - 8;
  if (maxX < minX || maxY < minY) return null;
  const point = pointer || { x: anchor.right, y: anchor.top };
  const xs = [point.x + gap, point.x - size.width - gap,
    anchor.right + gap, anchor.left - size.width - gap, minX, maxX];
  const ys = [point.y + gap, point.y - size.height - gap,
    anchor.bottom + gap, anchor.top - size.height - gap, minY, maxY];
  const candidates = xs.flatMap(x => ys.map(y => {
    const left = Math.max(minX, Math.min(maxX, x));
    const top = Math.max(minY, Math.min(maxY, y));
    return { left, top, right: left + size.width, bottom: top + size.height };
  })).filter(p => !(p.left < anchor.right + 4 && p.right > anchor.left - 4 &&
    p.top < anchor.bottom + 4 && p.bottom > anchor.top - 4));
  // Prefer the closest corner to the cursor. Surrounding content may overlap;
  // the inspected card stays clear and the overlay never intercepts input.
  const distance = p => Math.min(...[p.left, p.right].flatMap(x =>
    [p.top, p.bottom].map(y => Math.hypot(x - point.x, y - point.y))));
  return candidates.sort((a, b) => distance(a) - distance(b))[0] || null;
}

export function componentSnapshot(state, id, running, shardIndex = null) {
  if (id === "shard" && Number.isInteger(shardIndex) && shardIndex < state.shards && shardIndex >= 0) {
    const counts = shardRecords(state);
    const unavailable = state.faults.shard === "crash" && shardIndex === counts.findLastIndex(n => n > 0);
    return {
      status: unavailable ? "Unavailable" : counts[shardIndex] ? "Owning data" : "Empty shard",
      detail: `${fmt(counts[shardIndex])} logical records · ${state.owners.filter(owner => owner === shardIndex).length} buckets · epoch ${state.epoch}. ${state.migration ? `Migration: ${state.migration.stage}; the old owner still serves reads.` : counts[shardIndex] ? "Requests follow the current bucket ownership map." : "Run Pause writes → Copy → Verify → Switch owners to move data here."}`,
    };
  }
  const buildAction = { api2: "scale" }[id] || id;
  if (state.build?.action === buildAction) return {
    status: state.build.stage === "provisioning" ? "Provisioning" : "Checking readiness",
    detail: "Existing routes stay active. Traffic joins only after readiness passes.",
  };
  const available = id === "client" ? true : id === "db" ? !state.primaryDown : id === "api2" ? state.instances > 1
    : id === "shard" ? state.shards > 1
    : componentAvailable(state, id);
  let status = available ? "Ready" : id === "db" ? "Unavailable" : "Not built";
  if (id === "db" && state.fenced) status = "Fenced";
  const fault = state.faults[id];
  if (fault) return {
    status: `${state.protections.includes(`${id}:${fault}`) ? "Mitigated" : "Fault"} · ${faultCatalog[id][fault].label}`,
    detail: state.protections.includes(`${id}:${fault}`) ? faultCatalog[id][fault].result : faultCatalog[id][fault].effect,
  };
  const m = metrics(state);
  const detail = {
    client: `${fmt(state.rps)} offered req/s configured · ${running ? "flow active" : "traffic paused"}.`,
    proxy: `Routes across ${state.instances} ready API instance(s).`,
    api: `${fmt(m.apiCapacity)} req/s combined API capacity.`,
    api2: `${state.instances} total API instances · shared database capacity is unchanged.`,
    cache: `${Math.round(m.hitRatio * 100)}% modeled hit rate · ${fmt(m.dbDemand)} ops/s continue to storage.`,
    db: state.primaryDown ? "Writes stopped. Fence before promotion; verify data after recovery."
      : `${fmt(m.dbDemand)} ops/s storage demand / ${fmt(m.dbCapacity)} modeled capacity.`,
    replica: state.promoted ? "Promoted to primary. This standby needs reseeding."
      : "Eventual reads follow asynchronous WAL. Writes still go to the primary.",
    shard: `${state.shards} shards · epoch ${state.epoch} · ${state.migration?.stage || "stable ownership"}. Added shards stay empty until ownership switches.`,
  }[id];
  return { status, detail: !available && id !== "db" && id !== "shard"
    ? "Build this component using the action below its card. It has no active route yet." : detail };
}
