# Snap editor state and lifecycle

The embedded TurtleStitch/Snap editor shares a `Var[ProgrammingState]` with Eva's Python and Java tabs. Shared representations and conversions live in [ProgrammingState.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/ProgrammingState.scala); the browser boundary is [SnapCodeEditor.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/SnapCodeEditor.scala).

## Source state and conversion

Snap edits publish project XML as `ProgrammingStateSnapXml`. Regenerated preview/pen images are removed by `removeBloatFromXml`; scripts and authored costumes are retained. Python, Java and VM expression states have their own representations. Conversion uses the shared supported programming subset, not every native Snap block.

`ProgrammingExercise` writes the tagged `PROGRAMMING_STATE_V2` format, preserving the active representation. It still reads legacy `SNAP_XML_V1`, raw project XML and Python payloads. [SnapTurtlePythonBridge.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapTurtlePythonBridge.scala) validates Python-to-Snap conversion; [SnapProgramDerivation.scala](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/toRefactor/SnapProgramDerivation.scala) supplies the legacy derivation/popup path. Unsupported blocks can stay in saved XML even when a conversion is unavailable. Do not replace a project with a lossy derived subset.

## Runtime readiness and preview

`fullturtle.js` loads Snap's scripts sequentially and exposes `TurtleStitchPoCReady`. A cached client can mount previews before `IDE_Morph` is defined. Creating a world at that point fails and leaves the preview empty; script loading alone does not retry the mount. Both `SnapPreviewEditor` and `SnapCodeEditor` now use the implementation's `whenReady` hook before rendering. Mount generations discard delayed callbacks after a state replacement, unmount or fullscreen close/reopen. A loading or rendering failure is shown as an accessible alert.

The static preview exports `ScriptsMorph.scriptsPicture()` from a temporary IDE; the fullscreen editor retains its live IDE. Preview canvas bitmap dimensions come from the exported script image, and its presentation/aspect ratio comes from `homepage/css/editors.css`, without inline width/height styles. Preview rendering preserves the shared Morphic keyboard contents and selection. Empty-canvas diagnostics distinguish actual primitive/custom block instances from the empty `<blocks>` containers present in every project.

## Live IDE synchronization

The editor mounts into its own canvas and retains the IDE across fullscreen sessions. Snap-originated XML is acknowledged before publication so the state observer does not reload an edit back into the same IDE. Later state changes use `loadProgramIfChanged`; pending edits are flushed before unmounting and fullscreen close. Morphic cycles pause when closed and resume when opened. Fullscreen open loads changed state and fits the editor to its container.

`SnapCodeEditorImpl` also exposes `forceLoadProgram`, but the current fullscreen-open path uses `loadProgramIfChanged`. Configuration and implementation adapters remain in `SnapEditor/toRefactor/`.

## Palette, rendering and execution

`ProgrammingEditorPalette` chooses the default, Python-compatible or beginner turtle palette for an exercise. Library configuration controls native categories, explicit blocks and variable controls. Canvas category dimensions and colors come from CSS; the DOM layout lives in `homepage/css/editors.css` and shared component styles.

The bundled TurtleStitch `gui.js` has custom category/layout changes. Review those patches when updating the bundled distribution. Eva provides the active language tabs and turtle preview. The legacy Python popup and stage toolbar helpers still exist, but are not currently mounted by `SnapCodeEditor`; their presence does not imply visible controls in the active editor.

The asynchronous Pyodide command extraction service is described in [Snap command execution](snap-turtle-command-execution-overview.md). `SnapCodeEditor.getCurrentTurtleCommands()` captures and flushes the live project before delegating to that service.

## Verification

```sh
sbt 'coreJVM/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'coreJS/testOnly *ProgrammingState*Spec *SnapTurtlePythonBridgeSpec *SnapTurtleCatalogSpec'
sbt 'client/testOnly *SnapCodeEditor*Spec *SnapProjectXmlSyncSpec *SnapPythonPopupSpec *EvaEditorSpec'
npm run test:workbooks -- snap-preview editor-layout
```

These checks cover conversion, XML cleanup, live synchronization and editor geometry. Supported-subset and runtime behavior should be checked separately when extending the palette.
