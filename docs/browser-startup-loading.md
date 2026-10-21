# Browser startup and optional runtime loading

`homepage/js/app-loader.js` only finds the Scala.js bundle. It starts the request
while HTML is still parsing, retains ordered bundle fallbacks, and leaves asset
caching to HTTP headers. `MainApp` waits for DOM readiness before mounting.

`StartupPreheating.start()` runs once at browser application startup, independently
of EvaEditor. It starts CodeMirror loading and runtime configuration immediately.
Turtle workbook pages also start Snap and JSXGraph loading. These requests are not
awaited by `HomepageStartupLogic`, which mounts the shell before restoring cached
translations or refreshing language sources. Text updates through existing signals.
CodeMirror retains its editable textarea while its module is unavailable.

`SnapRuntimeLoading` preloads the bundled dependencies in parallel, then executes
scripts in their dependency order. A failed script stops later execution; a later
consumer can retry. Successful scripts are reused. Preheating creates no editor,
Morphic world or retained hidden GUI. `SnapRuntimeCompatibility` installs the
serializer patch and native resource base path in Scala.js; `fullturtle.js` and its
unused duplicate editor/API have been removed. `GraphRuntimeLoading` similarly
shares JSXGraph's load, and graph renderers discard late completion after unmount.

Script loads have a 30-second deadline. CodeMirror keeps its existing 15-second
module deadline. Failures remain local to consumers and never gate the shell.

## Client configuration

`ClientRuntimeConfig` fetches one known public client configuration location:
`deployment/client/.env` relative to the site root derived from `app-loader.js`.
There is no four-path search. Failure or a 1.5-second deadline selects defaults;
configuration discovery runs alongside rendering and optional editor loading.
The worker pool is preheated when settings arrive (or the deadline expires),
and an earlier on-demand worker counts toward that pool.

For custom installations, an external script loaded before the bootstrap can set
`window.EDUSQUIRREL_CLIENT_CONFIG` directly, avoiding configuration discovery.
Runtime worker settings are `EDUSQUIRREL_BACKEND_MODULE_WORKERS` (default 1; 0
suppresses warmup) and `EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT` (resolved relative
to the page). A shared Scala.js pool supplies `HomepageDefaults.workerBackendExecutor`.

Bundle selection necessarily happens before Scala.js runs. Custom bundle paths
must therefore be set before `app-loader.js` through `EDUSQUIRREL_APP_PATHS` or
`EDUSQUIRREL_CLIENT_CONFIG.EDUSQUIRREL_APP_SCRIPT_PRIMARY`, `_SECONDARY` and `_TERTIARY`.
Setting those bundle paths in `.env` no longer selects the bootstrap. Explicit
bundle queries such as `client.js?build=<hash>` are preserved; no timestamp is added.
The obsolete `INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED` flag is not used.

## Verification

Run `client/test`, build current client/worker artifacts, and run `npm run test:static`.
`npm run test:workbooks -- startup-loading snap-preview codemirror-loading`
checks stalled dependencies, concurrent prefetch with ordered execution, early
rendering, worker warmup, script reuse, editor persistence and late unmount guards.
Fixtures cover both a site root and a GitHub Pages repository prefix.
