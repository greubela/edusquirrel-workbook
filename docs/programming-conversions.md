# Programming conversions

Each immutable programming state owns one [`ProgrammingStateDerivations`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/ProgrammingStateDerivations.scala), shared by views and graders. The central `convertToLanguage` method returns a `ConversionResult` containing the native structured state plus diagnostics. Direct state methods expose `toBeExpression`, `toJava`, `toPython`, `toSnapXml`, `toJavaStructured` and `toPythonStructured`.

Cross-language conversion follows one route:

```
source state → source frontend → BeExpression → target printer → target state
```

`ProgrammingStateRaw(language, code)` is the single source transport type. Prefer structured Java/Python states and the typed Snap model for persisted answers; raw source is useful for imports and unfinished text editing, and remains permitted in histories. Raw source projections preserve the original text. Snap XML ↔ the full Snap model remains a lossless representation conversion; it does not need an executable expression tree. Structured state constructors retain unfinished source for editing, but cross-language conversion requires supported syntax.

`BeExpression` stores language-neutral definitions, calls, values and control flow. It contains no checked-Java runtime nodes, original Java source, target-language names, metadata-selected semantic modes or implicit Python receiver parameters. Printers supply target syntax, including Python's `self`. Explicit `BeExpressionUnsupported` and `BeExpressionUnparsable` nodes can retain unsupported source for diagnostics.

| Responsibility | Package below `vm.conversion` |
| --- | --- |
| Python frontend | `pythonexpression` |
| Java conversion frontend | `javaexpression` |
| Snap frontend, startup and detached scripts | `snapexpression.SnapToExpression`, using `turtlestitchexpression` |
| Shared-tree printers | `expressionpython`, `expressionjava`, `expressionsnap`, `expressionlisp` |
| Source grouping for structured editors | `structured` |
| Lossless Snap project representation | `snapxmlmodel`, `snapmodelxml` |
| Python-to-Snap application retaining project layout | `pythonsnap`, using the same frontend and expression exporter |
| SQL clause blocks | `sqlblocks` |

Snap green flags become startup; detached scripts become the reserved, uncalled `unusedCode` function. Export restores its body as loose blocks without a green-flag hat, including after Python/Java roundtrips. Saved variable initializers enter the shared tree once. Unsupported Snap classes are rejected rather than silently discarded. The Python frontend binds constructors, member access, instance calls and static calls. Inheritance and unknown decorators remain explicit unsupported source. The Java frontend also binds constructors, explicit and implicit instance receivers, local shadowing and static methods. Its lexical lowering shares the expression binder; the checked Java runtime still enforces its narrower subset. Static Java entry-point wrappers lower to module functions and startup; original wrapper names and formatting are not replayed after a language change.

`JavaTurtleCompiler` in `vm.compilation.java.turtle` parses source, validates structure and semantics, resolves symbols, and produces a `JavaTurtleProgram`. `JavaTurtleInterpreter` in `vm.simulation.java` executes that program with Java arithmetic, limits and tracing. This is a separate runtime capability. Its nodes do not extend `BeExpression`. Java unit-test execution can generate runtime instrumentation through `JavaTurtlePythonExport`; that instrumentation is not an editable programming-state conversion and does not select conversion routes. There is no Java-emulation metadata reader or editable Java/Python/Snap bridge.

Regression coverage lives in `ProgrammingStateConversionSpec`, `StructuredProgrammingStateSpec`, `PythonTypeHintConversionSpec` and the turtle execution tests. Checked Java arithmetic, bindings, tracing and runtime tests remain independent of conversion tests.

## Neutral object model

`BeFunctionCall.target` distinguishes a function, an instance method with a receiver, a static method, and a constructor. Its declaration remains the sole owner of the signature and canonical name. `BeCurrentInstance`, `BeMemberAccess` and `BeAssignMember` represent object operations; `BeDataType.ClassType` represents named object types. Receivers participate in traversal and replacement, and these nodes roundtrip through JSON and MessagePack. Java and Python printers render the same tree; Snap rejects object functionality explicitly. The small drawing simulator also rejects unsupported object execution.

A target sum type is preferable to independent `isStatic`/`isConstructor` flags and receiver fields: callers cannot accidentally retain a receiver on a constructor or static call, and each new call category has one explicit branch. No source-language names or runtime nodes are added to the model. Source placeholders alone retain optional `parsedFromLanguage`; emitting known foreign source fails explicitly. Snap numeric slots recover numeric types; they do not preserve an original Java `int`/`double` distinction.

Python and Java object binding, detached Snap export, structured editor conversion results, class hoisting and first-definition-wins warnings are implemented. `ConversionInfo.sourceError` optionally preserves parser categories and positions for editor markers. Raw transport uses one current codec; historical source-state tags are not supported.

Further work should extend the supported type and runtime subsets together with semantic roundtrip tests. Structured and raw sources of a language must use the same execution backend.

The shared model does not imply that every object-oriented source program can be parsed or executed. The checked Java drawing runner retains its narrower execution subset.

Module variables become static fields of Java `Main`; their initializers remain in startup order rather than moving ahead of intervening statements. Lifting a Java entry point restores module statements directly. Original source remains the input to the checked Java runtime and its source locations.

`BeIntrinsic` identifies turtle and printing library semantics independently of user names. Printers select target syntax from this marker; user-defined `print`/`forward` methods remain ordinary functions. Java conversion rejects a user `Turtle` class when it would hide actual turtle-library calls.

The shared numeric subset uses double precision. Java integer declarations are widened on cross-language conversion; `ConversionResult` reports this as a loss because wrapping overflow and truncating division are not preserved. Same-language source grouping retains the original types.

## Running Python

`ProgrammingStatePythonStructured.startExecution()(using ExecutionClient, ExecutionContext)` sends the native source to the existing `worker.runPython` command; supply the application's worker client. It returns `Future[RunPythonResult]` with captured stdout/stderr and also prints those streams to the caller's console when execution completes. Python exceptions fail the future; output emitted before the failure is retained in the worker diagnostic output and printed too.

```scala
given ExecutionClient = fullInfo.defaults.workerExecuter
val state = ProgrammingStatePythonStructured("from turtle import *\nforward(100)\nprintln(\"hai\")")
val result = state.startExecution() // stdout: "forward(100)\nhai\n"
```

This enables `RunPythonRequest.logTurtleCalls`: turtle methods log their called name and arguments instead of drawing, and `println` aliases `print`. Both imported and unqualified turtle calls work. The state API uses a fresh program namespace; explicit worker requests can retain globals with `resetGlobals = false`. Execution sets `__name__` to `__main__`, and restores the worker's previous turtle module even after an exception. Ordinary worker Python requests retain their existing behavior.
