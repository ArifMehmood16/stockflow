// Deliberately aggregate teaching sketch, not the planned discrete-event engine.
export const loadSteps = { min: 10000, max: 250000, interval: 10000 };
export const initialState = () => ({
  rps: loadSteps.min,
  records: 1000000,
  lesson: 0,
  owners: Array(16).fill(0),
  epoch: 1,
  migration: null,
  faults: {},
  protections: [],
  cache: false,
  replica: false,
  primaryDown: false,
  fenced: false,
  promoted: false,
  instances: 1,
  shards: 1,
  green: false,
  greenReady: false,
  build: null,
});
export function transition(state, action, value) {
  const s = { ...state };
  switch (action) {
    case "begin-build": {
      const eligible = {
        cache: !s.cache,
        replica: !s.replica && !s.primaryDown,
        shard: s.shards < 6 && !s.primaryDown && !s.migration,
        scale: s.instances < 3,
        "prepare-green": !s.greenReady,
      };
      if (!s.build && Object.hasOwn(eligible, value) && eligible[value])
        s.build = { action: value, stage: "provisioning" };
      break;
    }
    case "advance-build":
      if (s.build) s.build = { ...s.build, stage: "checking" };
      break;
    case "finish-build":
      if (s.build?.stage === "checking") {
        const ready = { ...s, build: null };
        if (s.build.action === "prepare-green")
          return { ...ready, greenReady: true };
        return transition(ready, s.build.action);
      }
      break;
    case "load":
      s.rps = Number.isFinite(Number(value))
        ? Math.max(loadSteps.min, Math.min(loadSteps.max,
            Math.round(Number(value) / loadSteps.interval) * loadSteps.interval))
        : s.rps;
      break;
    case "cache":
      s.cache = !s.cache;
      break;
    case "replica":
      s.replica = true;
      break;
    case "fail":
      s.primaryDown = true;
      s.fenced = false;
      s.promoted = false;
      break;
    case "fence":
      if (s.primaryDown) s.fenced = true;
      break;
    case "promote":
      if (s.primaryDown && s.fenced && s.replica) {
        s.primaryDown = false;
        s.promoted = true;
        s.replica = false;
      }
      break;
    case "scale":
      s.instances = Math.min(3, s.instances + 1);
      break;
    case "shard":
      if (!s.migration && !s.primaryDown) s.shards = Math.min(6, s.shards + 1);
      break;
    case "deploy":
      if (s.green || (s.greenReady && !s.faults.green)) s.green = !s.green;
      break;
    case "lesson":
      if (Number.isInteger(value) && value >= 0 && value < 6) s.lesson = value;
      break;
    case "start-migration":
      if (s.shards > 1 && !s.migration && !s.primaryDown && !s.build)
        s.migration = {
          stage: "paused",
          target: s.owners.map((_, i) => i % s.shards),
        };
      break;
    case "copy-buckets":
      if (s.migration?.stage === "paused" && !s.primaryDown)
        s.migration = { ...s.migration, stage: "copied" };
      break;
    case "verify-buckets":
      if (s.migration?.stage === "copied" && !s.primaryDown)
        s.migration = { ...s.migration, stage: "verified" };
      break;
    case "switch-ownership":
      if (s.migration?.stage === "verified" && !s.primaryDown) {
        s.owners = [...s.migration.target];
        s.epoch++;
        s.migration = null;
      }
      break;
    case "cancel-migration":
      s.migration = null;
      break;
    case "inject":
      if (
        faultCatalog[value?.node]?.[value?.kind] &&
        componentAvailable(s, value.node)
      )
        s.faults = { ...s.faults, [value.node]: value.kind };
      break;
    case "mitigate": {
      const kind = s.faults[value];
      if (kind && !s.protections.includes(`${value}:${kind}`))
        s.protections = [...s.protections, `${value}:${kind}`];
      // A failed deployment can be isolated by rolling back, not by declaring it healthy.
      if (value === "green" && kind) s.green = false;
      break;
    }
    case "recover":
      s.faults = { ...s.faults };
      delete s.faults[value];
      break;
    case "restart-primary":
      // Recovery is a teaching state; production requires WAL/ledger verification.
      if (s.primaryDown && !s.promoted) {
        s.primaryDown = false;
        s.fenced = false;
      }
      break;
    case "reset":
      return initialState();
  }
  return s;
}
// These constants illustrate cause and effect, never hardware benchmarks.
export const faultCatalog = {
  cache: {
    stampede: {
      label: "Cache stampede",
      fix: "Coalesce + jitter",
      effect:
        "Synchronized expiry multiplies origin work by 4; assumed hits fall to 5%.",
      result:
        "Single-flight bounds duplicate fills; jitter spreads expiry. Warm-hit assumption resumes. Distributed lock leases need owner tokens and expiry.",
    },
    stale: {
      label: "Stale cache",
      fix: "Version guard",
      effect:
        "An older fill wins a write race: 20% of cache-served reads are marked stale.",
      result:
        "Model blocks older versions and invalidates on write. Strict reservation checks always read the primary; TTL alone cannot ensure freshness.",
    },
    penetration: {
      label: "Cache penetration",
      fix: "Negative cache",
      effect:
        "30% of reads request nonexistent IDs and repeatedly reach the database.",
      result:
        "Short-lived negative entries absorb 90% of repeated misses. New-product writes invalidate them; random unique IDs still need admission control.",
    },
    hotkey: {
      label: "Hot key",
      fix: "Bounded L1 copies",
      effect: "One popular product saturates the cache path at 3,000 reads/s.",
      result:
        "Read-only local copies raise the assumed ceiling to 60,000 reads/s. Version invalidation and a staleness bound remain necessary. Hot writes still serialize.",
    },
    crash: {
      label: "Redis crash",
      fix: "Bounded fallback",
      effect:
        "All cache hits disappear. Origin demand jumps while Redis stays unavailable.",
      result:
        "A breaker and bulkhead admit at most 6,000 origin ops/s; excess is rejected. Redis remains down until Recover, which assumes a warm-up completed.",
    },
  },
  db: {
    slow: {
      label: "Missing index",
      fix: "Add selective index",
      effect:
        "Illustrative query capacity falls to a quarter; the request queue would grow.",
      result:
        "The teaching query regains baseline capacity. Real verification needs EXPLAIN, selectivity, index size and write-amplification evidence.",
    },
  },
  api: {
    crash: {
      label: "Instance crash",
      fix: "Health routing",
      effect:
        "One API process is unavailable. Surviving instances provide capacity.",
      result:
        "Router excludes the failed process. It cannot restore the lost capacity; build another instance or Recover.",
    },
  },
  proxy: {
    retries: {
      label: "Retry storm",
      fix: "Retry budget",
      effect: "Unbounded retries double downstream attempts in this sketch.",
      result:
        "Deadlines, jitter and a retry budget remove amplification. Writes require idempotency and reconciliation of unknown outcomes.",
    },
  },
  replica: {
    lag: {
      label: "Replica lag",
      fix: "Pin fresh reads",
      effect: "20% of replica reads carry an old version in this scenario.",
      result:
        "Freshness-sensitive reads return to primary in this conservative model. Demand rises there; lag itself is not repaired.",
    },
    crash: {
      label: "Replica crash",
      fix: "Primary fallback",
      effect: "The read endpoint is unavailable; routed reads fail.",
      result:
        "Eligible reads move to the primary, consuming its spare capacity. Recover represents reseeding and catch-up, not just process restart.",
    },
  },
  shard: {
    routing: {
      label: "Naive modulo resize",
      fix: "Versioned bucket map",
      effect:
        "Changing hash(key) % N before moving records sends a fraction of requests to the wrong owner.",
      result:
        "Restore the stable 16-bucket ownership map. Add an empty node, pause/copy/verify, then atomically switch its ownership epoch.",
    },
    skew: {
      label: "Hot tenant",
      fix: "Isolate + admit",
      effect:
        "80% of demand goes to one tenant on shard 1. Extra shards cannot split that tenant.",
      result:
        "Admission isolation protects cold tenants. The hot tenant is still limited by one owner; a finer domain partition is a separate design decision.",
    },
    crash: {
      label: "Shard crash",
      fix: "Isolate shard",
      effect:
        "The last populated shard is unavailable. Its owners cannot serve requests.",
      result:
        "Fail affected tenants quickly and preserve healthy routes. Recover represents verified restore; no replica or backup has actually run.",
    },
  },
  green: {
    bad: {
      label: "Bad release",
      fix: "Roll back",
      effect:
        "Green fails requests if serving and cannot pass the route-switch gate.",
      result:
        "Traffic returns to blue. The faulty green stays blocked until Recover represents a corrected, healthy release. Schema compatibility is required.",
    },
  },
};
export function componentAvailable(s, node) {
  return node === "cache"
    ? s.cache
    : node === "replica"
      ? s.replica
      : node === "shard"
        ? s.shards > 1
        : node === "green"
          ? s.greenReady
          : ["api", "proxy", "db"].includes(node);
}
export const shardRecords = (s) =>
  Array.from(
    { length: s.shards },
    (_, id) =>
      (s.owners.filter((owner) => owner === id).length * s.records) / 16,
  );
export function metrics(s) {
  const has = (node, kind) => s.faults[node] === kind;
  const fixed = (node, kind) => s.protections.includes(`${node}:${kind}`);
  const unhandled = (node, kind) => has(node, kind) && !fixed(node, kind);
  const reads = s.rps * 0.9,
    writes = s.rps * 0.1;
  const cacheAvailable = s.cache && !has("cache", "crash");
  const hitRatio = cacheAvailable
    ? unhandled("cache", "stampede")
      ? 0.05
      : 0.8
    : 0;
  const invalidReads = has("cache", "penetration") ? reads * 0.3 : 0;
  const cacheReads = (reads - invalidReads) * hitRatio;
  const negativeHits =
    cacheAvailable && fixed("cache", "penetration") ? invalidReads * 0.9 : 0;
  const misses = reads - cacheReads - negativeHits;
  const replicaAvailable =
    s.replica &&
    !has("replica", "crash") &&
    !(has("replica", "lag") && fixed("replica", "lag"));
  const primaryReadDemand = replicaAvailable ? 0 : misses;
  const amplification =
    (unhandled("cache", "stampede") ? 4 : 1) *
    (unhandled("proxy", "retries") ? 2 : 1);
  const rawDbDemand = primaryReadDemand * amplification + writes;
  const bounded = has("cache", "crash") && fixed("cache", "crash");
  const dbDemand = bounded ? Math.min(rawDbDemand, 6000) : rawDbDemand;
  const shares = shardRecords(s).map((n) => n / s.records);
  const skew = has("shard", "skew");
  const dbCapacity =
    (unhandled("db", "slow") ? 3000 : 12000) /
    (skew ? Math.max(0.8, shares[0]) : Math.max(...shares));
  const apiCapacity =
    22000 * Math.max(0, s.instances - (has("api", "crash") ? 1 : 0));
  const cacheCapacity = has("cache", "hotkey")
    ? fixed("cache", "hotkey")
      ? 60000
      : 3000
    : 1000000;
  const factor = Math.min(
    1,
    apiCapacity / s.rps,
    (bounded ? Math.min(dbCapacity, 6000) : dbCapacity) /
      Math.max(rawDbDemand, 1),
    replicaAvailable ? 12000 / Math.max(misses, 1) : 1,
    cacheCapacity / Math.max(cacheReads, 1),
  );
  let completed = s.primaryDown
    ? Math.min(
        apiCapacity,
        cacheReads +
          negativeHits +
          (replicaAvailable ? Math.min(misses, 12000) : 0),
      )
    : s.rps * factor;
  if (unhandled("replica", "crash"))
    completed = Math.max(0, completed - misses * factor);
  if (unhandled("api", "crash"))
    completed *= Math.max(0, (s.instances - 1) / s.instances);
  if (unhandled("shard", "routing")) completed *= 1 / s.shards;
  if (has("shard", "crash")) completed *= 1 - shares.findLast((n) => n > 0);
  // Writes to moving buckets pause; reads still use the old owner until switch.
  const movingShare = s.migration
    ? s.owners.filter((owner, i) => owner !== s.migration.target[i]).length / 16
    : 0;
  completed = Math.max(
    0,
    completed - Math.min(completed, writes * movingShare * factor),
  );
  if (s.green && has("green", "bad")) completed = 0;
  const staleReads = Math.min(
    completed,
    (unhandled("cache", "stale") ? cacheReads * 0.2 : 0) +
      (unhandled("replica", "lag") ? misses * 0.2 : 0),
  );
  return {
    reads,
    writes,
    hitRatio,
    cacheAvailable,
    primaryReadDemand,
    rawDbDemand,
    dbDemand,
    dbCapacity,
    apiCapacity,
    completed,
    rejected: s.rps - completed,
    writeCapacity: dbCapacity,
    writeAvailable: !s.primaryDown,
    pressure: dbDemand / dbCapacity,
    cacheReads,
    staleReads,
    movingShare,
  };
}

// Explain the aggregate model rather than presenting its numbers as a benchmark.
export function explain(state) {
  const m = metrics(state);
  if (state.primaryDown) return 'Primary database unavailable: writes are unavailable. Fence it before promoting a replica; cached reads may continue.';
  const activeFault = Object.entries(state.faults)[0];
  if (activeFault) {
    const [node, kind] = activeFault;
    const scenario = faultCatalog[node][kind];
    return `${scenario.label}: ${state.protections.includes(`${node}:${kind}`) ? scenario.result : scenario.effect}`;
  }
  if (m.rejected < 1) return 'Headroom available: the modeled system can handle this load. Increase requests to find the next limit.';
  if (m.apiCapacity / state.rps < m.dbCapacity / Math.max(m.rawDbDemand, 1))
    return 'API capacity is the next limit. Another API shares requests, but does not increase the capacity of the shared database.';
  if (state.replica && m.rawDbDemand <= m.dbCapacity)
    return 'The read replica is saturated. Splitting reads does not create unlimited read capacity or more primary write capacity.';
  return 'The database cannot serve all requested work. Caching removes repeated reads; a replica moves reads; balanced shards spread data ownership.';
}
