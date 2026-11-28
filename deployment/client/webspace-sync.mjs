#!/usr/bin/env node
// Mirrors the published part of the repository to the webspace over FTPS for deploy-client.sh.
//
// node webspace-sync.mjs stage <repo> <out-dir> <client-env>
//   Copies the published part of the repository into <out-dir>, in the repository's layout (the
//   webspace mirrors it): homepage/, resources/, favicon.ico, the client and worker of
//   artifacts/newest/ and artifacts/stable/ (never artifacts/history/ or server builds) and
//   <client-env> as deployment/client/.env. Hidden files, node_modules and links are left out.
// node webspace-sync.mjs sync <site-dir> [--dry-run] [--trust PREFIX]...
//   Uploads <site-dir> to CLIENT_FTP_ROOT. The connection comes from the environment
//   (CLIENT_FTP_HOST, CLIENT_FTP_PORT, CLIENT_FTP_USER, CLIENT_FTP_PASSWORD, CLIENT_FTP_TLS), so the
//   password never appears in a command line.
//
// The webspace keeps the manifest of the last successful deployment (sha256sum format): only files
// whose hash changed are uploaded, and only files that earlier deployments uploaded are deleted, so
// other webspace content is never touched. Every upload is checked by remote size, because servers
// can confirm transfers they stored incompletely; after each batch a progress manifest is stored,
// so a rerun resumes. basic-ftp uses Node's own TLS, unlike curl for Windows (Schannel).
import { Client, FTPError } from "basic-ftp";
import { createHash } from "node:crypto";
import { copyFileSync, cpSync, createReadStream, existsSync, lstatSync, mkdirSync, readdirSync, rmSync,
  statSync } from "node:fs";
import { basename, dirname, join, posix, resolve } from "node:path";
import { Readable, Writable } from "node:stream";
import { pathToFileURL } from "node:url";

export const manifestName = ".deploy-manifest.sha256";
const batchSize = 500;
// Transient failures (4xx replies, lost connections) are retried: twice at once, then after a pause.
const retryDelays = [0, 0, 1000, 2000, 4000, 8000, 8000];
const sizeAttempts = 3;
const pause = ms => new Promise(done => setTimeout(done, ms));

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

export const formatManifest = entries => [...entries].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
  .map(([path, hash]) => `${hash}  ${path}\n`).join("");

export function plan(current, previous, trusted = []) {
  const isTrusted = path => trusted.some(prefix => path === prefix || path.startsWith(prefix.replace(/\/?$/, "/")));
  const uploads = [...current].filter(([path, hash]) =>
    previous.has(path) ? previous.get(path) !== hash : !isTrusted(path)).map(([path]) => path);
  const deletes = [...previous.keys()].filter(path => !current.has(path));
  return { uploads, deletes };
}

// Manifests after each batch: files not yet uploaded keep their old hashes, so a rerun uploads them again.
export function progressManifests(current, previous, uploads) {
  const pending = new Set(uploads);
  const progress = new Map([...previous, ...[...current].filter(([path]) => !pending.has(path))]);
  const batches = [];
  for (let start = 0; start < uploads.length; start += batchSize) {
    const files = uploads.slice(start, start + batchSize);
    for (const path of files) progress.set(path, current.get(path));
    batches.push({ files, manifest: formatManifest(progress) });
  }
  return batches;
}

const log = message => console.error(`[client] ${message}`);

// One FTP(S) session that reconnects after a failed command and retries it.
class Session {
  constructor(options) { this.options = options; this.client = null; this.directories = new Set(); }

  async run(description, task) {
    for (let attempt = 1; ; attempt++) {
      try {
        if (!this.client || this.client.closed) {
          this.client = new Client(60000);
          await this.client.access(this.options);
        }
        return await task(this.client);
      } catch (error) {
        this.close();
        if (error instanceof FTPError && error.code === 530) {
          throw new Error(`the FTP server rejected the login of '${this.options.user}' (530): check CLIENT_FTP_USER ` +
            "and CLIENT_FTP_PASSWORD in local.env against FileZilla's site settings");
        }
        // Permanent replies (5xx) are final; transient ones (4xx such as 451) and lost connections are retried.
        const permanent = error instanceof FTPError && error.code >= 500;
        if (permanent || attempt > retryDelays.length) {
          throw Object.assign(new Error(`${description} failed: ${error.message}`), { code: error.code });
        }
        log(`warning: ${description} failed (${error.message}); retrying`);
        await pause(retryDelays[attempt - 1]);
      }
    }
  }

  async ensureDir(directory) {
    if (this.directories.has(directory)) return;
    await this.run(`creating ${directory}`, client => client.ensureDir(directory));
    this.directories.add(directory);
  }

  close() { this.client?.close(); this.client = null; }
}

async function readRemoteText(session, path) {
  const chunks = [];
  const sink = new Writable({ write(chunk, _, done) { chunks.push(chunk); done(); } });
  try {
    await session.run(`reading ${path}`, client => client.downloadTo(sink, path));
  } catch (error) {
    if (error.code === 550) return null;  // No such file: the first deployment.
    throw error;
  }
  return Buffer.concat(chunks).toString("utf8");
}

async function sync(siteDir, options) {
  const env = process.env;
  for (const name of ["CLIENT_FTP_HOST", "CLIENT_FTP_USER", "CLIENT_FTP_PASSWORD"]) {
    if (!env[name]) throw new Error(`${name} is not set`);
  }
  const root = (env.CLIENT_FTP_ROOT ?? "EvaHomepage/edusquirrel").replace(/^\/+|\/+$/g, "");
  const session = new Session({
    host: env.CLIENT_FTP_HOST, port: Number(env.CLIENT_FTP_PORT || 21),
    user: env.CLIENT_FTP_USER, password: env.CLIENT_FTP_PASSWORD,
    secure: (env.CLIENT_FTP_TLS ?? "true") === "true"
  });
  try {
    // Paths are relative to the login directory.
    const home = await session.run("connecting", client => client.pwd());
    const remote = path => posix.join(home, root, path);
    log(`reading the manifest of the last deployment from ${env.CLIENT_FTP_HOST}/${root}`);
    const previousText = await readRemoteText(session, remote(manifestName));
    const previous = previousText === null ? new Map() : parseManifest(previousText);

    const current = new Map();
    for (const path of listFiles(siteDir).sort()) current.set(path, await sha256(join(siteDir, path)));
    const { uploads, deletes } = plan(current, previous, options.trusted);
    if (previousText === null) {
      log("warning: no deployment manifest on the webspace: this deployment uploads every file not covered by --trust");
    }
    log(`${current.size} files staged; ${uploads.length} to upload, ${deletes.length} to delete`);
    if (options.dryRun) {
      for (const path of uploads) console.error(`  upload ${path}`);
      for (const path of deletes) console.error(`  delete ${path}`);
      log("dry run: webspace unchanged");
      return;
    }

    const batches = progressManifests(current, previous, uploads);
    for (const [index, batch] of batches.entries()) {
      log(`uploading batch ${index + 1} of ${batches.length} (${batch.files.length} files)`);
      for (const path of batch.files) {
        const local = join(siteDir, path), size = statSync(local).size;
        await session.ensureDir(posix.dirname(remote(path)));
        for (let attempt = 1; ; attempt++) {
          await session.run(`uploading ${path}`, client => client.uploadFrom(local, remote(path)));
          const stored = await session.run(`checking ${path}`, client => client.size(remote(path)));
          if (stored === size) break;
          if (attempt >= sizeAttempts) throw new Error(`${path} arrived with ${stored} of ${size} bytes ${sizeAttempts} times`);
          log(`warning: ${path} arrived with ${stored} of ${size} bytes; uploading it again`);
        }
      }
      await session.run("recording upload progress", client =>
        client.uploadFrom(Readable.from([batch.manifest]), remote(manifestName)));
    }
    if (deletes.length > 0) {
      log(`deleting ${deletes.length} files that earlier deployments uploaded`);
      for (const path of deletes) await session.run(`deleting ${path}`, client => client.remove(remote(path), true));
    }
    await session.run("storing the deployment manifest", client =>
      client.uploadFrom(Readable.from([formatManifest(current)]), remote(manifestName)));
    log(`client deployed to ${env.CLIENT_FTP_HOST}/${root}`);
  } finally {
    session.close();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [command, ...args] = process.argv.slice(2);
  const commands = {
    stage: ([repo, out, clientEnv]) => stageWebspace(repo, out, clientEnv).forEach(warning => log(`warning: ${warning}`)),
    sync: ([siteDir, ...rest]) => sync(siteDir, {
      dryRun: rest.includes("--dry-run"),
      trusted: rest.flatMap((option, index) => option === "--trust" ? [rest[index + 1]] : [])
    })
  };
  if (!commands[command]) {
    console.error("usage: see the header of webspace-sync.mjs");
    process.exitCode = 2;
  } else {
    Promise.resolve().then(() => commands[command](args))
      .catch(error => { log(`error: ${error.message}`); process.exitCode = 1; });
  }
}
