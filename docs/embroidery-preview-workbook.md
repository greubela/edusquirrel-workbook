# Embroidery workbook with geometric previews

`homepage/embroideryPreviewWorkbook/` is a separate edition with its own workbook
ID, language namespace and saved answers. The original raster-based edition,
translations and downloads remain available. Both support German and English.

## Content and ownership

The edition replaces raster recreation targets with inspectable turtle geometry,
then adds modulo/conditional patterns, recursive Koch/snowflake/star exercises and
an open-ended design task. Workbook construction and targets live under
`modules/client/src/main/scala/it/evadid/homepage/workbook/content/`.
Programs persist through the shared `ProgrammingState` model and use the regular
Eva/Snap editors.

Supplied examples and learner exercises have distinct initial states. Repeated
targets retain independent saved programs. Original custom-block illustrations
and downloadable TurtleStitch projects remain teaching resources.

## Drawing comparison and rendering

Recreation compares drawn strokes independently of pen-up travel: moving without
drawing cannot satisfy a target. Source-image travel marks must not become
stitches. Other renderer consumers can choose to compare travel as well.

Previews reuse unchanged geometry, batch graph updates and release boards on
unmount. Dense targets simplify angle annotations while retaining the drawing.
Opening/closing fullscreen should preserve the workbook's existing previews;
empty programs display a prompt instead of creating a native Snap thumbnail.
See [startup loading](browser-startup-loading.md) for runtime readiness and
[editor architecture](eva-editor-programming-state.md) for mount ownership.

Drawing comparison does not prove algorithm choice, command-limit compliance,
physical stitch spacing or machine suitability. The final workshop design still
requires TurtleStitch export and supervisor review; previews do not produce
machine files.

## Verification

Shared-platform tests cover executable conversions, including modulo and
recursion. Client tests cover target geometry, travel/stroke distinctions and
separate workbook identity. Browser checks exercise solved patterns, stable
previews and saved programs after reopen/reload.

```sh
sbt 'coreJVM/testOnly *ProgrammingStateConversionSpec' 'coreJS/testOnly *ProgrammingStateConversionSpec'
sbt 'client/testOnly *CreateEmbroideryPreviewWorkbookSpec *TurtleJsxGraphRendererSpec'
sbt buildClientDev buildWorkerDev
npm run test:workbooks -- embroidery-preview-workbook
```
