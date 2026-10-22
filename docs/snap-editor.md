# Snap editor design

## State and conversion

- Snap, Python and Java tabs share one `Var[ProgrammingState]`.
- `ProgrammingStateSnapXml` retains project XML; `removeBloatFromXml` removes generated previews, preserving authored assets.
- [ProgrammingStateSnapScalaModel](snap-scala-model.md) supports lossless typed editing, including native Snap blocks.
- Python/Java conversion supports a subset of Snap. Preserve unsupported blocks in the saved project; never replace it with a lossy conversion.
- Standard uPickle codecs retain the active representation. Development schema changes require resetting saved answers.

## Loading and ownership

- The Snap facade shares runtime readiness; preloading creates no IDE or Morphic world. See [startup loading](browser-startup-loading.md).
- Each mounted editor owns its canvas, IDE and world. Unmount flushes pending edits and destroys native handles; late callbacks are ignored.
- External state changes use `loadProgramIfChanged`; consumers observe the central Var.
- Static previews use temporary IDEs and preserve Morphic's shared keyboard state.

## Human language

- Editors and previews receive `Signal[HumanLanguage]`, normally `fullInfo.signals.currentLanguage`. Constant signals work for standalone use; compatibility overloads default to English.
- The facade loads and caches dictionaries before drawing. Explicit locale mapping supports all eight workbook languages, including `ua`, `dk` and `ru`; do not derive codes from `nameAbbr`.
- Language changes redraw previews even when XML is unchanged. Live editors commit slot text and rebuild from complete project XML, preserving content and palette configuration.
- The workbook signal overrides Snap's saved language preference. Learner-authored names and text remain unchanged.

## Presentation and execution

- `ProgrammingEditorPalette` selects exercise libraries and available blocks.
- Presentation belongs in `homepage/css/editors.css` and shared CSS.
- Review bundled `gui.js` category/layout patches when upgrading Snap.
- [Execution panels](snap-turtle-command-execution-overview.md) observe the central Var and own execution services; the editor owns no execution service.

## Verification

```sh
sbt 'coreJVM/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'coreJS/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'client/testOnly *SnapCodeEditor*Spec *SnapProjectXmlSyncSpec *SnapPythonPopupSpec *SnapLanguageSpec *EvaEditorSpec'
npm run test:workbooks -- snap-preview snap-language editor-layout
```
