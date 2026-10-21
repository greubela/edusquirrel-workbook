#!/usr/bin/env node
import { existsSync, mkdirSync, rmSync, cpSync, readdirSync, statSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve, dirname, relative } from "node:path";

const ROOT = resolve(process.cwd());
const OUT = join(ROOT, "_site");

function log(msg) { console.log(`[assemble-site] ${msg}`); }

function copyDirIfExists(source, destination, label) {
  if (!existsSync(source)) return false;
  log(`copy ${label}`);
  cpSync(source, destination, { recursive: true });
  return true;
}

log(`output: ${OUT}`);
rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

log("copy homepage/ → _site/");
cpSync(join(ROOT, "homepage"), OUT, { recursive: true });

if (!existsSync(join(ROOT, "artifacts/newest/client.js"))) {
  throw new Error("Build the current client first: sbt buildClientDev");
}
// Preview flattens homepage/, so rewrite only the native client script path.
function rewriteClientPaths(folder) {
  for (const entry of readdirSync(folder, {withFileTypes:true})) {
    const file = join(folder, entry.name);
    if (entry.isDirectory()) rewriteClientPaths(file);
    else if (entry.name.endsWith('.html')) {
      const client = relative(dirname(file), join(OUT, 'artifacts/newest/client.js')).split('\\').join('/');
      writeFileSync(file, readFileSync(file, 'utf8').replaceAll('../../artifacts/newest/client.js', client));
    }
  }
}
rewriteClientPaths(OUT);

const resourcesDir = join(ROOT, "resources");
const resourcesOut = join(OUT, "resources");
copyDirIfExists(resourcesDir, resourcesOut, "resources/ → _site/resources/");

const artifactsDir = join(ROOT, "artifacts");
if (existsSync(artifactsDir)) {
  const artifactsOut = join(OUT, "artifacts");
  mkdirSync(artifactsOut, { recursive: true });
  copyDirIfExists(join(artifactsDir, "newest"), join(artifactsOut, "newest"), "artifacts/newest/ → _site/artifacts/newest/");
  copyDirIfExists(join(artifactsDir, "stable"), join(artifactsOut, "stable"), "artifacts/stable/ → _site/artifacts/stable/");
}

const favicon = join(ROOT, "favicon.ico");
if (existsSync(favicon)) {
  cpSync(favicon, join(OUT, "favicon.ico"));
}

log("done.");
