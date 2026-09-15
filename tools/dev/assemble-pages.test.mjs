import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { runInNewContext } from "node:vm";
import { assemblePages } from "./assemble-pages.mjs";

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "edusquirrel-pages-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  function put(file, content) {
    mkdirSync(dirname(join(root, file)), { recursive: true });
    writeFileSync(join(root, file), content);
  }
  put("homepage/index.html", '<head></head><a href="../resources/programs/20260907MathworldMain/dist/index.html">MathWorld</a>');
  put("homepage/feedback-demo/index.html", '<head><script src="../js/app-loader.js"></script></head>');
  put("homepage/js/app-loader.js", readFileSync(new URL("../../homepage/js/app-loader.js", import.meta.url)));
  put("homepage/tools/nested/index.html", "nested page");
  put("homepage/css/main.css", '@font-face { src: url("../../resources/font.woff2"); }');
  put("resources/font.woff2", "font");
  put("resources/programs/20260907MathworldMain/dist/index.html", '<script src="./assets/app.js"></script>');
  put("artifacts/newest/client.js", 'const resource = "../../resources/keep.json";');
  put("artifacts/newest/worker.js", 'const resource = "../../resources/worker.json";');
  return { root, put };
}

test("Pages keeps source files unchanged and publishes only current browser artifacts", t => {
  const { root, put } = fixture(t);
  for (const file of ["homepage/.env", "resources/app/.env", "resources/app/node_modules/package/index.js",
    "artifacts/newest/server.jar", "artifacts/newest/backend-worker.js", "artifacts/stable/client.js"]) put(file, "old");
  put("_site/obsolete.js", "old output");
  const out = assemblePages(root);
  for (const file of ["homepage/index.html", "homepage/feedback-demo/index.html", "homepage/css/main.css",
    "homepage/js/app-loader.js", "resources/programs/20260907MathworldMain/dist/index.html",
    "artifacts/newest/client.js", "artifacts/newest/worker.js"]) {
    assert.deepEqual(readFileSync(join(out, file)), readFileSync(join(root, file)), file);
  }
  assert.deepEqual(readFileSync(join(out, "artifacts/newest/backend-worker.js")),
    readFileSync(join(root, "artifacts/newest/worker.js")));
  for (const file of ["homepage/.env", "resources/app/.env", "resources/app/node_modules",
    "artifacts/newest/server.jar", "artifacts/stable", "obsolete.js"]) assert.equal(existsSync(join(out, file)), false, file);
});

test("old entry URLs redirect inside the project and preserve query and fragment", t => {
  const { root } = fixture(t);
  const out = assemblePages(root);
  for (const prefix of ["/", "/edusquirrel-workbook/"]) {
    for (const page of ["index.html", "feedback-demo/index.html", "tools/nested/index.html"]) {
      const html = readFileSync(join(out, page), "utf8");
      const script = html.match(/<script>(.*?)<\/script>/s)[1];
      const source = new URL(prefix + page.replace(/index\.html$/, ""), "https://example.test");
      let destination;
      runInNewContext(script, { location: { search: "?lang=de", hash: "#section-2", replace: value => { destination = new URL(value, source); } } });
      assert.equal(destination.href, `https://example.test${prefix}homepage/${page.replace(/index\.html$/, "")}?lang=de#section-2`);
      assert.equal(new URL(html.match(/href="([^"]+)"/)[1], source).pathname, destination.pathname);
      assert.equal(existsSync(join(out, "homepage", page)), true);
    }
  }
});

test("the unchanged loader finds the client and worker in the Pages layout", async t => {
  const { root } = fixture(t);
  const out = assemblePages(root);
  for (const prefix of ["/", "/edusquirrel-workbook/"]) {
    const page = new URL(`https://example.test${prefix}homepage/feedback-demo/`);
    const scripts = [], workers = [];
    runInNewContext(readFileSync(join(out, "homepage/js/app-loader.js"), "utf8"), {
      URL, setTimeout, console,
      window: { location: page },
      document: { readyState: "complete", getElementsByTagName: () => [], createElement: () => ({}),
        head: { appendChild: script => scripts.push(script) } },
      Worker: function (url) { workers.push(url); },
      fetch: async () => ({ ok: false, status: 404 })
    });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(scripts.length, 1);
    assert.equal(workers.length, 1);
    for (const value of [scripts[0].src, workers[0]]) {
      const url = new URL(value, page);
      assert.ok(url.pathname.startsWith(prefix + "artifacts/newest/"));
      assert.ok(existsSync(join(out, url.pathname.slice(prefix.length))));
    }
    const font = new URL("../../resources/font.woff2", `https://example.test${prefix}homepage/css/main.css`);
    assert.equal(font.pathname, prefix + "resources/font.woff2");
  }
});

for (const file of ["artifacts/newest/client.js", "artifacts/newest/worker.js", "resources/programs/20260907MathworldMain/dist/index.html"]) {
  test(`missing input preserves the previous export: ${file}`, t => {
    const { root, put } = fixture(t);
    put("_site/index.html", "previous export");
    rmSync(join(root, file));
    assert.throws(() => assemblePages(root), /Missing build input/);
    assert.equal(readFileSync(join(root, "_site/index.html"), "utf8"), "previous export");
  });
}

test("a linked output directory cannot replace another directory", t => {
  const { root, put } = fixture(t);
  put("keep/index.html", "keep");
  symlinkSync(join(root, "keep"), join(root, "_site"), process.platform === "win32" ? "junction" : "dir");
  assert.throws(() => assemblePages(root), /Refusing to replace linked output/);
  assert.equal(readFileSync(join(root, "keep/index.html"), "utf8"), "keep");
});
