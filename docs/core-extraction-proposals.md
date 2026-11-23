# Shared-core boundaries and follow-up work

SQL clause operations and static feedback rules belong to shared core. Turtle assessment and ML inference have additional client boundaries to separate before moving their pure logic. The following sections distinguish current ownership from proposed work.

## SQL clause operations

[`SqlBlocks`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/sqlblocks/SqlBlocks.scala) lives in shared `vm.conversion.sqlblocks`. Scanning, splitting, joining, clause insertion and block ordering consume strings/lists without browser or editor-state dependencies. Its behavioral tests run on JVM and Scala.js, covering exact text reconstruction, quoted keywords, comments, nested queries and semicolon handling.

[`SqlEditorPalette`](../modules/client/src/main/scala/it/evadid/homepage/webElements/sqlEditor/SqlEditorPalette.scala) retains the editor's templates and comparison-button definitions, reusing `SnapPaletteCatalog`. `SqlEditor` invokes the shared clause functions. Diagram rendering and drag/drop event handling remain in the client.

## Static programming-feedback rules

[`PythonStaticRules`](../modules/core/shared/src/main/scala/it/evadid/vm/analysis/feedback/PythonStaticRules.scala), [`VmStaticRules`](../modules/core/shared/src/main/scala/it/evadid/vm/analysis/feedback/VmStaticRules.scala) and [`RuleResult` / `RuleSeverity`](../modules/core/shared/src/main/scala/it/evadid/vm/analysis/feedback/RuleResult.scala) live in shared `vm.analysis.feedback`.

The dependency boundary closes over these three files: they consume source strings or `BeExpression` and a core `HumanLanguage`, use Scala collections/regexes, and return immutable result records. Tests for source findings, variable use and unreachable code run on JVM and Scala.js. Feedback orchestration, execution/runtime services and presentation remain in the client and import the shared rules directly.

## Proposed: turtle assessment and scene data

[`TurtleDrawingGrading`](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/turtleStitch/TurtleDrawingGrading.scala) validates commands, applies resource limits and compares drawings. Its sole client dependency is the scene representation and builder nested in [`TurtleJsxGraphRenderer`](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/turtleStitch/TurtleJsxGraphRenderer.scala).

The nested `LineResult`, `RenderedLine`, `RenderedAngle`, `Scene`, `LineToRender`, and the `buildScene`/geometry helpers use shared `Point`, `TurtleGraphic`, `TurtleDrawingComparison` and `TurtleGradingLogic`, plus Scala numeric operations. Their records contain no JSXGraph handles, callbacks or reactive values. Extract those records and builders first; then assessment can move with them and return a core result plus scene data. Reuse the existing core comparison algorithm rather than introducing another grader.

Keep `render`, board creation/disposal, preload, hover handlers, `Signal`/`Var` state and DOM errors in the client. `angleSector` includes the renderer's Y-axis inversion, so leave that adapter-specific calculation there unless a separate coordinate contract is agreed. Test command limits and assessment in core; keep a small browser test for the drawing adapter. This is not a whole-file move.

## Proposed: ML inference model

[`SoftmaxModel`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/ml/SoftmaxModel.scala) contains independent feature normalization, softmax inference, label selection and contributor ranking. The model fields are Scala strings, vectors, maps, arrays and numbers; these numerical methods have no UI dependencies.

The file **cannot move unchanged**: its companion parses training-tool JSON using `scala.scalajs.js.JSON`, `js.Dynamic`, JavaScript arrays/dictionaries and `js.isUndefined`. Replace that parser with ujson and explicit input validation, or keep parsing at the client boundary and move only the typed model and inference. Prefer one canonical JSON reader rather than two platform-specific readers. Validate dimensions, finite results, normalization and contributor ordering against fixtures from the training tool.

Keep [`MlRouter`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/ml/MlRouter.scala)'s fetch/loading behavior in the client. A numerical model needs neither the browser loader nor global model state.

## Recommended order and exclusions

SQL clause operations and static rules are now shared. Next separate turtle assessment from display data; move ML inference when its JSON contract is covered by a fixture.

Compression algorithms and experiment models already live in shared `workbook.model.compression`; moving `CompressionActivitiesEditor` would mostly move DOM events and Laminar bindings. `WorkbookUserDataAnalyzer` primarily reads client session/control state and triggers downloads. Those are poor whole-file candidates. Snap's native runtime, Pyodide loading and CodeMirror lifecycle belong outside shared core: their browser adapters should remain in the client or workers.
