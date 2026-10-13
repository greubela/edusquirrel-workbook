# Workbook catalogue and language-cache startup

`homepage/workbooks/index.html` mounts the `landingPage` container.
`HtmlWorkbookDomElement` renders `HtmlSelectWorkbookElement` when no workbook is
selected, including before login. A native card navigates to its registered entry
page; the selected workbook uses the existing login/local-session flow. The user
menu offers “All digital workbooks” to return to the catalogue.

`DigitalWorkbookCatalog` currently has nine native editions and five standalone
or external digital offerings. It excludes PDF/ZIP-only materials and developer
demos. Native entries provide both the page/container and workbook factory, so
startup and the public list use the same registry. The native compression workbook
and standalone lab are separate entries.

The DOM is Laminar, with reactive German/English text from `workbookSelection`.
CSS uses shared tokens and keeps the privacy footer in document flow. The catalogue does not wait for editor dependencies. Optional runtimes are warmed
after the overview has painted; consumers share those loads and wait locally if
opened early.
See [dependency-loading boundaries](workbook-interaction-architecture.md#dependencies-and-execution).

Native entries currently set `inProgress` in the catalogue registry. That label
tracks edition/content review, not whether its editor or execution backend works.
See [migration scope](digital-workbook-migration.md) for actual remaining content.

## Current IndexedDB lifecycle

`HomepageStartupLogic.initHomepage()` uses `CONTINUE_AFTER_LOCAL_CACHE_SUCCESS`.
`ParsedTriples` is cached as `EvaDidCacheDb/cache/tripleCache`:

1. Read the record with one IndexedDB `get` and decode its established wire format.
2. Install cached translations without rewriting them, then complete cached-startup
   readiness. Remote refresh runs in a later browser task.
3. Merge fresh values by content identifier/language, replacing stale values while
   preserving unrelated entries/languages. Persist the merged result once.
4. On unavailable/corrupt cache, load from sources. A later refresh failure cannot
   undo successful cached startup. Immediate and full-load strategies keep their
   separate semantics.

Triple encoding builds lookup indices once; decoding uses indexed vectors and
precomputed language tables. Storage encodes once per write. IndexedDB connections
close after transactions/version changes, and failed/aborted/blocked operations
fail their futures. No schema migration is required by this cache path.

## Two startup phases

1. Entry HTML contains a small, accessible loading shell styled by shared CSS.
   The deferred `app-loader.js` requests the Scala.js bundle immediately; it does
   not fetch deployment configuration or start workers on the normal path.
   `HomepageStartupLogic` yields an animation frame and a following browser task
   before creating the application context and restoring translations.
2. Once the translated app shell is mounted, another paint opportunity precedes
   idle tasks in `OptionalRuntimeStartup`. Scala.js coordinates CodeMirror,
   ordered Snap scripts, JSXGraph, one reusable command-based Python worker and the shared
   backend execution client. No editor or Morphic world is created by warmup.
   Configuration and health checks are also background work. An early consumer
   shares the same readiness future or takes ownership of the warmed Python
   worker; independent Python execution sessions retain isolated state.

`BrowserDependencyLoading` shares script requests, uses low fetch priority,
times out failed loads and permits retries. Snap script order now lives in
`SnapRuntimeLoading`; `fullturtle.js` is an adapter for already loaded programs.
JSXGraph previews await their dependency locally and discard callbacks after
unmounting. The bootstrap remains JavaScript because it must locate Scala.js
before that code can execute. Its legacy `.env` bundle-path discovery only runs
if the default bundle is absent; use an explicit `EDUSQUIRREL_APP_PATHS` override
when a custom bundle must take precedence over an existing default.

Normal bundle URLs are stable to allow HTTP caching/revalidation. Deployments
can set `EDUSQUIRREL_APP_VERSION` to a release identifier for an explicit cache
key; this honors existing query parameters. Deployment cache headers still
control freshness. The old `INIT_WORKBOOK_ONLY_AFTER_ALL_DEPENDENCIES_LOADED`
setting no longer makes optional runtimes startup dependencies.

Existing Content Security Policies are unchanged. External CodeMirror/Pyodide
warmup is skipped where the policy does not already permit its CDN. Local Snap
and JSXGraph assets can still warm there. Cold cache misses still wait for
translations before the full overview, but not before the HTML loading shell.
Bundle download/evaluation and CSS delivery remain costs; no fixed millisecond
speedup is claimed.

## Verification

The catalogue browser test holds remote refresh on an unresolved gate and asserts
that cached UI becomes usable first. It checks one keyed read, no cursor scan/no
rewrite, an unrelated large record, deterministic translation replacement,
responsive layout, language switching, navigation and answer persistence. It also
covers corrupt cache and denied IndexedDB. External artwork is fulfilled locally,
so these tests do not verify availability of third-party sites.

```sh
sbt 'client/testOnly *LanguageCacheStartupSpec *DigitalWorkbookCatalogSpec *WorkbookContentStorageSpec *StartupTasksSpec *BrowserDependencyLoadingSpec *ClientStartupConfigSpec *PyodideWorkerPreheatSpec'
sbt 'coreJVM/testOnly *LanguageSerializationSpec *SyncDestinationRawSpec'
sbt 'coreJS/testOnly *LanguageSerializationSpec *SyncDestinationRawSpec'
sbt buildClientDev buildWorkerDev
npm run test:static
npm run test:workbooks -- startup-loading workbook-catalog codemirror-loading snap-preview
```

The browser runner uses Playwright Chromium unless `CHROMIUM_PATH` is set.
`CATALOG_SCREENSHOT` optionally saves a review capture. The reliable startup
assertion is independence from pending refresh, not a fixed millisecond threshold.
