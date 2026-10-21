# Workbook catalogue and language-cache startup

`homepage/workbooks/index.html` mounts the public Laminar catalogue. The shared
`DigitalWorkbookCatalog` registry supplies native page/container IDs and workbook
factories, keeping navigation and startup consistent. It also lists standalone
editions, while PDF/ZIP materials remain in the classic catalogue. Selecting a
workbook uses its normal login/local-session flow; the user menu returns to the
catalogue. Labels react to German/English language changes.

An `inProgress` label tracks content review, independently of editor/backend
availability. See [migration scope](digital-workbook-migration.md) for remaining
content and [startup loading](browser-startup-loading.md) for runtime preheating.

## Language cache

Startup mounts before restoring cached translations; labels update through
signals. The IndexedDB record `EvaDidCacheDb/cache/tripleCache` stores
`ParsedTriples` in its established wire format. Restoration installs cached values
without rewriting them. Remote refresh runs in a later task, merges by content
identifier/language, and persists once, preserving unrelated entries.

Unavailable or corrupt caches fall back to sources. Refresh failure cannot undo
successful cached startup. Transactions own their connections and close them on
completion or version changes; serialization remains in the content layer.
Immediate and full-load strategies retain their separate semantics.

## Verification

Catalogue browser tests cover navigation, saved answers, stalled refresh, corrupt
storage and translation replacement. Run the [standard checks](core-package-unit-tests.md)
and `npm run test:workbooks -- workbook-catalog`. The startup guarantee is usability
while refresh is pending, rather than a fixed timing threshold. `CATALOG_SCREENSHOT`
optionally saves a review capture.
