import test from 'node:test';
import assert from 'node:assert/strict';
import { initialState, transition, metrics } from '../prototype/model.mjs';

test('high load exposes a bottleneck and caching reduces database read demand', () => {
  const s = transition(initialState(), 'load', 300);
  const before = metrics(s);
  const after = metrics(transition(s, 'cache'));
  assert.ok(before.dbDemand > before.dbCapacity);
  assert.ok(after.dbDemand < before.dbDemand);
  assert.ok(after.completed > before.completed);
});
test('a failed primary rejects writes; only a replica can be promoted after fencing', () => {
  let s = transition(initialState(), 'fail');
  assert.equal(metrics(s).writeAvailable, false);
  assert.equal(transition(s, 'promote').primaryDown, true);
  s = transition(s, 'replica');
  assert.equal(transition(s, 'promote').primaryDown, true);
  s = transition(s, 'fence');
  s = transition(s, 'promote');
  assert.equal(metrics(s).writeAvailable, true);
  assert.equal(s.promoted, true);
});
test('reset restores the baseline and offered load is bounded', () => {
  assert.equal(transition(initialState(), 'load', 99999).rps, 500);
  assert.equal(transition(initialState(), 'load', -5).rps, 10);
  assert.deepEqual(transition(transition(initialState(), 'cache'), 'reset'), initialState());
});
test('replicas reduce reads on primary but never increase primary write capacity', () => {
  const s = transition(initialState(), 'replica');
  assert.equal(metrics(s).writeCapacity, metrics(initialState()).writeCapacity);
  assert.ok(metrics(s).primaryReadDemand < metrics(initialState()).primaryReadDemand);
});
test('model conserves offered requests across completed and rejected categories', () => {
  for (const action of ['cache','replica','fail','shard','scale','deploy']) {
    const s = transition(transition(initialState(), 'load', 500), action);
    const m = metrics(s);
    assert.ok(m.completed >= 0);
    assert.ok(Math.abs(m.completed + m.rejected - s.rps) < 0.001);
  }
});
