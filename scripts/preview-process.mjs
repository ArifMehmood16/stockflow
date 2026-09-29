import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile, unlink } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
const defaultDir = fileURLToPath(new URL("../.lab/", import.meta.url));
function fileFor(port, dir) {
  if (!Number.isInteger(port) || port < 1024 || port > 65535)
    throw new Error("Invalid preview port");
  return join(dir, `preview-${port}.json`);
}
function identity(pid) {
  if (!Number.isSafeInteger(pid) || pid <= 1) return null;
  try {
    // Match both process start time and command; a reused PID is not ownership.
    return (
      execFileSync(
        "ps",
        ["-ww", "-p", String(pid), "-o", "lstart=", "-o", "command="],
        { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] },
      ).trim() || null
    );
  } catch (error) {
    if (error.status === 1) return null;
    throw error;
  }
}
export async function registerPreview(
  port,
  pid = process.pid,
  dir = defaultDir,
) {
  const processIdentity = identity(pid);
  if (!processIdentity) throw new Error("Cannot register preview process");
  await mkdir(dir, { recursive: true, mode: 0o700 });
  await writeFile(
    fileFor(port, dir),
    JSON.stringify({
      kind: "stockflow-preview",
      pid,
      identity: processIdentity,
    }),
    { mode: 0o600 },
  );
}
export async function unregisterPreview(
  port,
  pid = process.pid,
  dir = defaultDir,
) {
  try {
    const file = fileFor(port, dir);
    const record = JSON.parse(await readFile(file, "utf8"));
    if (record.kind === "stockflow-preview" && record.pid === pid)
      await unlink(file);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
}
export async function stopPreview(port, dir = defaultDir) {
  let record;
  try {
    record = JSON.parse(await readFile(fileFor(port, dir), "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return "not-running";
    throw error;
  }
  if (
    record.kind !== "stockflow-preview" ||
    !Number.isSafeInteger(record.pid) ||
    record.pid <= 1 ||
    typeof record.identity !== "string"
  )
    return "identity-mismatch";
  const current = identity(record.pid);
  if (!current) {
    await unregisterPreview(port, record.pid, dir);
    return "not-running";
  }
  if (current !== record.identity) return "identity-mismatch";
  try {
    process.kill(record.pid, "SIGTERM");
  } catch (error) {
    if (error.code !== "ESRCH") throw error;
  }
  for (let attempt = 0; attempt < 30; attempt++) {
    if (identity(record.pid) !== record.identity) {
      await unregisterPreview(port, record.pid, dir);
      return "stopped";
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return "still-running";
}
