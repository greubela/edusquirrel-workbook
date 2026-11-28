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

The central Var is authoritative ([state rules](state-and-serialization.md)).

CodeMirror and Snap buffer typing locally and publish after a pause, focus loss,
page hiding or unmount. Eva binds directly to the answer; there is no second buffer.
External answer changes replace pending local text.

Conversions run asynchronously. Success commits the target representation; failure
keeps the draft/editor. Source changes, newer requests and unmount invalidate stale
conversion completions. `setActiveTab(language)` exposes this awaitable operation.

## Ownership and key locations

| Component | Responsibility |
| --- | --- |
| `EvaEditorTab`, `EvaEditorConfig` | Language metadata and factories; only the selected editor is created |
| `EvaConversionController` | Availability checks, requested switches and outcomes; no DOM |
| `EvaLanguageTabs` | Accessible, localized tab list |
| `EvaToolbarControls` | Selected editor's modes and class-diagram toggle, shown after the language tabs in Eva's single toolbar line (Snap adds none); `DiagramDisplay` hides the diagram on small screens |
| `EvaEditorContext`, `EvaEditorEnvironment` | Logger, labels, exercise environment, appearance and diagnostics |
| `WorkbookEvaEditor` | Workbook renderer's construction boundary |
| `EditorWithWorkbookPanel` | Task instructions, grading, hints and drawing previews |
| `ProgrammingExecutionPanel` | Console below Eva, observing the committed answer; its action bar holds Run, Stop and `ProjectDownloadButton` |

Panels receive the central answer and task configuration directly. They do not read
state back from editors or use an activation protocol; drawings and the console follow
[program analysis and execution](program-analysis-and-execution.md).

CodeMirror derives fallback visibility from its native handle. Startup and mounted
editors share the library load; failure leaves an editable fallback and permits retry.
Diagnostics and worker-generated flowcharts arrive through signals. The gutter reuses
`FlowLayout` on physical source lines; CodeMirror alone renders source text.

## Structure editors

`JavaComponentEditor` and `PythonComponentEditor` offer class, function and full-source views,
creating only the selected one; `JavaMethodEditing`/`PythonMethodEditing` (core) locate and
replace members on the parsed declarations. Class and function views are
`FragmentCodeEditor[S, I]`s (`editor/code/fragment`) that splice a partial draft into a
`FragmentRange` of the structured answer. A draft that grows beyond its declaration keeps its
code and switches to the most local view that shows it (`FragmentCodeEditor.Scope`), never while
brackets are open.

Both languages share one UML model (`core.datastructures.uml`), derived by `JavaUmlDiagram` and
`PythonUmlDiagram` and rendered by `UmlStructureSelector`. Program classes are editable;
`ExternalUmlClasses` adds read-only «library» and dashed «unknown» boxes from the worker analysis.

Labels come from `blockeditor` language maps. Use `LabelSelection` or
`JavaEditorLabels.select` for reactive text selection. Styles belong in dedicated CSS.

Current codecs preserve structured answers and lossless Snap models; no historical
schema aliases are supported. Scala tests cover state/conversion/lifetime contracts;
browser tests cover native editing, language switching, loading failures and layout.
Pending editor work is tracked in [editor-todos.md](editor-todos.md).
