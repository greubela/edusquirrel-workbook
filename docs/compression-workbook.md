# Native compression workbook

Open `homepage/compressionWorkbook/` or choose the compression workbook in the
shared digital workbook catalogue. This page uses the same Scala.js/Laminar
startup, `WorkbookFactory`, section navigation, language maps and answer history
as the embroidery workbook. The standalone lab in
`resources/programs/20260907Datenkompression/` remains available separately.

The existing German chapters, written questions and sorting demonstrations are
retained. The former widget placeholders now use native activities:

| Activity | Shared model / existing interaction | Client UI |
| --- | --- | --- |
| Video size and card capacity | `VideoBudget` | Numeric experiment controls |
| One changed password bit | `TextBits` | Byte/bit buttons |
| Changed monochrome image | Existing `BinaryPixelInteraction` | Existing pixel renderer |
| RLE of repeated symbols and prose | `RunLengthText`, shared Unicode handling | Text, runs and restored text |
| Step-by-step word dictionary | `DictionaryText` | Steps, dictionary, references and restored text |
| Match files to lossless methods | Existing `SortingReasonInteraction` | Existing sorting and justification UI |
| Separate brightness/color blocks; joint block size | `ImageBlocks` | Same-origin photo, block selectors and canvas |
| Text versus Word structure | Existing HTML instruction renderer | Actual supplied DOCX entries, metadata and matching TXT download |
| Individual files versus archive | `ArchiveBudget` | Editable size, transfer-rate and per-file overhead assumptions |
| Three storage packages | `StorageStudy` | Proportional area chart, file table and card-space calculation |

New experiment definitions use `CompressionExperimentInteraction` in shared core.
`CompressionExperimentRenderer` creates one bound state through the existing
`InteractionVariable` and synchronization control. It shows a live preview and
opens `CompressionEditor` with that same state. Closing, reopening, navigating
between chapters and reloading retain accepted settings; invalid numeric drafts
are local to the editor and do not overwrite a valid saved answer. Controls obey
the workbook's disabled state. Algorithms and definition/value codecs are shared
between JVM and JavaScript; browser APIs are confined to the client.

Experiments have explicit IDs. The replaced display elements' automatic ID slots
are retained so existing written-answer IDs do not shift. The workbook ID and
chapter IDs remain unchanged. Existing factory and renderer registries gain only
an additional interaction registration. Experiment definitions can use different
initial texts, image resources and storage packages.

Sizes are explained in the learner UI. Video/storage quantities use decimal MB.
RLE counts UTF-8 symbol bytes and decimal count digits, excluding display
punctuation; this illustrates costs without claiming a complete byte format.
Dictionary references preserve whitespace and include the dictionary in their
stated teaching cost. Image experiments sample the supplied photo at 64 × 64,
average Y/Cb/Cr channels, and count one byte per retained channel sample. These
are teaching models, not JPEG/ZIP implementations or measured compression rates.
The archive exercise includes unchanged payload plus configurable overhead and
can demonstrate either faster or slower transfer. Storage packages are authored
examples with stated sizes, independent of the standalone lab's scenarios.

The material links use the existing source files. The TXT comparison file contains
exactly the visible text extracted from the supplied Word document; the comparison
lists that document's actual package entries and example metadata. The former
Base64-as-encryption illustration is replaced by an explicitly symbolic example.

Dedicated `compression-editor.css` uses shared design tokens and is loaded by
the workbook entry pages. No standalone widget scripts, iframe, direct local
storage store or new runtime dependency is needed.

Validation:

```sh
sbt 'coreJVM/testOnly *CompressionExperimentSpec' 'coreJS/testOnly *CompressionExperimentSpec'
sbt 'client/testOnly *CreateCompressionWorkbookSpec' 'client/fastLinkJS'
node --test tools/dev/compression-workbook-content.test.mjs
node --test tools/dev/workbook-stylesheets.test.mjs
node --test tools/dev/compression-workbook.test.mjs
```

The browser test serves the freshly linked client bundle, selects guest mode and
checks edits, fullscreen reopening, independent RLE exercises, dictionary steps,
image controls, material links, storage selection, reload persistence and mobile
layout without a backend connection.
