# Eva: one editor and downstream panels

`EvaEditor` displays one language editor bound to the supplied central
`Var[ProgrammingState]`. `EvaEditorTab` describes a language choice: its label,
conversion and editor factory. It contains no editor instance, private answer,
synchronization baseline, or edit event channel. Eva constructs only the selected
editor. Ordinary state updates reach that editor through its bound Var; they do
not construct another editor. A language/task switch unmounts the old editor and
mounts a fresh one. Returning to a language creates a fresh editor from the current
answer, rather than resuming an inactive editor with a stale draft.

## State and conversion

Language transformations live in shared core under [`vm.conversion`](programming-conversions.md).
Use the direct conversion methods on `ProgrammingState`; its owned derivations cache parsing and target projections.

Exercise and panel interfaces accept `ProgrammingState`; authored starter programs
and language editors use its typed representations wherever possible:
`ProgrammingStateJavaStructured`, `ProgrammingStatePythonStructured` and
`ProgrammingStateSnapScalaModel`. Reserve `ProgrammingStateRaw` for boundaries
that strictly require source text or XML, rather than using it as the default
answer representation. An unfinished method can remain in a structured Java
class; incomplete text alone does not require a raw state.

The central Var is the answer. A change is a change, regardless of whether it
comes from an editor, restoration, conversion or another component. Use signals
to observe it. Do not add `onStateEdited`, input-listener constructor arguments,
or separate stores such as `synchronizedValue` alongside a bound Var. Project
views with `bimap` where appropriate; for example the Python editor binds the code
field of the central Python state directly. Java's method fragment is a view of
the current source, not a separately saved program.

Keystrokes stay in the editor: Eva's language views edit the draft of a
[`TypingDraftBinding`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/abstractions/TypingDraftBinding.scala)
over the central Var, an immutable core `TypingDraft` holding the last committed answer
and the TEMPORARY edits typed since. A token-ending key, a five-second interval, a
change without a key (blocks), a language switch, a one-second quiet period, leaving
the editor and unmounting commit the draft to the central Var; panels, grading and
storage see only commits. Any other change of the central Var replaces the draft.

Conversions and their availability checks execute in `Future`s. A successful
language switch writes the converted representation to the central Var and thus
replaces the editor. A failed conversion leaves the current draft and editor in
place. Newer requests, source changes and unmount invalidate pending conversions;
late results cannot overwrite a newer answer. The generic Eva component contains
no concrete language editor or runtime branches. Concrete factories live beside
the standalone editors and are selected by `EvaEditorConfig`.

A Future on Scala.js schedules work on the browser thread; it does not provide a
separate CPU thread. Use a WebWorker for expensive execution where available,
including Pyodide and the checked Java runtime. Native libraries still need
callbacks at their JavaScript boundary. Keep those callbacks inside the adapter;
Scala consumers observe the bound Var or a supplied diagnostic signal.

Language switching uses the shared, language-neutral expression pipeline described in
[programming conversions](programming-conversions.md). Checked Java execution is separate.

## Mounting and panels

Laminar owns lifetime. Closing the workbook dialog removes its child; it does not
retain an invisible editor. There is no `FullscreenLifecycle`, open/close event
interface, or editor/module activation protocol. Mount-owned subscriptions stop
on unmount. Native CodeMirror handles, Snap worlds, workers, resize observers and
other resources are released by the DOM that owns them. `dismissOnOutsideClick`
is a dialog policy on `HtmlAppElement`, not a resource-lifecycle hook.

Side panels (instruction, grading, previews) belong to the workbook's
`EditorWithWorkbookPanel`, not to Eva. They receive the same central Var and their
own task configuration and observe the answer downstream. A component that analyses
or runs programs owns its analyzer/runner and closes it on unmount. Panels never
obtain state by reaching into an editor or calling `captureSource()`.

CodeMirror keeps one optional native handle. The fallback textarea's visibility
is derived from that handle. Diagnostics arrive as a signal; there is no pending
diagnostic store or copied current-document state. The language's optional
`CodeMirrorFlowchartSource` charts the text in the shared analysis worker (`worker.flowchart`).
[`FlowchartGutter`](../modules/core/shared/src/main/scala/it/evadid/core/datastructures/vectorShapes/renderer/FlowchartGutter.scala)
builds the graph from statements placed on source lines by `PythonFlowchartGutter` or `JavaFlowchartGutter`
(located Java parser; a program, a class, or a single method in Function Mode). The gutter reuses
the block editor's `FlowLayout` overlay, mapped to physical source rows; CodeMirror
alone renders the editable content. Parsing stays in the worker. The library loader
shares one asynchronous module request between startup warmup and editors;
failure/timeout leaves the fallback usable and allows a later retry.

All changing client state must remain in `val`-bound `Var[T]` values. Prefer
immutable calculations and derived signals to additional stores. See
[state and serialization](state-and-serialization.md).

`ProgrammingState` and its concrete representations live directly in the `programming.state`
package. Use `toBeExpression`, `toJava`, `toPython`, `toSnapXml` and instance `fingerprint()`;
there are no nested type aliases or alternate conversion names.

Expression states (`ProgrammingStateBeExpression`) belong to the `BlockDisplay` language and open in
the [block editor](block-editor.md) tab; the Snap editor still shows them in previews.

Workbook initial programs can use any supported representation. Language conversion
uses `BeExpression`; generated Snap is an ordinary project without Java metadata.
Unsupported constructs are rejected, including class declarations that Snap cannot represent.

## Persistence and verification

Interactions use the current implicit `ProgrammingState` codec. JSON and
MessagePack preserve source text and full Snap projects. Expression
states serialize their language-neutral tree. This is a development schema;
historical readers and migration aliases are deliberately unsupported.

Tests cover single-editor construction and mounting, direct state observation,
conversion failure/cancellation, unmount cleanup and the owning execution
services. Browser checks cover real input, delayed library loading, native editor
replacement, draft restoration and panel geometry.

The active tab is derived from the current `ProgrammingState`, including the Blocks tab for a `ProgrammingStateBeExpression`. Mounting the editor does not select the first configured tab or convert the answer implicitly. If the native tab is unavailable, the answer remains intact. External controls and Scala tests can await `EvaEditor.setActiveTab(language)` to convert the bound answer and activate that representation. Browser tests select the intended tab explicitly before testing its behavior.

## Java component editor

`JavaComponentEditor` binds the Java answer to **Class Mode**, **Function Mode** and
**Full Mode**. Its diagram selects source entities; each class owns its Add function
button. Inserting another declaration in Function Mode selects only the new function.
`JavaFragmentEditor` splices drafts into their source range, including unfinished code.

Language-independent UML data lives in `core.datastructures.uml.UmlClassDiagram` in shared core.
`vm.structure.JavaUmlDiagram` parses Java into that model; `UmlStructureSelector` renders a model
signal. This lets a future Python parser reuse the same diagram. Each `UmlClass` has an
`origin`; only `Program` classes are clickable and open editors. Read-only boxes («library», «unknown»):

- a verbatim library definition in the source (the generated `Turtle`); an edited copy is the program's own;
- `JavaUmlDiagram.external`: the Java form of the libraries the worker's `ProgramAnalysis` reports
  (`System`/`PrintStream` for printing) and the classes of its `BeUnknownDependency`
  (`System.out2.println` → `System` with `out2: SystemOut2`).

External boxes follow the last finished analysis, so they do not flicker while typing.
UML classes have square corners and stable hover geometry.

`ProgrammingExecutionPanel` is the console below the editor. It observes the committed answer, not
the typing draft, and depends only on `ProgrammingState`: static analysis decides whether and as what
Python a program runs, and Pyodide runs it ([program analysis and execution](program-analysis-and-execution.md)).
Drawings come from the BeExpression turtle commands; printing is never a drawing command.

`EvaEditor` composes three parts: `EvaConversionController` (conversion checks, requested switches,
their outcome; no DOM), `EvaLanguageTabs` (the accessible tab list) and the selected editor, created by
`EvaEditorTab.create(state, context)`. `EvaEditorContext` carries the page's `EvaEditorEnvironment`
(logger, labels, display language), Eva's appearance and the conversion marker. Workbook renderers
create Eva through `WorkbookEvaEditor`.

Editor labels resolve from `blockeditor` language maps. Choose among texts with `LabelSelection` (or `JavaEditorLabels.select`), not `flatMapSwitch`, which can end on an outdated text when its source changes in consecutive transactions. `JavaEditorNavigation` holds the Java editor's mode and diagram selection; `ProgrammingStateView` gives each editor its representation of the answer and drops writes of an editor that is being replaced. Pending gutter work is listed in [editor-todos.md](editor-todos.md).
