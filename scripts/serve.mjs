import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../prototype/", import.meta.url));
const port = Number(process.env.PORT || 4173);
const host = process.env.HOST || "127.0.0.1";
const files = new Map([
  ["/", ["index.html", "text/html; charset=utf-8"]],
  ["/index.html", ["index.html", "text/html; charset=utf-8"]],
  ["/styles.css", ["styles.css", "text/css; charset=utf-8"]],
  ["/app.mjs", ["app.mjs", "text/javascript; charset=utf-8"]],
  ["/model.mjs", ["model.mjs", "text/javascript; charset=utf-8"]],
]);
if (!Number.isInteger(port) || port < 1024 || port > 65535)
  throw new Error("PORT must be 1024–65535");
const server = createServer(async (req, res) => {
  if (req.method !== "GET" && req.method !== "HEAD") {
    res.writeHead(405, { Allow: "GET, HEAD" });
    res.end();
    return;
  }
  const route = files.get((req.url || "/").split("?")[0]);
  if (!route) {
    res.writeHead(404);
    res.end("Not found");
    return;
  }
  try {
    const body = await readFile(root + route[0]);
    res.writeHead(200, {
      "Content-Type": route[1],
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy":
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; frame-ancestors 'none'; base-uri 'none'",
    });
    res.end(req.method === "HEAD" ? undefined : body);
  } catch {
    res.writeHead(500);
    res.end("Preview unavailable");
  }
});
server.on("error", (error) => {
  if (error.code !== "EADDRINUSE") throw error;
  const alternativePort = port === 65535 ? 4173 : port + 1;
  process.stderr.write(
    `Port ${port} is already in use on ${host}.\n` +
      `If StockFlow is already running, open http://${host}:${port}.\n` +
      `Otherwise stop that process in its terminal, or use: PORT=${alternativePort} make run\n`,
  );
  process.exitCode = 1;
});
server.listen(port, host, () =>
  process.stdout.write(`StockFlow design prototype: http://${host}:${port}\n`),
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () => server.close(() => process.exit(0)));
