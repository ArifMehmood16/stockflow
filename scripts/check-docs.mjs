import { readdir, readFile, stat } from "node:fs/promises";
import { resolve, dirname } from "node:path";
const root = process.cwd();
let checked = 0;
const errors = [];
async function walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    if (entry.name.startsWith(".") || entry.name === "node_modules") continue;
    const path = resolve(dir, entry.name);
    if (entry.isDirectory()) await walk(path);
    else if (entry.name.endsWith(".md")) {
      const source = await readFile(path, "utf8");
      for (const match of source.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
        const target = match[1].split("#")[0];
        if (!target || /^(https?:|mailto:)/.test(target)) continue;
        checked++;
        try {
          await stat(resolve(dirname(path), decodeURI(target)));
        } catch {
          errors.push(`${path}: missing ${target}`);
        }
      }
    }
  }
}
await walk(root);
if (errors.length) {
  process.stderr.write(errors.join("\n") + "\n");
  process.exitCode = 1;
} else process.stdout.write(`Checked ${checked} local documentation links.\n`);
