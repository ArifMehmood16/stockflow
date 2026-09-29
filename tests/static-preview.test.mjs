import test from 'node:test';
import assert from 'node:assert/strict';
import { createPreview } from '../scripts/serve.mjs';

test('preview serves only static simulator assets, never APIs or local secrets', async () => {
  const server = createPreview();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(base)).status, 200);
    assert.match((await fetch(`${base}/model.mjs`)).headers.get('content-type'), /javascript/);
    assert.equal((await fetch(`${base}/favicon.svg`)).status, 200);
    for (const path of ['/lab/traffic', '/.env', '/.lab/runs', '/%2e%2e/.env'])
      assert.equal((await fetch(base + path)).status, 404);
    assert.equal((await fetch(base, { method: 'POST' })).status, 405);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
