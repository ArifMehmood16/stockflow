// Deliberately aggregate teaching sketch, not the planned discrete-event engine.
export const initialState = () => ({rps:80,cache:false,replica:false,primaryDown:false,fenced:false,promoted:false,instances:1,shards:1,green:false});
export function transition(state, action, value) {
  const s = {...state};
  switch (action) {
    case 'load': s.rps = Number.isFinite(Number(value)) ? Math.max(10, Math.min(500, Number(value))) : s.rps; break;
    case 'cache': s.cache = !s.cache; break;
    case 'replica': s.replica = true; break;
    case 'fail': s.primaryDown = true; s.fenced = false; s.promoted = false; break;
    case 'fence': if (s.primaryDown) s.fenced = true; break;
    case 'promote':
      if (s.primaryDown && s.fenced && s.replica) {s.primaryDown = false; s.promoted = true; s.replica = false;}
      break;
    case 'scale': s.instances = Math.min(3, s.instances + 1); break;
    case 'shard': s.shards = 2; break;
    case 'deploy': s.green = !s.green; break;
    case 'reset': return initialState();
  }
  return s;
}
export function metrics(s) {
  const reads = s.rps * .9;
  const writes = s.rps * .1;
  const hitRatio = s.cache ? .8 : 0; // Assumed warm cache, not measured hits.
  const misses = reads * (1 - hitRatio);
  const primaryReadDemand = s.replica ? 0 : misses;
  const dbDemand = primaryReadDemand + writes;
  const dbCapacity = 120 * s.shards; // Assumes balanced independent shards.
  const apiCapacity = 220 * s.instances;
  const factor = Math.min(1, apiCapacity / s.rps, dbCapacity / dbDemand, s.replica ? 120 / Math.max(misses, 1) : 1);
  const completed = s.primaryDown
    ? Math.min(apiCapacity, reads * hitRatio + (s.replica ? Math.min(misses, 120) : 0))
    : s.rps * factor;
  return {reads,writes,hitRatio,primaryReadDemand,dbDemand,dbCapacity,apiCapacity,completed,
    rejected:s.rps-completed,writeCapacity:dbCapacity,writeAvailable:!s.primaryDown,
    pressure:dbDemand/dbCapacity,cacheReads:reads*hitRatio};
}
