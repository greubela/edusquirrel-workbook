# QR creation interaction

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
The current workbook architecture has no generic pass-condition interface, so
this interaction exposes its own evaluator and renders its feedback directly.

## Shared model

The model under `it.evadid.workbook.model.qr` implements ISO/IEC 18004 QR Model 2
byte mode, versions 1–40 and correction levels L/M/Q/H, without a QR library at
runtime. It builds byte segments, terminators and padding; Reed–Solomon parity
in GF(256) with polynomial 0x11D; block interleaving; function patterns; BCH format
and version information; zigzag placement; all eight masks and penalty selection.
Ties choose the lowest mask number. Numeric/alphanumeric/Kanji compression,
Micro QR and structured append are outside this initial byte-mode model.

`QrCode.content` returns a defensive copy of the payload bytes. Value equality
and hashing include the bytes and resolved configuration. Persistence stores the
payload and configuration, not redundant module matrices. Serialization rejects
invalid configurations and oversized payloads.

`QrCode.encode(bytes)` chooses the smallest fitting version and best mask.
Optional version/mask arguments force those choices. `QrCode(bytes, config)`
reconstructs an exact symbol; `withMask`, `maskPenalties`, `withBestMask` and
`modules` support future explanatory exercises. Coordinates are `modules(y)(x)`.
`QrCode.fromText` encodes UTF-8 and inserts ECI assignment 26. Byte requirements
count the payload only, excluding ECI, headers, padding and correction bytes.

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

`colors.css` defines `--color-qr-finder`, `--color-qr-timing`,
`--color-qr-alignment`, `--color-qr-format`, `--color-qr-version`,
`--color-qr-fixed-dark`, `--color-qr-encoding`, `--color-qr-data`,
`--color-qr-error-correction` and `--color-qr-remainder`. These reuse the existing
palette and style both SVG paths and legend swatches. Light modules, separators
and the four-module quiet zone retain `--color-qr-light`. Set all region colors
to `var(--color-qr-dark)` for a monochrome display. Custom colors should retain
high contrast with the light modules to preserve scanning.

Color coverage is checked for every version 1–40: shared tests cover all correction
levels and both header modes, and renderer tests ensure colored paths partition
the dark modules exactly. The browser test decodes every version with the default
palette (cycling all correction levels and masks), then verifies every region's
CSS override and matching legend swatch, including alignment/version information
when those regions exist.
