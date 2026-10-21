// Minimal bootstrap: only locate the Scala.js bundle. Runtime configuration,
// dependency readiness and worker preheating belong to Scala.js startup.
// Custom bundle locations must be supplied through EDUSQUIRREL_APP_PATHS or
// EDUSQUIRREL_CLIENT_CONFIG before this script runs.
(function () {
  var origin = window.location.origin || "";
  var config = window.EDUSQUIRREL_CLIENT_CONFIG || {};
  var paths = window.EDUSQUIRREL_APP_PATHS || [
    config.EDUSQUIRREL_APP_SCRIPT_PRIMARY || "../../artifacts/newest/client.js",
    config.EDUSQUIRREL_APP_SCRIPT_SECONDARY || "../../artifacts/newest/client-fastOpt.js",
    config.EDUSQUIRREL_APP_SCRIPT_TERTIARY || "../../artifacts/stable/client.js",
    "../js/app/main.js", "./js/app/main.js", origin + "/js/app/main.js"
  ];
  function loadAt(index) {
    if (index >= paths.length) {
      console.error("[edusquirrel] Failed to load Scala.js bundle from any known path.");
      return;
    }
    var script = document.createElement("script");
    if (window.EDUSQUIRREL_APP_AS_MODULE !== false) script.type = "module";
    else script.defer = true;
    script.src = paths[index];
    script.onerror = function () { loadAt(index + 1); };
    document.head.appendChild(script);
  }
  // Begin downloading immediately, including while HTML is still being parsed.
  // MainApp handles DOM readiness before mounting the application.
  loadAt(0);
})();
