# Laminar workbook catalogue and language-cache startup

`homepage/workbooks/index.html` mounts the existing `landingPage` container.
`HtmlWorkbookDomElement` renders `HtmlSelectWorkbookElement` whenever no workbook
is selected, including before login. Selecting a native card navigates to its
existing entry page; that page loads its own editor dependencies and restores the
user session. The user menu offers “All digital workbooks” to return to selection.
A selected workbook still requires the existing login or local-session flow.
The classic catalogue is retained and links to the digital catalogue.

`DigitalWorkbookCatalog` lists eight native workbooks and five standalone digital
offerings from the old landing page. It excludes PDF/ZIP-only materials, developer
demos, editor previews and proposed workbooks. Native entries provide both the
public entry-page link and the factory used by `HomepageStartupLogic`, avoiding
a separate public/factory list. The compression workbook and standalone lab are
labelled separately. External links open a new tab; same-origin links preserve
normal browser navigation. Partial digital editions remain marked in development.

The catalogue's DOM is entirely Laminar, with reactive English/German labels from
`workbookSelection`. Artwork and card structure follow the classic page. CSS is
scoped to the catalogue and uses shared design tokens; the privacy footer remains
in document flow so it cannot cover a card. The catalogue entry loads no Turtle,
JSXGraph or CodeMirror assets. Direct workbook pages retain their dependencies.

## Findings about ParsedTriples and IndexedDB

The original `CONTINUE_AFTER_LOCAL_CACHE_SUCCESS` path did read a real persistent
`EvaDidCacheDb/cache/tripleCache` record and could render before remote refresh
completed. A baseline browser run with language refresh delayed five seconds
rendered the cached workbook login first. The flag was not simply ignored.

However, restoring the cache called the method that immediately wrote the whole
cache again. The generic typed storage adapter also serialized each write twice.
Triple serialization searched the full content-ID list for each entry; decoding
indexed lists and remapped language tables repeatedly. These synchronous costs
occurred on the browser thread, even though the IndexedDB API itself is async.
The IndexedDB implementation read one key by scanning every cache record and
left database connections open. Immediate-start strategy never actually completed
immediately, and background refresh failure could try to fail an already fulfilled
startup promise. Fresh and cached values for the same translation key were united
as separate entries, making the selected value dependent on set iteration.

The revised path:

1. Read `tripleCache` with one IndexedDB `get` and decode its unchanged wire format.
2. Install cached translations without rewriting them.
3. Complete cached startup readiness and schedule remote refresh in a later browser
   task. Immediate and full-load strategies retain their distinct semantics.
4. Replace old values by identifier/language when fresh data arrives, preserving
   unrelated entries and languages. Persist the merged translations once.
5. Report cache read/write errors and fall back to source loading; a later refresh
   failure cannot undo successful cached startup.

Triple serialization now builds lookup indices once, and decoding uses indexed
vectors and precomputed language tables. Storage serializes once per write.
IndexedDB connections close after each transaction and on version change; aborted
transactions and blocked/unavailable opens fail their futures instead of hanging.
No cache/schema migration is needed. Startup also no longer serializes every
workbook into both full debug formats for console output.

Another delay occurred **before** `initHomepage`: `app-loader.js` waited up to
8 seconds for CodeMirror, and a never-resolving readiness promise could stall it
indefinitely. `CodeMirrorEditor` already supports an available facade or textarea
fallback. The loader now starts the app without waiting for the optional editor.
The development client bundle is about 23 MB and the loader still uses a timestamp
query parameter; bundle download/parse and deployment-env discovery are separate
startup costs that the translation cache cannot remove. Production optimization
and asset-versioning changes are outside this change.

## Validation

The browser fixture blocks refresh on an unresolved gate and verifies that the
cached catalogue renders first. It records exactly one `get`, no cursor scan and
no cache rewrite, with an unrelated 1 MB record also present. Releasing the gate
replaces the cached title with fresh text. It also checks responsive layout,
language switching, navigation/login/answer persistence, corrupt cache and denied
IndexedDB. External artwork is fulfilled with local test images; this does not
verify live availability of the external sites.

One local fixture run observed cold/warm catalogue times of 2.29/1.67 seconds with
a 479,160-character cache; concurrent compilation produced substantially slower
times. These are diagnostics, not a stable performance benchmark. The reliable
assertion is that remote refresh remains pending when the cached UI becomes usable.

```
sbt 'client/testOnly *LanguageCacheStartupSpec *DigitalWorkbookCatalogSpec *WorkbookContentStorageSpec'
sbt 'coreJVM/testOnly *LanguageSerializationSpec *SyncDestinationRawSpec'
sbt 'coreJS/testOnly *LanguageSerializationSpec *SyncDestinationRawSpec'
sbt buildClientDev
node tools/dev/app-loader.test.mjs
npm run test:workbooks
node tools/dev/workbook-stylesheets.test.mjs
node tools/dev/assemble-pages.test.mjs
```

`CHROMIUM_PATH` defaults to `/usr/bin/chromium`.
`CATALOG_SCREENSHOT` optionally saves a visual-review screenshot.
