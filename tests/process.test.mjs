import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, writeFile, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { registerPreview, stopPreview } from "../scripts/preview-process.mjs";

test(
  "stop ends only the registered process and is harmless when repeated",
  { timeout: 5000 },
  async (t) => {
    const dir = await mkdtemp(join(tmpdir(), "stockflow-stop-"));
    const child = spawn(
      process.execPath,
      ["-e", "setInterval(() => {}, 1000)"],
      { stdio: "ignore" },
    );
    t.after(async () => {
      if (child.exitCode === null) child.kill();
      await rm(dir, { recursive: true, force: true });
    });
    await once(child, "spawn");
    await registerPreview(4173, child.pid, dir);
    const exit = once(child, "exit");
    assert.equal(await stopPreview(4173, dir), "stopped");
    const [, signal] = await exit;
    assert.equal(signal, "SIGTERM");
    assert.equal(await stopPreview(4173, dir), "not-running");
  },
);

test(
  "stop refuses stale process identity and leaves that process alive",
  { timeout: 5000 },
  async (t) => {
    const dir = await mkdtemp(join(tmpdir(), "stockflow-stop-"));
    const child = spawn(
      process.execPath,
      ["-e", "setInterval(() => {}, 1000)"],
      { stdio: "ignore" },
    );
    t.after(async () => {
      if (child.exitCode === null) child.kill();
      await rm(dir, { recursive: true, force: true });
    });
    await once(child, "spawn");
    await registerPreview(4173, child.pid, dir);
    const file = join(dir, "preview-4173.json");
    const record = JSON.parse(await readFile(file, "utf8"));
    record.identity = "stale process identity";
    await writeFile(file, JSON.stringify(record));
    assert.equal(await stopPreview(4173, dir), "identity-mismatch");
    assert.doesNotThrow(() => process.kill(child.pid, 0));
  },
);
