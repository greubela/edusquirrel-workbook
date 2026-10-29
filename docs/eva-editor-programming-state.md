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

The central Var is the answer. A change is a change, regardless of whether it
comes from an editor, restoration, conversion or another component. Use signals
to observe it. Do not add `onStateEdited`, input-listener constructor arguments,
or separate stores such as `synchronizedValue` alongside a bound Var. Project
views with `bimap` where appropriate; for example the Python editor binds the code
field of the central Python state directly. Java's method fragment is a view of
the current source, not a separately saved program.

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
`NestedBlockRenderer`’s flow overlay, mapped to physical source rows; CodeMirror
alone renders the editable content. Parsing stays in the worker. The library loader
shares one asynchronous module request between startup warmup and editors;
failure/timeout leaves the fallback usable and allows a later retry.

All changing client state must remain in `val`-bound `Var[T]` values. Prefer
immutable calculations and derived signals to additional stores. See
[state and serialization](state-and-serialization.md).

`ProgrammingState` and its concrete representations live directly in the `programming.state`
package. Use `toBeExpression`, `toJava`, `toPython`, `toSnapXml` and instance `fingerprint()`;
there are no nested type aliases or alternate conversion names.

For workbook initial programs: beginner turtle / native Snap tasks should start from
`ProgrammingStateSnapXml` (for example `SnapProjectXml.mini`), not from
`ProgrammingStateJavaString(...).toSnapXml`. The latter is correct for Eva language
switching on full Java classes, but it produces Java editing-bridge Snap (custom blocks
and project notes), not a normal Snap turtle project.

## Persistence and verification

Interactions use the current implicit `ProgrammingState` codec. JSON and
MessagePack preserve source text and full Snap projects. Checked Java expression
states save their source and recompile on read. This is a development schema;
historical readers and migration aliases are deliberately unsupported.

Tests cover single-editor construction and mounting, direct state observation,
conversion failure/cancellation, unmount cleanup and the owning execution
services. Browser checks cover real input, delayed library loading, native editor
replacement, draft restoration and panel geometry.
