import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { initialState, transition, metrics } from '../prototype/model.mjs';

test('the simulator never requests a backend or imports live traffic control', async () => {
  const app = await readFile(new URL('../prototype/app.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(app, /real-traffic|fetch\(|\/lab\/traffic|initReal\(/);
});

test('one evolving system demonstrates overload, caching and API scaling', () => {
  let state = transition(initialState(), 'load', 30000);
  const baseline = metrics(state);
  assert.equal(baseline.completed, 12000);
  state = transition(state, 'cache');
  assert.equal(metrics(state).completed, 22000);
  assert.ok(metrics(state).dbDemand < baseline.dbDemand);
  state = transition(state, 'scale');
  assert.equal(metrics(state).completed, 30000);
  assert.equal(state.records, 1000000);
  assert.equal(state.rps, 30000);
});

test('the explanation follows the bottleneck as solutions are added', async () => {
  const { explain } = await import('../prototype/model.mjs');
  let state = transition(initialState(), 'load', 30000);
  assert.match(explain(state), /database/i);
  state = transition(state, 'cache');
  assert.match(explain(state), /API/);
  state = transition(state, 'scale');
  assert.match(explain(state), /headroom/i);
  state = transition(state, 'fail');
  assert.match(explain(state), /writes.*unavailable/i);
});
