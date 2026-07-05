// Generic Scala.js bundle loader.
//
// Loads the workbook app with fallback paths so the same HTML pages work in
// deployed and local development environments. Runtime defaults can be
// overridden with /deployment/client/.env (or a relative local-dev fallback).
//
// Page authors can still override the search list before this script runs by
// setting window.EDUSQUIRREL_APP_PATHS to an array of strings. If they want
// the script tag to be injected as a JS module (the workbook does), they can
// set window.EDUSQUIRREL_APP_AS_MODULE = true.
//
// Pages that load CodeMirror before the Scala.js app can expose
// window.EduSquirrelCodeMirrorReady. The loader waits briefly for that promise
// when it sees a CodeMirror script tag, then continues even if CodeMirror fails.
(function () {
  var cacheBust = "v=" + Date.now();
  var origin = window.location.origin || "";

  var defaultConfig = {
    EDUSQUIRREL_APP_SCRIPT_PRIMARY: "../../artifacts/newest/client.js",
    EDUSQUIRREL_APP_SCRIPT_SECONDARY: "../../artifacts/newest/client-fastOpt.js",
    EDUSQUIRREL_APP_SCRIPT_TERTIARY: "../../artifacts/stable/client.js",
    EDUSQUIRREL_BACKEND_MODULE_WORKERS: "1",
    INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED: "true",
    WRITE_FULL_LOGFILES: "false"
  };

  var builtInFallbackPaths = [
    "../js/app/main.js",
    "./js/app/main.js",
    origin + "/js/app/main.js",
    "../target/client/scala-3.8.3/client-fastopt.js",
    "../../target/client/scala-3.8.3/client-fastopt.js",
    origin + "/target/client/scala-3.8.3/client-fastopt.js",
    "../target/scala-3.3.3/workbookapp-fastopt/main.js",
    "../../target/scala-3.3.3/workbookapp-fastopt/main.js",
    origin + "/target/scala-3.3.3/workbookapp-fastopt/main.js"
  ];

  var envUrls = [
    origin + "/deployment/client/.env",
    "../../deployment/client/.env",
    "../deployment/client/.env",
    "./deployment/client/.env"
  ];

  function parseEnv(text) {
    var result = {};
    text.split(/\r?\n/).forEach(function (line) {
      var trimmed = line.trim();
      if (!trimmed || trimmed.charAt(0) === "#") return;
      var separator = trimmed.indexOf("=");
      if (separator === -1) return;
      var key = trimmed.slice(0, separator).trim();
      var value = trimmed.slice(separator + 1).trim();
      if ((value.charAt(0) === '"' && value.charAt(value.length - 1) === '"') ||
          (value.charAt(0) === "'" && value.charAt(value.length - 1) === "'")) {
        value = value.slice(1, -1);
      }
      result[key] = value;
    });
    return result;
  }

  function loadEnvAt(index) {
    if (index >= envUrls.length) return Promise.resolve({});
    return fetch(envUrls[index] + "?" + cacheBust, { cache: "no-store" })
      .then(function (response) {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.text();
      })
      .then(parseEnv)
      .catch(function () { return loadEnvAt(index + 1); });
  }

  function configValue(config, key) {
    return config[key] == null || config[key] === "" ? defaultConfig[key] : config[key];
  }

  function asBoolean(value) {
    return String(value).toLowerCase() === "true" || String(value) === "1";
  }

  function asPositiveInt(value, fallback) {
    var parsed = parseInt(value, 10);
    return isNaN(parsed) || parsed < 0 ? fallback : parsed;
  }

  function configuredPaths(config) {
    var paths = [
      configValue(config, "EDUSQUIRREL_APP_SCRIPT_PRIMARY"),
      configValue(config, "EDUSQUIRREL_APP_SCRIPT_SECONDARY"),
      configValue(config, "EDUSQUIRREL_APP_SCRIPT_TERTIARY")
    ].filter(function (path) { return path && path.length; });
    return paths.concat(builtInFallbackPaths);
  }

  function preloadBackendWorkers(config) {
    var workerCount = asPositiveInt(configValue(config, "EDUSQUIRREL_BACKEND_MODULE_WORKERS"), 1);
    var workerPath = config.EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT || "../../artifacts/newest/backend-worker.js";
    window.EDUSQUIRREL_BACKEND_WORKERS = window.EDUSQUIRREL_BACKEND_WORKERS || [];
    for (var i = 0; i < workerCount; i++) {
      try {
        window.EDUSQUIRREL_BACKEND_WORKERS.push(new Worker(workerPath + "?" + cacheBust));
      } catch (err) {
        console.warn("[edusquirrel] Failed to preheat backend worker", i + 1, err);
      }
    }
  }

  function loadAt(paths, index) {
    if (index >= paths.length) {
      console.error("[edusquirrel] Failed to load Scala.js bundle from any known path.");
      return;
    }
    var script = document.createElement("script");
    if (window.EDUSQUIRREL_APP_AS_MODULE !== false) script.type = "module";
    else script.defer = true;
    script.src = paths[index] + "?" + cacheBust;
    script.onerror = function () { loadAt(paths, index + 1); };
    document.head.appendChild(script);
  }

  function hasCodeMirrorScript() {
    var scripts = document.getElementsByTagName("script");
    for (var i = 0; i < scripts.length; i++) {
      var src = scripts[i].src || "";
      if (src.indexOf("/js/CodeMirrorLoader.js") !== -1 ||
          src.indexOf("/js/feedback-demo-codemirror.js") !== -1) {
        return true;
      }
    }
    return false;
  }

  function waitForCodeMirror(maxMs) {
    if (!hasCodeMirrorScript()) return Promise.resolve();

    var startTime = Date.now();
    return new Promise(function (resolve) {
      function tick() {
        var ready = window.EduSquirrelCodeMirrorReady;
        if (ready && typeof ready.then === "function") {
          ready.then(resolve, resolve);
          return;
        }
        if (Date.now() - startTime > maxMs) {
          resolve();
          return;
        }
        setTimeout(tick, 30);
      }

      tick();
    });
  }

  function start() {
    loadEnvAt(0).then(function (envConfig) {
      var config = Object.assign({}, defaultConfig, envConfig);
      window.EDUSQUIRREL_CLIENT_CONFIG = config;
      window.EDUSQUIRREL_INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED = asBoolean(
        configValue(config, "INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED")
      );
      preloadBackendWorkers(config);

      waitForCodeMirror(8000).then(function () {
        var paths = (window.EDUSQUIRREL_APP_PATHS && window.EDUSQUIRREL_APP_PATHS.length)
          ? window.EDUSQUIRREL_APP_PATHS
          : configuredPaths(config);
        loadAt(paths, 0);
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
