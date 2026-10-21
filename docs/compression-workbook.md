# Compression workbook: two implementations

The catalogue offers the native workbook at `homepage/compressionWorkbook/` and
retains the standalone workbook at `resources/programs/20260907Datenkompression/`.
The standalone files, scripts and materials are unchanged.

## Structure and state

The native edition reconstructs the seven source chapters using the shared
workbook application, language maps and interaction history. It includes written
questions, capacity calculators, text/image experiments and file simulations.
Materials remain downloadable from the original paths.

Shared models and algorithms live under
`modules/core/shared/src/main/scala/it/evadid/workbook/model/compression/`, with
the interaction definition in the corresponding `elements/interactionElements/compression/`
package.
Browser controls live under
`modules/client/src/main/scala/it/evadid/homepage/webElements/editor/compression/`.
The workbook factory supplies authored text and scenario configuration; editors
do not read workbook content directly. Browser image APIs stay in the client.

Written answers render inline; experiments use the regular fullscreen dialog.
Accepted values and results survive navigation/reload, invalid numeric drafts
stay local, and playback stops on unmount. Slideshow position derives from saved
history. The workbook ID remains `CompressionWorkbook`; earlier incomplete-draft
auto-generated answer IDs are not a supported migration format.

## Accuracy notes

The original lesson text is retained. Explicit notes correct these source issues:

- Base64 is encoding, not encryption. The original strings remain visible with
  that correction, including in the optional encryption chapter.
- Variable blocks and averaging illustrate information loss. Actual JPEG uses
  fixed 8×8 transforms and quantization. Block and file-size estimates are teaching
  models, not JPEG encoding or measured file sizes. Face/eye challenges use the
  original parameter thresholds; learners must assess actual visibility.
- ZIP usually compresses entries independently. Shared dictionaries across files
  are available in solid 7z/RAR modes.
- The storage scenarios use 16×1024³ bytes, correctly labeled **16 GiB**, and the
  exact source compression factors with 48 MiB archive overhead. These are authored
  assumptions, not measured compression rates. Format conversion can discard
  formatting, image layers or working information.
- Video/photo calculators consistently use decimal MB. The standalone calculators
  mix binary and decimal assumptions for a “2 GB” card.
- The supplied `Screenshot.jpg` actually contains PNG data, also in the original
  embedded widget data. This is stated in the native screenshot experiment; its
  projected size is a teaching estimate, not a generated JPEG.
- The inspector's 13,460-byte DOCX is the original illustrative assumption, not the
  measured size of the supplied material DOCX. The TXT size is measured as UTF-8.
- RLE includes multi-digit counts and Unicode byte costs. The prose source's
  simplified “two characters per tuple” is not used as a general byte calculation.

## Maintenance and validation

`tools/dev/sync-compression-source.mjs` inventories the retained HTML and language
files and generates native declarative content, scenario constants, language
entries and the exact embedded photo. Reviewed technical annotations live in
`resources/workbookresources/compression/source-corrections.json`.
`generate-compression-slides.mjs` produces the six static illustrations using the
original canvas functions offline. Neither original widget scripts nor an iframe
are loaded by the native client.

`generate-compression-parity.mjs` creates reference fixtures from the original
storage operations. Shared JVM/JS tests compare behavior against those fixtures;
content checks compare the native chapters with retained sources. Browser checks
cover native controls, persistence, playback cleanup and responsive layout.

```sh
node tools/dev/sync-compression-source.mjs
node tools/dev/generate-compression-parity.mjs
node tools/dev/generate-compression-slides.mjs
sbt 'coreJVM/testOnly *CompressionExperimentSpec' 'coreJS/testOnly *CompressionExperimentSpec'
sbt 'client/testOnly *CreateCompressionWorkbookSpec' 'client/fastLinkJS'
node --test tools/dev/compression-workbook-content.test.mjs tools/dev/workbook-stylesheets.test.mjs
node --test tools/dev/compression-workbook.test.mjs
```
