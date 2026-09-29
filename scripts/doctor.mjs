import { spawnSync } from "node:child_process";
for (const [name, cmd, args] of [
  ["Node (required now)", "node", ["--version"]],
  ["Make", "make", ["--version"]],
  ["Docker (optional)", "docker", ["--version"]],
  ["Java (future phases)", "java", ["-version"]],
]) {
  const r = spawnSync(cmd, args, { encoding: "utf8" });
  process.stdout.write(
    `${name}: ${r.status === 0 ? (r.stdout || r.stderr).split("\n")[0] : "unavailable"}\n`,
  );
}
