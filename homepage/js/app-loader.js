// Generic Scala.js bundle loader.
//
// Loads the workbook app `main.js` with several fallback paths so the same
// HTML pages work in three environments:
//   1) GitHub Pages deployment (bundle copied to /js/app/main.js by CI)
//   2) Local preview of the assembled _site/ directory
//   3) Local sbt fastOptJS dev (bundle still under target/scala-3.3.3/...)
//
// Page authors can override defaults before this script runs:
//   window.EDUSQUIRREL_APP_PATHS  – array of URLs to try in order
//   window.EDUSQUIRREL_APP_AS_MODULE = false – load as plain script instead of ES module
//   window.EduSquirrelCodeMirrorReady – a Promise; when present the bundle is
//     deferred until that promise resolves so CodeMirror is ready first
(function () {
  var cacheBust = "v=" + Date.now();
  var origin = window.location.origin || "";

  var defaultPaths = [
    "../../artifacts/newest/client.js",
    "../../artifacts/newest/client-fastOpt.js",
    "../../artifacts/stable/client.js",
    "../js/app/main.js",
    "./js/app/main.js",
    origin + "/js/app/main.js",
    "../target/scala-3.3.3/workbookapp-fastopt/main.js",
    "../../target/scala-3.3.3/workbookapp-fastopt/main.js",
    origin + "/target/scala-3.3.3/workbookapp-fastopt/main.js"
  ];

  var paths = (window.EDUSQUIRREL_APP_PATHS && window.EDUSQUIRREL_APP_PATHS.length)
    ? window.EDUSQUIRREL_APP_PATHS
    : defaultPaths;

  var asModule = window.EDUSQUIRREL_APP_AS_MODULE !== false;

  function loadAt(index) {
    if (index >= paths.length) {
      console.error("[edusquirrel] Failed to load Scala.js bundle from any known path.");
      return;
    }
    var script = document.createElement("script");
    if (asModule) script.type = "module";
    else script.defer = true;
    script.src = paths[index] + "?" + cacheBust;
    script.onerror = function () { loadAt(index + 1); };
    document.head.appendChild(script);
  }

  function start() {
    var cmReady = window.EduSquirrelCodeMirrorReady;
    if (cmReady && typeof cmReady.then === "function") {
      cmReady.then(function () { loadAt(0); }, function () { loadAt(0); });
    } else {
      loadAt(0);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
