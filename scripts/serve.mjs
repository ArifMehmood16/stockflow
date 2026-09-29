import { createServer } from 'node:http';
import { readFile, writeFile, mkdir, rm } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../prototype/', import.meta.url));
const types = { '.html': 'text/html', '.mjs': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };
const pages = new Set(['index.html', 'app.mjs', 'model.mjs', 'hover.mjs', 'topology.mjs', 'styles.css', 'favicon.svg']);

export function createPreview() {
  return createServer(async (request, response) => {
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.writeHead(405, { Allow: 'GET, HEAD' }).end();
      return;
    }
    let file;
    try { file = decodeURIComponent(new URL(request.url, 'http://localhost').pathname).slice(1) || 'index.html'; }
    catch { response.writeHead(400).end(); return; }
    if (!pages.has(file) && !/^assets\/[a-z-]+\.svg$/.test(file)) {
      response.writeHead(404).end();
      return;
    }
    try {
      const body = await readFile(path.join(root, file));
      response.writeHead(200, {
        'Content-Type': `${types[path.extname(file)]}; charset=utf-8`,
        'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
        'Content-Security-Policy': "default-src 'self'; connect-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'",
      });
      response.end(request.method === 'HEAD' ? undefined : body);
    } catch { response.writeHead(404).end(); }
  });
}

function fingerprint(pid) {
  return execFileSync('ps', ['-p', String(pid), '-o', 'lstart=', '-o', 'command='], { encoding: 'utf8' }).trim();
}

async function main() {
  const port = Number(process.env.PORT || 4173);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('PORT must be 1024–65535.');
  const directory = fileURLToPath(new URL('../.preview/', import.meta.url));
  const registry = path.join(directory, `${port}.json`);
  if (process.argv[2] === 'stop') {
    let record;
    try { record = JSON.parse(await readFile(registry, 'utf8')); }
    catch { console.log('No registered static preview. Use Ctrl-C for an older preview.'); return; }
    let current;
    try { current = fingerprint(record.pid); } catch { current = ''; }
    if (!current || current !== record.fingerprint || !current.includes('scripts/serve.mjs')) {
      await rm(registry, { force: true });
      console.log('Stale preview record removed; no process stopped.');
      return;
    }
    process.kill(record.pid, 'SIGTERM');
    console.log(`Static preview on ${port} asked to stop.`);
    return;
  }
  const server = createPreview();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, process.env.HOST || '127.0.0.1', resolve);
  });
  if (process.env.STOCKFLOW_CONTAINER !== '1') {
    await mkdir(directory, { recursive: true, mode: 0o700 });
    await writeFile(registry, JSON.stringify({ pid: process.pid, fingerprint: fingerprint(process.pid) }), { mode: 0o600 });
  }
  const stop = () => {
    server.closeAllConnections();
    server.close(async () => {
      if (process.env.STOCKFLOW_CONTAINER !== '1') await rm(registry, { force: true });
      process.exit(0);
    });
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
  console.log(`Distributed Systems Simulator: http://127.0.0.1:${port} — static files only.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => {
    console.error(error.code === 'EADDRINUSE'
      ? 'Port occupied. Use the existing page, make stop, or PORT=4174 make run.' : error.message);
    process.exit(1);
  });
}
