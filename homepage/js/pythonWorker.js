/**
 * Pyodide Web Worker
 *
 * Protocol (postMessage both ways):
 *   Request : { id: number, script: string, packages: string[] }
 *   Response: { id: number, result: string }   – on success
 *             { id: number, error:  string }   – on failure / exception
 */

importScripts("https://cdn.jsdelivr.net/pyodide/v0.26.2/full/pyodide.js");

var pyodide = null;
var pyodideLoadPromise = null;
var loadedPackages = {};

function ensurePyodide() {
  if (pyodide !== null) return Promise.resolve(pyodide);
  if (pyodideLoadPromise !== null) return pyodideLoadPromise;
  pyodideLoadPromise = loadPyodide().then(function (py) {
    pyodide = py;
    return py;
  });
  return pyodideLoadPromise;
}

function ensurePackages(py, packages) {
  if (!packages || packages.length === 0) return Promise.resolve();
  var missing = packages.filter(function (p) { return !loadedPackages[p]; });
  if (missing.length === 0) return Promise.resolve();
  return py.loadPackage(missing).then(function () {
    missing.forEach(function (p) { loadedPackages[p] = true; });
  });
}

self.onmessage = function (event) {
  // "warmup" message: load Pyodide and signal readiness when done.
  if (event.data.type === "warmup") {
    ensurePyodide().then(function () {
      self.postMessage({ type: "ready" });
    });
    return;
  }

  var id       = event.data.id;
  var script   = event.data.script;
  var packages = event.data.packages || [];

  ensurePyodide()
    .then(function (py) {
      return ensurePackages(py, packages).then(function () { return py; });
    })
    .then(function (py) {
      return py.runPythonAsync(script);
    })
    .then(function (result) {
      self.postMessage({ id: id, result: String(result) });
    })
    .catch(function (err) {
      var msg = (err && err.message) ? err.message : String(err);
      self.postMessage({ id: id, error: msg });
    });
};
