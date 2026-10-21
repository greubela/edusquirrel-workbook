# QR creation interaction

For the shared model/renderer/editor responsibilities and dependency direction, see [Interaction architecture](workbook-interaction-architecture.md).

`CreateQrCodeInteraction` follows the existing turtle interaction: a card line shows
an editor button, the saved symbol and requirement feedback. Its fullscreen
`QrCodeEditor` takes text and regenerates a scannable SVG as the learner types.
The Test workbook contains a sample exercise requiring at least 32 payload bytes.

```scala
import it.evadid.workbook.elements.interactionElements.qr.*
import it.evadid.workbook.model.qr.*

CreateQrCodeInteraction(
  elementId = "summary-create-qr",
  requirements = QrCodeRequirements(minBytes = 32),
  initialCode = QrCode.fromText("")
)
```

Requirements can also specify a maximum byte count, an exact version, an exact
mask and a minimum correction level. `evaluate` returns individual criteria;
`isSatisfiedBy` and the interaction's `isPassed` expose aggregate completion.
Shared interactions have generic grader/scaffolder contracts, but this interaction
currently exposes its own requirement evaluator and renders feedback directly.
Its requirement results are not automatically attached to `gradingElements`; see
[the current grading integration](workbook-interaction-architecture.md#grading-and-scaffolding).

## Shared model

The model under `it.evadid.workbook.model.qr` implements ISO/IEC 18004 QR Model 2
byte mode, versions 1–40 and correction levels L/M/Q/H, without a runtime QR
library. Other compression modes, Micro QR and structured append are outside
this model.

`QrCode.content` returns a defensive copy of the payload bytes. Value equality
and hashing include the bytes and resolved configuration. Persistence stores the
payload and configuration, not redundant module matrices. Serialization rejects
invalid configurations and oversized payloads.

Encoding can select version/mask automatically or preserve an explicit
configuration. Text uses UTF-8 with an ECI marker; byte requirements count the
payload, excluding encoding headers and correction data.

The editor's version/mask selectors default to automatic selection. Feedback
always shows the resolved version and mask. Oversized drafts show an error and
hide the stale preview; they never replace the last valid saved interaction value.
Closing and reopening restores that saved value. SVG includes the required
four-module quiet zone and CSS uses dark region colors on a light background. The legend distinguishes structure, mask/correction metadata, encoding headers, payload/padding and parity.
UI labels are in the existing English/German basic language maps; layout, color
and dimension rules are CSS-based and use the shared tokens.

## Verification and reference interaction

The reference interaction is exercise 1 at [LucasQR, Zusammenfassung](https://evadid.it/LucasQR/#zusammenfassung). The implementation provides the requested text-to-finished-code interaction on the current turtle/editor architecture. Exact visual and behavioral parity with the legacy page has not been verified.

The fixture generator `tools/dev/generate-qr-fixtures.py` uses test-only
python-qrcode 8.2 and qrcodegen 1.8.0. Committed fixtures compare complete matrix
checksums for every version/correction combination, full-capacity payloads and
UTF-8 automatic-mask examples. Runtime Scala uses neither reference library.
Run `coreJVM/testOnly *QrCodeSpec`, `coreJS/testOnly *QrCodeSpec`, and
`client/testOnly *QrCode*Spec *CreateTestWorkbookSpec` for targeted checks.

The browser integration test uses test-only jsQR to decode the actual rendered
SVG back to Unicode text, and checks grading, invalid drafts, reopening and
responsive layout. Run `sbt buildJS`, `npm ci --ignore-scripts`, then
`node --test tools/dev/qr-interaction.test.mjs` (requires Chromium; override
`CHROMIUM_PATH` when necessary). It serves all assets locally and aborts external
requests; it does not use the backend or an online account.

## Region colors

`QrCode.regions` and `regionAt(x, y)` identify each module's semantic purpose,
independent of mask choice and styling. Header bits are tracked through block
interleaving, including bytes shared by header and payload. Format information
stores the selected mask and error-correction level; the mask is applied to all
non-function modules rather than occupying a separate rectangular area.

Region colors are CSS tokens in `homepage/css/generic/colors.css`, shared by
SVG paths and legend swatches. Override them there; use the common dark token
for a monochrome display. Keep high contrast against light modules and preserve
the quiet zone so codes remain scannable. Tests verify that region styling covers
the symbol and that the rendered result still decodes.
