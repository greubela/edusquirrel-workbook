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

Optional panels are ordinary `HtmlAppElement`s supplied to Eva. They receive the
same central Var and their own necessary task configuration. They observe the
answer downstream; Eva neither binds a context into them nor activates their
execution services. The turtle panel owns its execution service, cancellation,
drawing assessment and target. Its subscriptions and worker cleanup follow its
mount. Panels never obtain state by reaching into an editor or calling
`captureSource()`.

CodeMirror keeps one optional native handle. The fallback textarea's visibility
is derived from that handle. Diagnostics arrive as a signal; there is no pending
diagnostic store or copied current-document state. The mount owns the optional
flowchart analysis and releases it with the native handle. The gutter reuses
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

Eva's Java tab is [`JavaComponentEditor`](../modules/client/src/main/scala/it/evadid/homepage/webElements/code/JavaComponentEditor.scala),
bound to a `Var[ProgrammingStateJavaStructured]`. A UML diagram of the classes sits
above an editing area that shows, for the diagram selection, a `JavaFunctionEditor`
(a function) or a `JavaClassEditor` (a class or one of its attributes). A Full source
view, Add class/Add function dialogs and an optional task reference remain. The
entry point `main` (of class `Main`, if present) opens first; without one, the
full source does.

Both editors extend [`JavaFragmentEditor`](../modules/client/src/main/scala/it/evadid/homepage/webElements/code/JavaFragmentEditor.scala),
which owns the shared update logic. An editor shows its fragment in a local
`Var[String]`; a `JavaFragmentRange` records where the fragment lies in its class
source. Local edits are spliced into the answer at that range, which then spans the
draft, so unfinished code stays in place. Any other answer change refreshes the
local text; if the fragment's text changed, it is located again by identity (a
class name, or a method signature from `JavaMethodSource`). Subclasses only locate
and identify their fragment. Renaming a class in its declaration renames its entry
in place. Line numbers follow the printed program.

### UML structure selector

[`EvaEditor.uml.UmlStructureSelector`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/EvaEditor/uml/UmlStructureSelector.scala)
observes a `Signal[ProgrammingStateJavaStructured]` and draws each class as a UML box
(name, attributes, functions; no relationships; known library classes such as the
generated `Turtle` are omitted). Every entry is a button.
`UmlClassDiagram` derives the boxes from the class sources with `SourceStructure`.
The component stores only the clicked entry's key (`selectedKeySignal`);
`selectedEntitySignal` resolves it against the current state, so the selected
`fullCode` follows edits and becomes `None` when the entry is removed.
Styles: `homepage/css/uml-structure-selector.css`.
