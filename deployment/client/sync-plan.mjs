#!/usr/bin/env node
// Plans an incremental FTP upload of an assembled site for deploy-client.sh.
// The webspace keeps the manifest of the last successful deployment (sha256sum format);
// only files whose hash changed are uploaded, and only files that earlier deployments
// uploaded are deleted, so unrelated webspace content is never touched.
//
// node sync-plan.mjs <site-dir> <previous-manifest|-> <out-dir> <ftp-base-url> <remote-root> [--trust PREFIX]...
// Writes into <out-dir>: upload-NNN.curl batches with progress-NNN.sha256 manifests (the previous
// manifest plus every batch up to NNN), delete.curl, the final manifest.sha256 and summary.json.
import { createHash } from "node:crypto";
import { createReadStream, existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, posix, resolve } from "node:path";
import { pathToFileURL } from "node:url";

export const manifestName = ".deploy-manifest.sha256";
const batchSize = 500;

export function listFiles(root, folder = "") {
  return readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry => {
    const path = posix.join(folder, entry.name);
    if (entry.isDirectory()) return listFiles(root, path);
    if (!entry.isFile()) throw new Error(`Unsupported non-regular file: ${path}`);
    return path === manifestName ? [] : [path];
  });
}

function sha256(file) {
  return new Promise((resolve, reject) => {
    const hash = createHash("sha256");
    createReadStream(file).on("error", reject).on("data", chunk => hash.update(chunk))
      .on("end", () => resolve(hash.digest("hex")));
  });
}

export function parseManifest(text) {
  const entries = new Map();
  for (const line of text.split("\n")) {
    const match = /^([0-9a-f]{64}) {2}(.+)$/.exec(line);
    if (match) entries.set(match[2], match[1]);
  }
  return entries;
}

export function plan(current, previous, trusted = []) {
  const isTrusted = path => trusted.some(prefix => path === prefix || path.startsWith(prefix.replace(/\/?$/, "/")));
  const uploads = [...current].filter(([path, hash]) =>
    previous.has(path) ? previous.get(path) !== hash : !isTrusted(path)).map(([path]) => path);
  const deletes = [...previous.keys()].filter(path => !current.has(path));
  return { uploads, deletes };
}

// curl config strings use backslash escapes inside double quotes.
const quote = value => `"${value.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")}"`;
const encodePath = path => path.split("/").filter(Boolean).map(encodeURIComponent).join("/");

async function main([siteDir, previousFile, outDir, baseUrl, remoteRoot, ...options]) {
  if (!outDir || !baseUrl || remoteRoot === undefined) throw new Error("usage: see header of sync-plan.mjs");
  const trusted = options.flatMap((option, index) => option === "--trust" ? [options[index + 1]] : []);
  const current = new Map();
  for (const path of listFiles(siteDir).sort()) current.set(path, await sha256(join(siteDir, path)));
  const previous = previousFile !== "-" && existsSync(previousFile)
    ? parseManifest(readFileSync(previousFile, "utf8")) : new Map();
  const { uploads, deletes } = plan(current, previous, trusted);

  const root = remoteRoot.replace(/^\/+|\/+$/g, "");
  const url = path => `${baseUrl.replace(/\/+$/, "")}/${encodePath(posix.join(root, path))}`;
  const formatManifest = entries => [...entries].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
    .map(([path, hash]) => `${hash}  ${path}\n`).join("");
  writeFileSync(join(outDir, "manifest.sha256"), formatManifest(current));
  // Progress manifests keep old hashes for files not yet uploaded, so a rerun uploads them again.
  const pending = new Set(uploads);
  const progress = new Map([...previous, ...[...current].filter(([path]) => !pending.has(path))]);
  for (let start = 0; start < uploads.length; start += batchSize) {
    const batch = uploads.slice(start, start + batchSize);
    const number = String(start / batchSize + 1).padStart(3, "0");
    writeFileSync(join(outDir, `upload-${number}.curl`), batch.map(path =>
      `upload-file = ${quote(join(siteDir, path))}\nurl = ${quote(url(path))}\n`).join(""));
    for (const path of batch) progress.set(path, current.get(path));
    writeFileSync(join(outDir, `progress-${number}.sha256`), formatManifest(progress));
  }
  // A leading '*' lets curl continue when a file is already gone.
  writeFileSync(join(outDir, "delete.curl"), deletes.map(path =>
    `quote = ${quote(`*DELE ${posix.join(root, path)}`)}\n`).join(""));
  writeFileSync(join(outDir, "summary.json"), JSON.stringify({
    files: current.size, uploads: uploads.length, deletes: deletes.length, firstDeployment: previous.size === 0,
    manifestUrl: url(manifestName), uploadList: uploads, deleteList: deletes
  }, null, 2));
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
}
