# Snap editor state and lifecycle

The embedded TurtleStitch/Snap editor shares a `Var[ProgrammingState]` with Eva's Python and Java tabs. Shared representations and conversions live in [ProgrammingState.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/ProgrammingState.scala); the browser boundary is [SnapCodeEditor.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/SnapCodeEditor.scala).

## Source state and conversion

Snap edits publish project XML as `ProgrammingStateSnapXml`. Regenerated preview/pen images are removed by `removeBloatFromXml`; scripts and authored costumes are retained. Python, Java and VM expression states have their own representations. Conversion uses the shared supported programming subset, not every native Snap block.

The shared [full Snap Scala model](snap-scala-model.md) provides `ProgrammingStateSnapScalaModel` for lossless project conversion, typed inspection and immutable edits, including native blocks outside the Turtle subset. Existing workbooks keep their XML representation; typed states are also supported by the editors and state serializers.

`ProgrammingExercise` uses the standard implicit uPickle state serializer, preserving the active representation. Development schema changes require resetting stored answers; no historical formats or migration readers are maintained. [SnapTurtlePythonBridge.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapTurtlePythonBridge.scala) validates Python-to-Snap conversion; [SnapProgramDerivation.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/toRefactor/SnapProgramDerivation.scala) supplies the legacy derivation/popup path. Unsupported blocks can stay in saved XML even when a conversion is unavailable. Do not replace a project with a lossy derived subset.

## Runtime readiness and preview

Startup preheats bundled Snap dependencies before an editor opens. Consumers
share readiness; warmup creates no editor or Morphic world. Mount ownership
discards delayed work after replacement or unmount. See
[browser startup loading](browser-startup-loading.md).

Static previews use temporary native IDEs; the mounted editor owns its live IDE.
Preview generation preserves the shared Morphic keyboard state, and presentation
belongs in `homepage/css/editors.css`.

## Human language

`SnapCodeEditor` and `SnapPreviewEditor` accept a `Signal[HumanLanguage]`.
Workbook renderers pass `fullInfo.signals.currentLanguage` through Eva's tab
factory to the selected Snap editor and separately to its preview. Standalone
callers may use a constant `Val(language)`; the compatibility overloads use English.

The Snap facade loads the bundled dictionary before rendering. Locale mapping is
explicit: Ukrainian uses `ua`, Danish uses `dk`, and Russian uses `ru` regardless
of its shared abbreviation in `HumanLanguage`. All eight workbook languages are
supported. Dictionaries are cached together rather than unloaded when switching.
Closed mounts and superseded language requests discard late completions.

Changing the language redraws a static preview even when its XML is unchanged.
A live editor commits pending slot text and rebuilds localized Morphic controls
from its complete live project XML, retaining custom blocks, variables, assets
and the exercise palette. The workbook signal takes precedence over Snap's
persistent standalone language preference. Native labels are translated; names
and text authored inside the learner's project remain their own content.

## Live IDE synchronization

The editor mounts into its own canvas. The live IDE exists only for that mount;
unmount flushes applicable pending Snap state and destroys the native world.
Later changes to the central Var use `loadProgramIfChanged`. Native XML rendering
metadata avoids unnecessary native redraws, but consumers observe the central
Var; there is no special learner-edit callback. Closing the dialog unmounts its
content, and reopening constructs an editor from the saved answer.

Editor implementations and native adapters are located in
`modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/`.

## Palette, rendering and execution

`ProgrammingEditorPalette` chooses the default, Python-compatible or beginner turtle palette for an exercise. Library configuration controls native categories, explicit blocks and variable controls. Canvas category dimensions and colors come from CSS; the DOM layout lives in `homepage/css/editors.css` and shared component styles.

The bundled TurtleStitch `gui.js` has custom category/layout changes. Review those patches when updating the bundled distribution. Eva provides the active language tabs and turtle preview. The legacy Python popup and stage toolbar helpers still exist, but are not currently mounted by `SnapCodeEditor`; their presence does not imply visible controls in the active editor.

The asynchronous Pyodide command extraction service is described in [Snap command execution](snap-turtle-command-execution-overview.md). The downstream turtle panel reads the central Var and delegates to that service; the editor does not own an execution service.

## Verification

```sh
sbt 'coreJVM/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'coreJS/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'client/testOnly *SnapCodeEditor*Spec *SnapProjectXmlSyncSpec *SnapPythonPopupSpec *SnapLanguageSpec *EvaEditorSpec'
npm run test:workbooks -- snap-preview snap-language editor-layout
```

These checks cover conversion, XML cleanup, live synchronization and editor geometry. Supported-subset and runtime behavior should be checked separately when extending the palette.
