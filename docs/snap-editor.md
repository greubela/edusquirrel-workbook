# Snap editor design

## State and conversion

- Snap uses a `Var[ProgrammingStateSnapScalaModel]` view of Eva's central answer Var; only meaningful model changes are published.
- Model comparison ignores generated media and XML spelling; selectors, inputs, authored assets and script layout remain meaningful.
- [ProgrammingStateSnapScalaModel](snap-scala-model.md) supports lossless typed editing, including native Snap blocks.
- Python/Java conversion supports a subset of Snap. Preserve unsupported blocks in the saved project; never replace it with a lossy conversion.
- The typed-state codec excludes thumbnails and pen trails from history; authored costumes and opaque project data remain.

## Loading and ownership

- The Snap facade shares runtime readiness; preloading creates no IDE or Morphic world. See [startup loading](browser-startup-loading.md).
- Each mounted editor owns its canvas, IDE and world. Unmount flushes pending edits and destroys native handles; late callbacks are ignored.
- External state changes use `loadProgramIfChanged`; consumers observe the central Var.
- Static previews use temporary IDEs and preserve Morphic's shared keyboard state.

## Human language

- Editors and previews receive `Signal[HumanLanguage]`, normally `fullInfo.signals.currentLanguage`. Constant signals work for standalone use; compatibility overloads default to English.
- The facade loads and caches dictionaries before drawing. Explicit locale mapping supports all eight workbook languages, including `ua`, `dk` and `ru`; do not derive codes from `nameAbbr`.
- Language changes redraw previews. Live editors use native `reflectLanguage` on the existing IDE/world; Snap reloads scenes, then exercise libraries are reapplied.
- Serialization runs with an English translator and restores the display locale in `finally`, including native refresh exports. Language-only refreshes produce no answer update.
- The workbook signal overrides Snap's saved language preference. Learner-authored names and text remain unchanged.

## Presentation and execution

- `ProgrammingEditorPalette.layout` selects exercise libraries and available blocks. Both regular and turtle renderers use `SnapCodeEditorConfig.forPalette` to translate that shared layout into native tabs; Embroidery therefore exposes its declared Motion, Pen, Embroidery and Control categories. Mixed layouts use one tab with category buttons hidden.
- Presentation belongs in `homepage/css/editors.css` and shared CSS.
- Review bundled `gui.js` category/layout patches when upgrading Snap.
- [Execution panels](snap-turtle-command-execution-overview.md) observe the central Var and own execution services; the editor owns no execution service.

## Exercise-specific palettes

Use `ProgrammingEditorPalette.Custom` directly in an exercise's existing
`availablePalette` field. For example, a recreate-shape exercise can offer only
start, movement and repeat blocks:

```scala
TurtleRecreateShapeInteraction(
  elementId = "draw-square",
  initProgram = initialProgram,
  desiredResult = targetShape,
  availablePalette = ProgrammingEditorPalette.Custom(
    PaletteLayout.Mixed(List("receiveGo", "forward", "turn", "doRepeat"))
  ),
  maxCommandUsage = MaxCommandUsage()
)
```

`PaletteLayout.CatalogTabs(List(PaletteTab.Motion, PaletteTab.Control))` selects
whole shared-catalog categories. `NativeSnap` selects the native categories and
is not Python-compatible. `Mixed(Nil)` is an explicit empty palette. Category
lists must be nonempty and unique, avoiding Snap's empty-list fallback to its
full native palette.

Mixed entries use **Snap selectors** from `SnapPaletteCatalog`, not canonical
method names: the right-turn block is `turn`, not `right`. Uncatalogued selectors
are rejected when the custom palette is constructed or deserialized. Supporting
additional native selectors requires adding them to the shared catalogue and
reviewing their conversion support first. Python compatibility is derived from
the selected layout, not supplied by the exercise author.

Custom layouts use the same `SnapCodeEditorConfig.forPalette` mapping as presets;
the exercise factory stores them in both JSON and constructor-style definitions.
Palette selection controls offered blocks, not acceptance of submitted programs.
Existing program blocks and separately defined grading constraints remain distinct.

## Command usage limits

Recreate-shape exercises use `maxCommandUsage: MaxCommandUsage` with independent maps:

```scala
maxCommandUsage = MaxCommandUsage(
  occurrencesInCode = Map("forward" -> 1),
  callsDuringExecution = Map("forward" -> 4, "right" -> 4)
)
```

This permits one authored forward block/call, reused up to four times during
execution. A missing entry is unlimited for that measure; zero forbids usage,
and negative maxima are invalid. The default `MaxCommandUsage()` adds no limits.
Each map entry creates its own required, always-running `TurtleCommandUsageGrader`,
with a separate stable identity and grading result. The shape grader remains independent.

Source occurrences include unused function bodies and both conditional branches;
execution counts include only calls actually made. Source-only checks do not execute
the program. Execution uses the existing bounded turtle analysis and requires completion:
a failed or truncated execution produces a grading error, never a successful limit result.

Keys currently identify turtle commands. Canonical names, supported aliases and drawing
selectors such as `fd`, `forward`, `turn` and `gotoXY` resolve through `TurtleCommand`.
Control blocks such as `doRepeat` are not yet supported as constrained elements and are
rejected rather than silently ignored. `MaxCommandUsage` keeps the two maps independent
of the current turtle grader so additional palette-element counters can be added later.
Palette availability and usage grading remain separate concerns.

## Verification

```sh
sbt 'coreJVM/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'coreJS/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'client/testOnly *SnapCodeEditor*Spec *SnapProjectXmlSyncSpec *SnapPythonPopupSpec *SnapLanguageSpec *EvaEditorSpec'
npm run test:workbooks -- snap-preview snap-language editor-layout
```
