// Generic Scala.js bundle loader.
//
// Loads the workbook app with fallback paths so the same HTML pages work in
// deployed and local development environments. Runtime configuration is loaded
// in Scala.js after first paint. Legacy .env bundle paths remain a fallback.
//
// Page authors can still override the search list before this script runs by
// setting window.EDUSQUIRREL_APP_PATHS to an array of strings. If they want
// the script tag to be injected as a JS module (the workbook does), they can
// set window.EDUSQUIRREL_APP_AS_MODULE = true.
//
// CodeMirror is optional at startup. Its editor uses an available facade or a
// textarea fallback, so a slow CDN must not delay the app shell.
(function () {
  var version = window.EDUSQUIRREL_APP_VERSION;
  function versioned(path) {
    return version == null ? path : path + (path.indexOf("?") < 0 ? "?" : "&") + "v=" + encodeURIComponent(version);
  }
  var origin = window.location.origin || "";

  var defaultConfig = {
    EDUSQUIRREL_APP_SCRIPT_PRIMARY: "../../artifacts/newest/client.js",
    EDUSQUIRREL_APP_SCRIPT_SECONDARY: "../../artifacts/newest/client-fastOpt.js",
    EDUSQUIRREL_APP_SCRIPT_TERTIARY: "../../artifacts/stable/client.js"
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
    return fetch(envUrls[index], { cache: "no-store" })
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

  function configuredPaths(config) {
    var paths = [
      configValue(config, "EDUSQUIRREL_APP_SCRIPT_PRIMARY"),
      configValue(config, "EDUSQUIRREL_APP_SCRIPT_SECONDARY"),
      configValue(config, "EDUSQUIRREL_APP_SCRIPT_TERTIARY")
    ].filter(function (path) { return path && path.length; });
    return paths.concat(builtInFallbackPaths);
  }

  function loadAt(paths, index, discoverConfig) {
    if (index >= paths.length) {
      console.error("[edusquirrel] Failed to load Scala.js bundle from any known path.");
      return;
    }
    var script = document.createElement("script");
    if (window.EDUSQUIRREL_APP_AS_MODULE !== false) script.type = "module";
    else script.defer = true;
    script.src = versioned(paths[index]);
    script.onerror = function () {
      if (discoverConfig) {
        loadEnvAt(0).then(function (config) {
          var configured = configuredPaths(config).filter(function (path) { return path !== paths[0]; });
          loadAt(configured, 0, false);
        });
      } else loadAt(paths, index + 1, false);
    };
    document.head.appendChild(script);
  }

  function start() {
    // The bundle starts immediately; deployment settings are runtime work in Scala.js.
    // Legacy bundle-path settings are consulted only if the default bundle is absent.
    var paths = (window.EDUSQUIRREL_APP_PATHS && window.EDUSQUIRREL_APP_PATHS.length)
      ? window.EDUSQUIRREL_APP_PATHS
      : configuredPaths(defaultConfig);
    loadAt(paths, 0, !window.EDUSQUIRREL_APP_PATHS);
  }

  start();
})();
