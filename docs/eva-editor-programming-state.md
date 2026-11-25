# Eva: one editor and downstream panels

`EvaEditor` mounts one language editor bound to the central `Var[ProgrammingState]`.
The active tab follows the answer's representation; mounting never converts it
implicitly. Ordinary edits update the mounted editor. Language/task switches replace
it, and unmount releases its native handles, workers and subscriptions.

## State and conversion

Prefer `ProgrammingStateJavaStructured`, `ProgrammingStatePythonStructured` and
`ProgrammingStateSnapScalaModel` for answers. `ProgrammingStateRaw` is for text/XML
boundaries; unfinished Java/Python can remain in structured states. Expression states
use `BlockDisplay`. See [programming conversions](programming-conversions.md) and
[source parsing](source-parsing.md).

The central Var is authoritative. Restoration, conversion and user changes use the
same binding; components observe signals rather than editor callbacks or separate
synchronization stores. All changing client state uses `val`-bound `Var[T]` values.

`TypingDraftBinding` keeps uncommitted typing local. Token boundaries, quiet/time
limits, focus loss, switching and unmount commit to the answer; panels, grading and
storage observe those commits. External answer changes replace the local draft.

Conversions run asynchronously. Success commits the target representation; failure
keeps the draft/editor. Source changes, newer requests and unmount invalidate stale
conversion completions. `setActiveTab(language)` exposes this awaitable operation.
Expensive work runs in workers: a Scala.js `Future` alone does not create a CPU thread.

## Ownership and key locations

| Component | Responsibility |
| --- | --- |
| `EvaEditorTab`, `EvaEditorConfig` | Language metadata and factories; only the selected editor is created |
| `EvaConversionController` | Availability checks, requested switches and outcomes; no DOM |
| `EvaLanguageTabs` | Accessible, localized tab list |
| `EvaEditorContext`, `EvaEditorEnvironment` | Logger, labels, exercise environment, appearance and diagnostics |
| `WorkbookEvaEditor` | Workbook renderer's construction boundary |
| `EditorWithWorkbookPanel` | Task instructions, grading, hints and drawing previews |
| `ProgrammingExecutionPanel` | Console below Eva, observing the committed answer |

Panels receive the central answer and task configuration directly. They do not read
state back from editors or use an activation protocol. Drawings follow the explicitly run answer and use shared
BeExpression turtle commands; the console runs the Python form in Pyodide.
See [program analysis and execution](program-analysis-and-execution.md).

CodeMirror derives fallback visibility from its native handle. Startup and mounted
editors share the library load; failure leaves an editable fallback and permits retry.
Diagnostics and worker-generated flowcharts arrive through signals. The gutter reuses
`FlowLayout` on physical source lines; CodeMirror alone renders source text.

## Java component editor

`JavaComponentEditor` (`editor/code/JavaEditor`) offers Class, Function and Full modes. `JavaEditorNavigation`
owns mode/selection; `JavaFragmentEditor` splices partial drafts into source ranges, and
core `JavaMethodEditing` locates, replaces and inserts methods on step-1 declarations.
Adding a declaration in Function Mode selects only the new function.
Attribute and constructor dialogs, deletion and relationship arrows use the same shared UML model.

Shared UML data lives in `core.datastructures.uml`; `JavaUmlDiagram` derives it from
the parsing stages and `UmlStructureSelector` renders it. Program classes are editable.
Library/unknown-dependency boxes are read-only and retain the last finished analysis
while typing. An edited library definition becomes program code. UML classes use
square corners and stable hover geometry.

Labels come from `blockeditor` language maps. Use `LabelSelection` or
`JavaEditorLabels.select` for reactive text selection. Styles belong in dedicated CSS.

Current codecs preserve structured answers and lossless Snap models; no historical
schema aliases are supported. Scala tests cover state/conversion/lifetime contracts;
browser tests cover native editing, language switching, loading failures and layout.
Pending editor work is tracked in [editor-todos.md](editor-todos.md).
