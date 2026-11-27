#!/usr/bin/env node
// Stages the webspace mirror and plans its incremental FTP upload for deploy-client.sh.
// The webspace keeps the manifest of the last successful deployment (sha256sum format);
// only files whose hash changed are uploaded, and only files that earlier deployments
// uploaded are deleted, so unrelated webspace content is never touched.
//
// node sync-plan.mjs <site-dir> <previous-manifest|-> <out-dir> <ftp-base-url> <remote-root> [--trust PREFIX]...
//   Writes into <out-dir>: per batch NNN an upload-NNN.curl config, a verify-NNN.curl size query and
//   batch-NNN.tsv (url, size, local file), progress-NNN.sha256 (the previous manifest plus every
//   batch up to NNN), delete.curl, the final manifest.sha256 and summary.json.
//   Local files in the curl configs are ASCII paths relative to <site-dir>, where curl must run:
//   curl for Windows opens config paths in the ANSI code page, so a non-ASCII user folder or file
//   name would not be found. Files with non-ASCII names are uploaded from an ASCII-named copy in
//   the sibling folder upload-aliases/; their remote names stay unchanged.
// node sync-plan.mjs stage <repo> <out-dir> <client-env>
//   Copies the published part of the repository into <out-dir>, in the repository's layout (the
//   webspace mirrors it): homepage/, resources/, favicon.ico, the client and worker of
//   artifacts/newest/ and artifacts/stable/ (never artifacts/history/ or server builds) and
//   <client-env> as deployment/client/.env. Hidden files, node_modules and links are left out.
// node sync-plan.mjs verify <batch-tsv> <curl-head-output> <retry-curl-out>
//   Compares the remote sizes reported by `curl --head` with the local files, writes an upload
//   config for every missing or mismatched file and prints their number.
import { createHash } from "node:crypto";
import { copyFileSync, cpSync, createReadStream, existsSync, lstatSync, mkdirSync, readdirSync, readFileSync, rmSync,
  statSync, writeFileSync } from "node:fs";
import { basename, dirname, join, posix, resolve } from "node:path";
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

// Returns the warnings, e.g. for a missing stable build.
export function stageWebspace(repo, out, clientEnv) {
  rmSync(out, { recursive: true, force: true });
  for (const folder of ["homepage", "resources"]) {
    cpSync(join(repo, folder), join(out, folder), {
      recursive: true,
      filter: path => {
        const name = basename(path);
        if (path !== join(repo, folder) && (name.startsWith(".") || name === "node_modules")) return false;
        if (lstatSync(path).isSymbolicLink()) return false;
        return true;
      }
    });
  }
  const warnings = [];
  for (const [file, required] of [["artifacts/newest/client.js", true], ["artifacts/newest/worker.js", true],
    ["artifacts/stable/client.js", false], ["artifacts/stable/worker.js", false], ["favicon.ico", false]]) {
    if (!existsSync(join(repo, file))) {
      if (required) throw new Error(`Missing build output: ${file}`);
      warnings.push(`${file} is missing and is not uploaded`);
      continue;
    }
    mkdirSync(dirname(join(out, file)), { recursive: true });
    copyFileSync(join(repo, file), join(out, file));
  }
  mkdirSync(join(out, "deployment", "client"), { recursive: true });
  copyFileSync(clientEnv, join(out, "deployment", "client", ".env"));
  return warnings;
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
const uploadEntry = (file, target) => `upload-file = ${quote(file)}\nurl = ${quote(target)}\n`;
// Written by `curl --head --write-out` after each file of a verify config.
export const sizeMarker = "EDUSQUIRREL-SIZE";

// Maps each URL to the Content-Length curl reported for it; files that failed are absent.
export function parseRemoteSizes(output) {
  const sizes = new Map();
  let length = null;
  for (const line of output.split(/\r?\n/)) {
    const header = /^Content-Length: (\d+)$/i.exec(line);
    if (header) length = Number(header[1]);
    const marker = line.split("\t");
    if (marker[0] === sizeMarker) {
      if (marker[2] === "0" && length !== null) sizes.set(marker[1], length);
      length = null;
    }
  }
  return sizes;
}

function verify([batchFile, headOutput, retryFile]) {
  const remote = parseRemoteSizes(readFileSync(headOutput, "utf8"));
  const failed = readFileSync(batchFile, "utf8").split("\n").filter(Boolean).map(line => line.split("\t"))
    .filter(([target, size]) => remote.get(target) !== Number(size));
  writeFileSync(retryFile, failed.map(([target, , file]) => uploadEntry(file, target)).join(""));
  for (const [target, size] of failed) console.error(`  ${target}: expected ${size} bytes, remote ${remote.get(target) ?? "missing"}`);
  console.log(failed.length);
}

async function main([siteDir, previousFile, outDir, baseUrl, remoteRoot, ...options]) {
  if (!outDir || !baseUrl || remoteRoot === undefined) throw new Error("usage: see header of sync-plan.mjs");
  const trusted = options.flatMap((option, index) => option === "--trust" ? [options[index + 1]] : []);
  const current = new Map();
  for (const path of listFiles(siteDir).sort()) current.set(path, await sha256(join(siteDir, path)));
  const aliasDir = join(siteDir, "..", "upload-aliases");
  rmSync(aliasDir, { recursive: true, force: true });
  const localFile = path => {
    if (/^[\x20-\x7e]*$/.test(path)) return path;
    mkdirSync(aliasDir, { recursive: true });
    const alias = join(aliasDir, current.get(path));
    if (!existsSync(alias)) copyFileSync(join(siteDir, path), alias);
    return `../upload-aliases/${current.get(path)}`;
  };
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
    writeFileSync(join(outDir, `upload-${number}.curl`), batch.map(path => uploadEntry(localFile(path), url(path))).join(""));
    writeFileSync(join(outDir, `verify-${number}.curl`),
      `head\nwrite-out = "${sizeMarker}\\t%{url}\\t%{exitcode}\\n"\n` + batch.map(path => `url = ${quote(url(path))}\n`).join(""));
    writeFileSync(join(outDir, `batch-${number}.tsv`), batch.map(path =>
      `${url(path)}\t${statSync(join(siteDir, path)).size}\t${localFile(path)}\n`).join(""));
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
  const [command, ...rest] = process.argv.slice(2);
  const commands = {
    verify,
    stage: ([repo, out, clientEnv]) => stageWebspace(repo, out, clientEnv).forEach(warning => console.error(warning))
  };
  Promise.resolve().then(() => (commands[command] ?? (args => main([command, ...args])))(rest))
    .catch(error => { console.error(error.message); process.exitCode = 1; });
}
