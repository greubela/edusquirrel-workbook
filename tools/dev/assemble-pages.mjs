#!/usr/bin/env node
import { cpSync, existsSync, lstatSync, mkdirSync, readdirSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import { basename, dirname, join, posix, resolve } from "node:path";
import { pathToFileURL } from "node:url";

function redirect(target) {
  return `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>EduSquirrel</title></head>
<body>
<a href="${target}">Open EduSquirrel</a>
<script>location.replace(${JSON.stringify(target)} + location.search + location.hash);</script>
</body>
</html>
`;
}

export function assemblePages(project = process.cwd()) {
  const root = realpathSync(project);
  const out = join(root, "_site");
  for (const file of ["homepage/index.html", "artifacts/newest/client.js", "artifacts/newest/worker.js",
    "resources/programs/20260907MathworldMain/dist/index.html"]) {
    if (!existsSync(join(root, file)) || !lstatSync(join(root, file)).isFile()) {
      throw new Error(`Missing build input: ${file}`);
    }
  }
  if (existsSync(out) && (lstatSync(out).isSymbolicLink() || realpathSync(out) !== out)) {
    throw new Error(`Refusing to replace linked output: ${out}`);
  }
  rmSync(out, { recursive: true, force: true });
  for (const folder of ["homepage", "resources"]) {
    cpSync(join(root, folder), join(out, folder), {
      recursive: true,
      filter: path => {
        const name = basename(path);
        if (name.startsWith(".") || name === "node_modules") return false;
        if (lstatSync(path).isSymbolicLink()) throw new Error(`Unsupported symbolic link: ${path}`);
        return true;
      }
    });
  }
  const artifacts = join(out, "artifacts", "newest");
  mkdirSync(artifacts, { recursive: true });
  for (const name of ["client.js", "worker.js"]) {
    cpSync(join(root, "artifacts", "newest", name), join(artifacts, name));
  }
  cpSync(join(artifacts, "worker.js"), join(artifacts, "backend-worker.js"));

  function addRedirects(folder = "") {
    for (const entry of readdirSync(join(out, "homepage", folder), { withFileTypes: true })) {
      const page = posix.join(folder, entry.name);
      if (entry.isDirectory()) addRedirects(page);
      else if (/\.html?$/.test(entry.name)) {
        const target = posix.relative(posix.dirname(page), `homepage/${page}`)
          .split("/").map(encodeURIComponent).join("/").replace(/index\.html$/, "");
        mkdirSync(dirname(join(out, page)), { recursive: true });
        writeFileSync(join(out, page), redirect(target));
      }
    }
  }
  addRedirects();
  if (existsSync(join(root, "favicon.ico"))) cpSync(join(root, "favicon.ico"), join(out, "favicon.ico"));
  return out;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    console.log(`Pages assembled: ${assemblePages()}`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
