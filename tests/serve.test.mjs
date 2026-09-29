import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath } from "node:url";

test(
  "a busy port gives actionable guidance without stopping the existing listener",
  { timeout: 5000 },
  async (t) => {
    const existing = createServer((request, response) =>
      response.end("existing service"),
    );
    t.after(() => new Promise((resolve) => existing.close(resolve)));
    existing.listen(0, "127.0.0.1");
    await once(existing, "listening");
    const port = existing.address().port;
    const child = spawn(
      process.execPath,
      [fileURLToPath(new URL("../scripts/serve.mjs", import.meta.url))],
      {
        env: { ...process.env, HOST: "127.0.0.1", PORT: String(port) },
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
    t.after(() => {
      if (child.exitCode === null) child.kill();
    });
    let error = "";
    child.stderr.setEncoding("utf8").on("data", (chunk) => {
      error += chunk;
    });
    const [code] = await once(child, "close");
    assert.equal(code, 1);
    assert.match(error, new RegExp(`Port ${port} is already in use`));
    assert.match(error, /PORT=\d+ make run/);
    assert.doesNotMatch(error, /Unhandled 'error' event|node:events|at Server/);
    assert.equal(
      await (await fetch(`http://127.0.0.1:${port}`)).text(),
      "existing service",
    );
  },
);
