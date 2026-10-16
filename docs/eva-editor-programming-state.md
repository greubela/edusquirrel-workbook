# Eva Editor: tabs and feature modules

`EvaEditor` composes tabs and optional feature modules around one supplied `Var[ProgrammingState]`. It owns navigation, conversion errors and generic lifecycle coordination. It contains no Snap, CodeMirror, Java-editor or turtle-runtime branches. Default editor construction lives in `EvaEditorConfig.defaultEditorFor`; a caller can supply a tab factory for another editor or for tests.

## Editor adapters

Each standalone editor supplies an `EvaEditorTab[T]` adapter beside its own implementation:

- `CodeMirrorEditor.pythonEditorForEva` binds a raw Python string.
- `SnapCodeEditor.forEvaTab` retains XML/typed Snap state and legacy floating data, and flushes the live IDE when capturing its source.
- `JavaFunctionBasedEditor.forEvaTab` binds Java source, including unfinished classes, and owns Java-specific controls and presentation.

An adapter implements `fromProgram`, `toProgram`, its DOM, label/icon presentation and fullscreen lifecycle. `prepare` directly evaluates the synchronous conversion as a `Try` and returns an install callback without replacing the visible draft. Unavailable target tabs are disabled. Navigation validates before closing the current view and installs only if lifecycle callbacks have not restored the source or closed Eva. There is no conversion cache or `Future` wrapper: scheduling synchronous Scala.js work in a `Future` would still run it on the browser thread. Native editor loading and feature execution retain their own asynchronous lifecycles.

`setWith` synchronizes an external value without emitting a learner edit. A `Var[Option[T]]` holds the synchronization baseline so programmatic updates do not become learner edits. Subsequent user edits update the baseline, including an edit that returns to an earlier value. `edits` reports subsequent editor changes; identical echoes do not write again. Inactive tabs cannot publish. Conversion failures retain the raw answer and current view and expose an error. Matching unfinished source opens without converting through another language. Tabs appear in configured order; an empty language list displays an unavailable view safely.

The selected view and saved representation are separate. Viewing a successful conversion does not normalize or persist the answer. An actual edit in that view becomes the new saved representation. `captureSource()` delegates to the active adapter so pending native edits can be flushed before navigation or a feature operation. Consumers should use the captured draft rather than reach into a concrete editor.

All changing editor, adapter and module state—including lifecycle guards and native handle references—must be held in `val`-bound `Var[T]` values. Use immutable calculations and derived signals instead of Scala `var` or mutable collections. See [the client state rule](state-and-serialization.md#changing-client-state).

## Optional feature modules

`EvaEditorModule` receives a generic context with the bound state, a source-capture callback and the configured modules. Its optional panel and lifecycle cleanup belong to the module. Headless execution services return no DOM, so they do not create empty layout cells. The generic editor neither chooses a runtime nor derives turtle commands.

The turtle feature owns `TurtleProgramExecution`, Python/Snap workers, `JavaTurtleEditorExtension`, and `TurtleTaskEditorExtension` with its drawing panel. The task module captures the active source before a run, dispatches execution to its feature services and closes owned work on close or successful tab changes. Java worker execution and drawing assessment are tested independently of Eva's navigation internals.

`ProgrammingState` remains the shared conversion boundary. Its `associatedLanguage` identifies the source representation, and `toLanguage` dispatches to the requested supported language. Native Snap projects outside the checked subset remain editable even when a text conversion fails. Full Java classes use the existing checked bridge and runtime; unsupported/unfinished programs must retain their source.

## Persistence and verification

Concrete programming state records derive uPickle `ReadWriter`; the root codec uses a derived typed payload sum, including the expression adapter. Interactions use their standard implicit state serializer. JSON and MessagePack preserve the current representation and all source content. Checked Java VM expression states persist their source and recompile it on read because runtime bindings are not a persistent expression tree. This is a development schema: old headers, historical tags, aliases and migration readers are deliberately unsupported; reset development databases when the schema changes. See [state and serialization](state-and-serialization.md), [full Snap model](snap-scala-model.md) and [Snap lifecycle](snap-editor.md).

CodeMirror keeps its own asynchronous loading, fallback textarea, document/diagnostics synchronization, optional Python gutter and handle/worker cleanup. Its settings are supplied through `EvaEditorConfig.codeMirrorConfig`; those details stay outside Eva.

```sh
sbt 'coreJVM/testOnly *ProgrammingState*Spec *ProgrammingStateSerializationSpec'
sbt 'coreJS/testOnly *ProgrammingState*Spec *ProgrammingStateSerializationSpec'
sbt 'client/testOnly *EvaEditorSpec *Turtle*Spec *ProgrammingEditorDiagnosticsSpec *CodeMirrorEditor*Spec *SnapCodeEditorSyncSpec'
```

Tests target the generic tab/module contracts and the owning feature components. Tests of removed specialized Eva subclasses, hard-coded language enums, or a central execution/cache implementation are replaced by those contract tests.
