#!/usr/bin/env node
// Assemble the GitHub Pages site locally, mirroring what .github/workflows/scala.yml does.
// Run with `npm run assemble` after `npm run build` (sbt fastOptJS).
import { existsSync, mkdirSync, rmSync, cpSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";

const ROOT = resolve(process.cwd());
const OUT = join(ROOT, "_site");

function log(msg) { console.log(`[assemble-site] ${msg}`); }

function findBundleDir(start) {
  // Walk target/ and look for a directory named workbookapp-fastopt.
  const target = join(start, "target");
  if (!existsSync(target)) return null;
  const stack = [target];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try { entries = readdirSync(dir); } catch { continue; }
    for (const name of entries) {
      const full = join(dir, name);
      let s;
      try { s = statSync(full); } catch { continue; }
      if (s.isDirectory()) {
        if (name === "workbookapp-fastopt") return full;
        stack.push(full);
      }
    }
  }
  return null;
}

log(`output: ${OUT}`);
rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

log("copy homepage/ → _site/");
cpSync(join(ROOT, "homepage"), OUT, { recursive: true });

const bundleDir = findBundleDir(ROOT);
if (!bundleDir) {
  console.error("[assemble-site] Could not find target/.../workbookapp-fastopt — run `npm run build` first.");
  process.exit(1);
}
const appOut = join(OUT, "js", "app");
mkdirSync(appOut, { recursive: true });
log(`copy ${bundleDir} → ${appOut}`);
cpSync(bundleDir, appOut, { recursive: true });

const resourcesDir = join(ROOT, "resources");
if (existsSync(resourcesDir)) {
  log("copy resources/ → _site/resources/");
  cpSync(resourcesDir, join(OUT, "resources"), { recursive: true });
}

const favicon = join(ROOT, "favicon.ico");
if (existsSync(favicon)) {
  cpSync(favicon, join(OUT, "favicon.ico"));
}

log("done.");
