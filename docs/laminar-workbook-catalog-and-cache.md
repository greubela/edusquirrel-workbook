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

Triple encoding builds lookup indices once; decoding uses indexed vectors and
precomputed language tables. Storage encodes once per write. IndexedDB connections
close after transactions/version changes, and failed/aborted/blocked operations
fail their futures. No schema migration is required by this cache path.

The cache cannot remove bundle download/evaluation, deployment-env discovery or
other resource initialization costs. Do not use an old local timing measurement
as a performance guarantee. The minimal `app-loader.js` starts the app bundle
immediately. Scala.js owns configuration discovery and optional runtime preheating;
see [browser startup loading](browser-startup-loading.md).

## Verification

The catalogue browser test holds remote refresh on an unresolved gate and asserts
that cached UI becomes usable first. It checks one keyed read, no cursor scan/no
rewrite, an unrelated large record, deterministic translation replacement,
responsive layout, language switching, navigation and answer persistence. It also
covers corrupt cache and denied IndexedDB. External artwork is fulfilled locally,
so these tests do not verify availability of third-party sites.

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
