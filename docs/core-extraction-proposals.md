# Next shared-core extractions

Dependency review after the programming-conversion refactor, 11 November 2026. These are proposed follow-up tasks, not additional moves in this change. “Direct move” means the implementation and the small dependency bundle listed below use only Scala, upickle and existing shared-core types. This is a source/import review, not a claim that the proposed extractions have already been compiled.

## 1. SQL clause operations — small, direct move

[`SqlBlocks`](../modules/client/src/main/scala/it/evadid/homepage/webElements/sqlEditor/SqlBlocks.scala) splits and rejoins SQL without losing strings, comments or nested queries. Its only project import is `SnapPaletteCatalog`, already in shared core. Inputs and outputs are strings and lists; there are no DOM, JavaScript, Laminar, network or editor-state dependencies.

Move clause scanning, splitting, joining and insertion into `vm.conversion.sqlblocks`. Keep the editor's palette labels/templates in the client if separating presentation from syntax. Update `SqlEditor` to call the shared functions and move the existing clause tests to core JVM and JS. Validate exact text reconstruction, quoted keywords, comments, nested queries and semicolon handling. Leave `SqlDatabaseDiagram` and drag/drop event handling in the client; they return Laminar/SVG elements.

## 2. Static programming-feedback rules — small, direct bundle

Move [`PythonStaticRules`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/rules/PythonStaticRules.scala), [`VmStaticRules`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/rules/VmStaticRules.scala) and [`RuleResult` / `RuleSeverity`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/rules/RuleResult.scala) together into a focused `vm.analysis.feedback` package.

The dependency boundary closes over these three files: the rules consume source strings or `BeExpression` and a core `HumanLanguage`, use Scala collections/regexes, and return immutable result records. `RuleResult` depends only on upickle. Neither rule object needs `BlockFeedbackService`, the browser runtime, a request controller or UI elements. Current callers in the feedback service can retain orchestration and render the returned results.

Move behavioral tests for variable use, unreachable code and source-rule findings into core; keep presentation tests in the client. Preserve the current messages first, then consider localization separately. Do not move the whole feedback service: its execution/runtime dependencies are a different boundary.

## 3. Turtle drawing assessment and scene data — medium, extract a boundary first

[`TurtleDrawingGrading`](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/turtleStitch/TurtleDrawingGrading.scala) validates commands, applies resource limits and compares drawings. Its sole client dependency is the scene representation and builder nested in [`TurtleJsxGraphRenderer`](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/turtleStitch/TurtleJsxGraphRenderer.scala).

The nested `LineResult`, `RenderedLine`, `RenderedAngle`, `Scene`, `LineToRender`, and the `buildScene`/geometry helpers use shared `Point`, `TurtleGraphic`, `TurtleDrawingComparison` and `TurtleGradingLogic`, plus Scala numeric operations. Their records contain no JSXGraph handles, callbacks or reactive values. Extract those records and builders first; then assessment can move with them and return a core result plus scene data. Reuse the existing core comparison algorithm rather than introducing another grader.

Keep `render`, board creation/disposal, preload, hover handlers, `Signal`/`Var` state and DOM errors in the client. `angleSector` includes the renderer's Y-axis inversion, so leave that adapter-specific calculation there unless a separate coordinate contract is agreed. Test command limits and assessment in core; keep a small browser test for the drawing adapter. This is not a whole-file move.

## 4. Small ML inference model — small/medium, replace its JSON boundary

[`SoftmaxModel`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/ml/SoftmaxModel.scala) contains independent feature normalization, softmax inference, label selection and contributor ranking. The model fields are Scala strings, vectors, maps, arrays and numbers; these numerical methods have no UI dependencies.

The file **cannot move unchanged**: its companion parses training-tool JSON using `scala.scalajs.js.JSON`, `js.Dynamic`, JavaScript arrays/dictionaries and `js.isUndefined`. Replace that parser with ujson and explicit input validation, or keep parsing at the client boundary and move only the typed model and inference. Prefer one canonical JSON reader rather than two platform-specific readers. Validate dimensions, finite results, normalization and contributor ordering against fixtures from the training tool.

Keep [`MlRouter`](../modules/client/src/main/scala/it/evadid/homepage/workbook/legacy/interactionPlugins/blockEnvironment/feedback/ml/MlRouter.scala)'s fetch/loading behavior in the client. A numerical model needs neither the browser loader nor global model state.

## Recommended order and exclusions

Start with SQL clause operations and static rules: each has a small, closed dependency set. Next separate turtle assessment from display data; move ML inference when its JSON contract is covered by a fixture.

Compression algorithms and experiment models already live in shared `workbook.model.compression`; moving `CompressionActivitiesEditor` would mostly move DOM events and Laminar bindings. `WorkbookUserDataAnalyzer` primarily reads client session/control state and triggers downloads. Those are poor whole-file candidates. Likewise, the programming conversion refactor does not make Snap's native runtime, Pyodide loading or CodeMirror lifecycle suitable for shared core: their browser adapters should remain in the client or workers.
