# Workbook catalogue and language-cache startup

`homepage/workbooks/index.html` mounts the `landingPage` container.
`HtmlWorkbookDomElement` renders `HtmlSelectWorkbookElement` when no workbook is
selected, including before login. A native card navigates to its registered entry
page; the selected workbook uses the existing login/local-session flow. The user
menu offers “All digital workbooks” to return to the catalogue.

`DigitalWorkbookCatalog` registers native and standalone/external digital
editions. It excludes PDF/ZIP-only materials and developer demos. Native entries provide both the page/container and workbook factory, so
startup and the public list use the same registry. The native compression workbook
and standalone lab are separate entries.

The DOM is Laminar, with reactive German/English text from `workbookSelection`.
CSS uses shared tokens and keeps the privacy footer in document flow. The catalogue
preheats CodeMirror independently of editor creation, but does not load Turtle or
JSXGraph. Turtle workbook startup preheats those runtimes through Scala.js.
See [dependency-loading boundaries](workbook-interaction-architecture.md#dependencies-and-execution).

Native entries currently set `inProgress` in the catalogue registry. That label
tracks edition/content review, not whether its editor or execution backend works.
See [migration scope](digital-workbook-migration.md) for actual remaining content.

## Current IndexedDB lifecycle

`HomepageStartupLogic.initHomepage()` mounts the UI before starting language-cache
restoration with `CONTINUE_IMMEDIATELY`. Labels update reactively as content arrives.
`ParsedTriples` is cached as `EvaDidCacheDb/cache/tripleCache`:

1. Read the record with one IndexedDB `get` and decode its established wire format.
2. Install cached translations without rewriting them. Remote refresh runs in a
   later browser task; neither operation gates the initial mount.
3. Merge fresh values by content identifier/language, replacing stale values while
   preserving unrelated entries/languages. Persist the merged result once.
4. On unavailable/corrupt cache, load from sources. A later refresh failure cannot
   undo successful cached startup. Immediate and full-load strategies keep their
   separate semantics.

IndexedDB connections are transaction-owned and close on completion or version
changes. Storage failures propagate through futures so startup can fall back to
sources. Cache serialization stays in the content layer.

The cache cannot remove bundle download/evaluation, deployment-env discovery or
other resource initialization costs. Do not use an old local timing measurement
as a performance guarantee. The minimal `app-loader.js` starts the app bundle
immediately. Scala.js owns configuration discovery and optional runtime preheating;
see [browser startup loading](browser-startup-loading.md).

## Verification

Browser tests deliberately stall refresh to verify that cached UI becomes usable
first. They also cover corrupt/unavailable IndexedDB, translation replacement,
navigation and saved answers. Local artwork fixtures do not establish availability
of external sites.

```sh
sbt 'client/testOnly *LanguageCacheStartupSpec *DigitalWorkbookCatalogSpec *WorkbookContentStorageSpec'
sbt 'coreJVM/testOnly *LanguageSerializationSpec *SyncDestinationRawSpec'
sbt 'coreJS/testOnly *LanguageSerializationSpec *SyncDestinationRawSpec'
sbt buildClientDev buildWorkerDev
npm run test:static
npm run test:workbooks -- workbook-catalog codemirror-loading
```

The browser runner uses Playwright Chromium unless `CHROMIUM_PATH` is set.
`CATALOG_SCREENSHOT` optionally saves a review capture. The reliable startup
assertion is independence from pending refresh, not a fixed millisecond threshold.
