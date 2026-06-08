#!/usr/bin/env node
import { existsSync, mkdirSync, rmSync, cpSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";

const ROOT = resolve(process.cwd());
const OUT = join(ROOT, "_site");

function log(msg) { console.log(`[assemble-site] ${msg}`); }

function findBundle(start) {
  const target = join(start, "target");
  if (!existsSync(target)) return null;
  const stack = [target];
  let legacyDir = null;
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try { entries = readdirSync(dir); } catch { continue; }
    for (const name of entries) {
      const full = join(dir, name);
      let s;
      try { s = statSync(full); } catch { continue; }
      if (s.isDirectory()) {
        if (name === "workbookapp-fastopt") legacyDir = full;
        stack.push(full);
      } else if (name === "client-fastopt.js") {
        return { type: "file", path: full };
      }
    }
  }
  return legacyDir ? { type: "dir", path: legacyDir } : null;
}

log(`output: ${OUT}`);
mkdirSync(OUT, { recursive: true });
for (const name of (existsSync(OUT) ? readdirSync(OUT) : [])) {
  if (name === "resources") continue;
  rmSync(join(OUT, name), { recursive: true, force: true });
}

log("copy homepage/ → _site/");
cpSync(join(ROOT, "homepage"), OUT, { recursive: true });

const bundle = findBundle(ROOT);
if (!bundle) {
  console.error("[assemble-site] Could not find target/.../client-fastopt.js — run `npm run build` first.");
  process.exit(1);
}
const appOut = join(OUT, "js", "app");
mkdirSync(appOut, { recursive: true });
if (bundle.type === "file") {
  log(`copy ${bundle.path} → ${join(appOut, "main.js")}`);
  cpSync(bundle.path, join(appOut, "main.js"));
  const mapPath = `${bundle.path}.map`;
  if (existsSync(mapPath)) cpSync(mapPath, join(appOut, "main.js.map"));
} else {
  log(`copy ${bundle.path} → ${appOut}`);
  cpSync(bundle.path, appOut, { recursive: true });
}

const resourcesDir = join(ROOT, "resources");
const resourcesOut = join(OUT, "resources");
if (existsSync(resourcesDir) && !existsSync(resourcesOut)) {
  log("copy resources/ → _site/resources/");
  cpSync(resourcesDir, resourcesOut, { recursive: true });
} else if (existsSync(resourcesOut)) {
  log("skip resources/ (already present; rm -rf _site/resources to refresh)");
}

const favicon = join(ROOT, "favicon.ico");
if (existsSync(favicon)) {
  cpSync(favicon, join(OUT, "favicon.ico"));
}

log("done.");
