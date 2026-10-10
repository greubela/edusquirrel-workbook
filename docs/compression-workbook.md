# Compression workbook: two implementations

The catalogue offers the native workbook at `homepage/compressionWorkbook/` and
retains the standalone workbook at `resources/programs/20260907Datenkompression/`.
The standalone files, scripts and materials are unchanged.

The native version uses the same Scala.js/Laminar application, `WorkbookFactory`,
section navigation, language maps, interaction synchronization and answer history
as the embroidery workbooks. It reconstructs all seven original chapters in order,
including the optional encryption chapter. It contains all 41 written questions,
two pseudocode skeletons, the conditional introductory opinion and justification,
seven final agree/disagree questions, nine hints, all 17 original widgets and both
capacity calculators. The final comparison displays the saved introductory
technical recommendation. Materials remain downloadable from the original paths.

| Original activity | Native implementation |
| --- | --- |
| Video and photo capacity | `VideoBudget`, `PhotoBudget`; decimal MB calculator controls |
| RLE examples, prose and three sticky challenges | `RunLengthText`; Unicode runs, restored text and explicit cost model |
| Dictionary animation | `DictionaryText`; first occurrences literal, repeated words `Wn`, word steps, playback, speed and expandable dictionary |
| Compression classifications and reasons | Existing `SortingReasonInteraction`, original four files/options in each activity |
| Password and monochrome bit changes | `BitComparison`; original password, bit sequence and 128×128 ring image |
| Six JPEG explanation slides | Existing `Slideshow` and `ImageSlide`; illustrations generated from original canvases |
| Brightness/color and joint blocks | `ImageBlocks`; exact embedded 960×640 source image, independent Y/Cb/Cr means or RGB means |
| Text versus screenshot | Original full-resolution screenshot, actual source file sizes, independent blocks and zoom |
| Text versus DOCX inspector | Original letter, formatted version, expandable internal package descriptions |
| Ten documents versus one archive | `TransferSimulation`; exact original texts, visible process steps, playback and explanatory FAQ |
| Storage overview | `FileOverview`; exact original scenarios, metadata, logical grouped-file counts and area display |
| Tutorial plus three independent simulators | `FileSimulation`; conversion, lossless/lossy operations, archive selection, metadata blocking, step counts, tutorial checklist and completion |

`CompressionExperimentInteraction` defines shared persisted values and their
codecs. `CompressionExperimentRenderer` creates bound state through the existing
synchronization control. `CompressionEditor` and `CompressionActivitiesEditor`
provide native controls. Written answers and opinions render inline; experiments
open in the regular fullscreen dialog. Settings, answers, challenges and simulator
results survive reopening, chapter navigation and reload. Playback stops when the
dialog closes. Invalid numeric drafts do not overwrite accepted values. Shared
models and algorithms run on both JVM and JavaScript; browser image APIs remain in
the client. Authored inspector text, formatted content, comparison sizes and archive
files are passed through shared exercise configuration, rather than read from
workbook content inside the editor. Existing slideshow navigation now derives its position from saved
transition history, also restoring it across chapter changes and reload.

Source element IDs identify questions and widgets; the workbook ID remains
`CompressionWorkbook`. The new chapter IDs match the source chapter inventory.
The prior incomplete native draft's auto-generated answer IDs are not a migration
format for this complete reconstruction.

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

`generate-compression-parity.mjs` executes the original storage operations offline
and generates 420 input states with three expected operation results each. Shared
JVM/JS tests compare all 1,260 outcomes, including sequential changes and repeated
operations. Inventory tests compare chapters, answers, choices, hints, materials
and lesson text with the retained original. Browser validation covers every chapter,
all activity types, full-resolution canvases, tutorial completion, independent
simulators, errors, persistence, slideshow navigation and mobile layout.

```sh
node tools/dev/sync-compression-source.mjs
node tools/dev/generate-compression-parity.mjs
node tools/dev/generate-compression-slides.mjs
sbt 'coreJVM/testOnly *CompressionExperimentSpec' 'coreJS/testOnly *CompressionExperimentSpec'
sbt 'client/testOnly *CreateCompressionWorkbookSpec' 'client/fastLinkJS'
node --test tools/dev/compression-workbook-content.test.mjs tools/dev/workbook-stylesheets.test.mjs
node --test tools/dev/compression-workbook.test.mjs
```
