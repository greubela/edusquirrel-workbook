# Browser startup and optional runtime loading

Workbook HTML loads `artifacts/newest/client.js` through a native module script
marked `data-eva-client`. `MainApp` mounts after DOM readiness. Asset paths derive
from that script URL, supporting both local preview and repository-prefixed sites.

`HomepageStartupLogic.preheat()` starts CodeMirror, Algebrite and configuration
loading before rendering, independently of editor creation. Turtle pages also
preheat Snap and JSXGraph. Mounting never awaits these requests, language-cache
restoration or remote refresh. CodeMirror keeps an editable textarea until ready.
Dependency failures have bounded deadlines and remain local to their consumers.

## Ownership

Native bindings and loading support live in
`modules/core/js/src/main/scala/it/evadid/facades/`, with one facade file per
dependency and private CodeMirror implementation helpers in `internal`.
`EvaExternalDependency[T]` separates shared code readiness (`preload`) from typed
handle access (`createInstance`). Editors and interpreters belong to individual
consumers; library APIs such as Algebrite are shared. Synchronous renderers may use
`createLoadedInstance` after awaiting readiness.

Page startup owns scheduling and configuration. Facades own imports, compatibility
patches and native handle creation. Snap fetches dependencies in parallel and
executes them in order; failed loads can retry. CodeMirror imports native ESM
packages from Scala.js. Python initializes Pyodide inside the command worker.
Pyodide stays on the maintained 0.29 line because 314.x requires module workers.
Mounted editors own cleanup and discard late completion after unmount. Scala editor
settings and flowchart results stay typed; JavaScript conversion belongs only at
the external browser API boundary.

## Configuration

`ClientRuntimeConfig` reads the public `deployment/client/.env` relative to the
site root; failure or its deadline selects defaults. A deployment may instead set
`window.EDUSQUIRREL_CLIENT_CONFIG` before the client script runs.

`EDUSQUIRREL_BACKEND_MODULE_WORKERS` sets the warm pool size (default 1; 0 disables
warmup). `EDUSQUIRREL_BACKEND_MODULE_WORKER_SCRIPT` overrides the worker URL,
relative to the page. On-demand workers count toward the pool. Bundle selection
belongs to the HTML script's `src`, independently of runtime configuration.

## Verification

CI permits Chromium's sandbox through an executable-scoped AppArmor profile on
Ubuntu. The browser runner checks sandbox startup before running workbook tests.

See the [test guide](core-package-unit-tests.md) for module checks and rebuilt
browser artifacts. Run `npm run test:workbooks -- startup-loading snap-preview codemirror worker-execution`
for stalled/failed loads, reuse, fallback editing and unmount cleanup. Startup
fixtures cover both site-root and GitHub Pages repository paths.
