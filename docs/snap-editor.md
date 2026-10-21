# Snap editor state and lifecycle

The embedded TurtleStitch/Snap editor shares a `Var[ProgrammingState]` with Eva's Python and Java tabs. Shared representations and conversions live in [ProgrammingState.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/ProgrammingState.scala); the browser boundary is [SnapCodeEditor.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/SnapCodeEditor.scala).

## Source state and conversion

Snap edits publish project XML as `ProgrammingStateSnapXml`. Regenerated preview/pen images are removed by `removeBloatFromXml`; scripts and authored costumes are retained. Python, Java and VM expression states have their own representations. Conversion uses the shared supported programming subset, not every native Snap block.

The shared [full Snap Scala model](snap-scala-model.md) provides `ProgrammingStateSnapScalaModel` for lossless project conversion, typed inspection and immutable edits, including native blocks outside the Turtle subset. Existing workbooks keep their XML representation; typed states are also supported by the editors and state serializers.

`ProgrammingExercise` uses the standard implicit uPickle state serializer, preserving the active representation. Development schema changes require resetting stored answers; no historical formats or migration readers are maintained. [SnapTurtlePythonBridge.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapTurtlePythonBridge.scala) validates Python-to-Snap conversion; [SnapProgramDerivation.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/toRefactor/SnapProgramDerivation.scala) supplies the legacy derivation/popup path. Unsupported blocks can stay in saved XML even when a conversion is unavailable. Do not replace a project with a lossy derived subset.

## Runtime readiness and preview

Scala.js `StartupPreheating` starts `SnapRuntimeLoading` when a turtle workbook starts, before an editor is opened. `BrowserScriptLoading` preloads all 22 dependencies concurrently and executes them in their required order, stopping after a failed dependency. The final `fullturtle.js` compatibility bridge installs its API and serializer patch; it no longer schedules or downloads scripts. Consumers share one readiness future. Warmup creates no Morphic world or editor. `SnapPreviewEditor` and `SnapCodeEditor` await the implementation's `whenReady` hook, and mount ownership discards delayed callbacks after replacement or unmount. See [browser startup loading](browser-startup-loading.md).

The static preview exports `ScriptsMorph.scriptsPicture()` from a temporary IDE; the mounted editor owns its live IDE. Preview canvas bitmap dimensions come from the exported script image, and its presentation/aspect ratio comes from `homepage/css/editors.css`, without inline width/height styles. Preview rendering preserves the shared Morphic keyboard contents and selection. Empty-canvas diagnostics distinguish actual primitive/custom block instances from the empty `<blocks>` containers present in every project.

CodeMirror's shared Scala.js module loader starts warming at application startup. The
warmup request has low fetch priority, creates no editor and never delays startup.
Editors share the pending/completed request. Failure leaves the editable fallback
available and permits a later retry.

## Live IDE synchronization

The editor mounts into its own canvas. The live IDE exists only for that mount;
unmount flushes applicable pending Snap state and destroys the native world.
Later changes to the central Var use `loadProgramIfChanged`. Native XML rendering
metadata avoids unnecessary native redraws, but consumers observe the central
Var; there is no special learner-edit callback. Closing the dialog unmounts its
content, and reopening constructs an editor from the saved answer.

`SnapCodeEditorImpl` also exposes `forceLoadProgram`, but the current fullscreen-open path uses `loadProgramIfChanged`. Configuration and implementation adapters remain in `SnapEditor/toRefactor/`.

## Palette, rendering and execution

`ProgrammingEditorPalette` chooses the default, Python-compatible or beginner turtle palette for an exercise. Library configuration controls native categories, explicit blocks and variable controls. Canvas category dimensions and colors come from CSS; the DOM layout lives in `homepage/css/editors.css` and shared component styles.

The bundled TurtleStitch `gui.js` has custom category/layout changes. Review those patches when updating the bundled distribution. Eva provides the active language tabs and turtle preview. The legacy Python popup and stage toolbar helpers still exist, but are not currently mounted by `SnapCodeEditor`; their presence does not imply visible controls in the active editor.

The asynchronous Pyodide command extraction service is described in [Snap command execution](snap-turtle-command-execution-overview.md). The downstream turtle panel reads the central Var and delegates to that service; the editor does not own an execution service.

## Verification

```sh
sbt 'coreJVM/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'coreJS/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'client/testOnly *SnapCodeEditor*Spec *SnapProjectXmlSyncSpec *SnapPythonPopupSpec *EvaEditorSpec'
npm run test:workbooks -- snap-preview editor-layout
```

These checks cover conversion, XML cleanup, live synchronization and editor geometry. Supported-subset and runtime behavior should be checked separately when extending the palette.
