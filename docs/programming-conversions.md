# Programming conversions

Each immutable programming state owns one [`ProgrammingStateDerivations`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/ProgrammingStateDerivations.scala), shared by views and graders. The central `convertToLanguage` method returns a `ConversionResult` containing the native structured state plus diagnostics. Direct state methods expose `toBeExpression`, `toJava`, `toPython`, `toSnapXml`, `toJavaStructured` and `toPythonStructured`.

Cross-language conversion follows one route:

```
source state → source frontend → BeExpression → target printer → target state
```

`ProgrammingStateRaw(language, code)` is the single source transport type. Prefer structured Java/Python states and the typed Snap model for persisted answers; raw source is useful for imports and unfinished text editing, and remains permitted in histories. Raw source projections preserve the original text. Snap XML ↔ the full Snap model remains a lossless representation conversion; it does not need an executable expression tree. Structured state constructors retain unfinished source for editing, but cross-language conversion requires supported syntax.

**Consistency requirement.** A program behaves the same whichever state it starts in: every frontend produces the shared tree, every printer reads it, and Python and Java share one binder. A language subset that cannot be represented is rejected or kept as an explicit unsupported node rather than converted differently.

`BeExpression` stores language-neutral definitions, calls, values and control flow. It contains no checked-Java runtime nodes, original Java source, target-language names, metadata-selected semantic modes or implicit Python receiver parameters. Printers supply target syntax, including Python's `self`. Explicit `BeExpressionUnsupported` and `BeExpressionUnparsable` nodes can retain unsupported source for diagnostics.

| Responsibility | Package below `vm.conversion` |
| --- | --- |
| Shared parsed model and binding for Python and Java ([source parsing](python-parsing-architecture.md)) | `parsed` |
| Python frontend (fastparse syntax tree → parsed model) | `pythonexpression` |
| Java frontend (fastparse syntax tree → parsed model) | `javaexpression` |
| Snap frontend, startup and detached scripts | `snapexpression.SnapToExpression`, using `turtlestitchexpression` |
| Shared-tree printers and the shared module layout | `printing`, `expressionpython`, `expressionjava`, `expressionsnap`, `expressionlisp` |
| Source grouping for structured editors | `structured` |
| Lossless Snap project representation | `snapxmlmodel`, `snapmodelxml` |
| Python-to-Snap application retaining project layout | `pythonsnap`, using the same frontend and expression exporter |
| SQL clause blocks | `sqlblocks` |

Snap green flags become startup; detached scripts become the reserved, uncalled `unusedCode` function. Export restores its body as loose blocks without a green-flag hat, including after Python/Java roundtrips. Saved variable initializers enter the shared tree once. Unsupported Snap classes are rejected rather than silently discarded. The Python frontend binds constructors, member access, instance calls and static calls. Inheritance and unknown decorators remain explicit unsupported source. The Java frontend also binds constructors, explicit and implicit instance receivers, local shadowing and static methods; the checked Java runtime still enforces its narrower subset. Snap has no comment statements: comments are skipped with an informational `ConversionInfo`. Static Java entry-point wrappers lower to module functions and startup; original wrapper names and formatting are not replayed after a language change.

`JavaTurtleCompiler` in `vm.compilation.java.turtle` parses source, validates structure and semantics, resolves symbols, and produces a `JavaTurtleProgram`. `JavaTurtleInterpreter` in `vm.simulation.java` executes that program with Java arithmetic, limits and tracing. This is a separate runtime capability. Its nodes do not extend `BeExpression`. Java unit-test execution can generate runtime instrumentation through `JavaTurtlePythonExport`; that instrumentation is not an editable programming-state conversion and does not select conversion routes. There is no Java-emulation metadata reader or editable Java/Python/Snap bridge.

Regression coverage lives in `ProgrammingStateConversionSpec`, `StructuredProgrammingStateSpec`, `PythonTypeHintConversionSpec`, `CrossLanguageRoundTripSpec`, `ParsedStructureSpec`, `CountingLoopSpec`, `DependencySpec`, `JavaCompilationRoundTripSpec` and the turtle execution tests. `CrossLanguageRoundTripSpec` normalises a source once (A → BeExpression → A), so formatting never matters, then requires A → BeExpression → B → BeExpression → A to restore the text between Python and Java and to reach a fixpoint after one pass through Snap, which has no types; every route must draw the same commands. Checked Java arithmetic, bindings, tracing and runtime tests remain independent of conversion tests.

## Neutral object model

`BeFunctionCall.target` distinguishes a function, an instance method with a receiver, a static method, and a constructor. Its declaration remains the sole owner of the signature and canonical name. `BeCurrentInstance`, `BeMemberAccess` and `BeAssignMember` represent object operations; `BeDataType.ClassType` represents named object types. Receivers participate in traversal and replacement, and these nodes roundtrip through JSON and MessagePack. Java and Python printers render the same tree; Snap rejects object functionality explicitly. The small drawing simulator also rejects unsupported object execution.

A target sum type is preferable to independent `isStatic`/`isConstructor` flags and receiver fields: callers cannot accidentally retain a receiver on a constructor or static call, and each new call category has one explicit branch. No source-language names or runtime nodes are added to the model. Source placeholders alone retain optional `parsedFromLanguage`; emitting known foreign source fails explicitly. Snap numeric slots recover numeric types; they do not preserve an original Java `int`/`double` distinction.

Python and Java object binding, detached Snap export, structured editor conversion results, class hoisting and first-definition-wins warnings are implemented. `ConversionInfo.sourceError` optionally preserves parser categories and positions for editor markers. Raw transport uses one current codec; historical source-state tags are not supported.

Further work should extend the supported type and runtime subsets together with semantic roundtrip tests. Structured and raw sources of a language must use the same execution backend. Turtle tasks run Java on the shared BeExpression interpreter (Python in Pyodide, Snap in its runtime, unless a task selects the shared interpreter for all languages); only the full Java exercise uses the checked Java runner ([`TurtleProgramExecution`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/EvaEditor/extensions/turtle/TurtleProgramExecution.scala)).

The shared model does not imply that every object-oriented source program can be parsed or executed. The checked Java drawing runner retains its narrower execution subset.

Module variables become static fields of Java `Main` ([`ModuleLayout`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/printing/ModuleLayout.scala)); a literal first assignment becomes the field initializer, other initializers remain in startup order rather than moving ahead of intervening statements. Lifting a Java entry point restores module statements directly. Original source remains the input to the checked Java runtime and its source locations.

`BeIntrinsic` identifies library and built-in semantics (turtle, printing, reading a URL, `int`/`float`/`str`/`bool`/`abs`/`max`/`min`) independently of user names. Printers select target syntax from this marker; user-defined `print`/`forward` methods remain ordinary functions. Java conversion rejects a user `Turtle` class when it would hide actual turtle-library calls.

## Libraries and built-ins

A [`BeDependency`](../modules/core/shared/src/main/scala/it/evadid/vm/code/defining/BeDependency.scala)`(structureName, addDefinitions)` holds the definitions a language needs for a library. [`KnownDependencies`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/dependencies/KnownDependencies.scala) lists them by intrinsic. A program requires a dependency when it calls one of its functions. Printers then add the definitions, and parsers remove a definition only when it matches exactly, ignoring layout, so an edited copy becomes user code.

| Dependency | Python | Java | Snap |
| --- | --- | --- | --- |
| Turtle | `import turtle` | generated `class Turtle` ([`JavaTurtleLibrary`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/dependencies/JavaTurtleLibrary.scala)), runnable on a plain JVM, Swing window unless headless | native blocks |
| `fetch_text(url)` | `urllib.request` helper | `class Web` with `fetchText` | `url` reporter (`reportURL`) |
| `print` | `print` | `System.out.println` | speech bubble (`bubble`) |

The Java `Turtle` methods are generated from the shared turtle signatures. `goto` is reserved in Java and is spelled with its alias `setPosition`. [`JavaBuiltinSpelling`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/expressionjava/JavaBuiltinSpelling.scala) is the single table for Java built-ins in both directions:

- numbers are cast: `(int) x`, `(double) x`;
- text is parsed: `Integer.parseInt`, `Double.parseDouble`;
- `String.valueOf`, `Math.abs`, `Math.max` and `Math.min` map directly;
- `bool(n)` becomes `(n != 0)` and reads back as that comparison.

`round` is not shared because Python rounds halves to even and Java rounds them up. Lists and other collections need a collection type in the shared tree first. `JavaCompilationRoundTripSpec` compiles and runs generated programs with `javac`.

## Loops

[`ParsedStatement.countingLoop`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/parsed/ParsedProgram.scala) maps Python's `range` and Java's counting `for` to the most specific loop:

- **`BeRepeatNr`** when the counter is never read and the bounds are whole-number literals. This includes `for _ in range(1, 5)`, `for (int i = 4; i > 0; i--)` and `i += 2`.
- **`BeFor`** for steps of ±1. It is inclusive like Snap's `for`, which counts down when start > end, so empty literal ranges become `repeat 0`.
- **Initialisation followed by `BeWhile`**, ending with the step, for other steps.

A Java counter belongs to its loop. A Python counter is "read" if the program uses the name anywhere outside `for` targets. A Java `for` that is not a counting loop, for example `i = i * 2` or a body that changes its bound, becomes its initialisation followed by a `while` that ends with its updates. `BeRepeatNr` holds a fixed number, so `range(n)` remains a `BeFor`. `CountingLoopSpec` covers these cases; `ParsedStructureSpec` checks the tree that equivalent Python, Java and Snap sources build.

A top-level `if __name__ == "__main__":` is read as its body, so a guarded `main()` is the usual entry point.

The shared numeric subset uses double precision. Java integer declarations are widened on cross-language conversion; `ConversionResult` reports this as a loss because wrapping overflow and truncating division are not preserved. Same-language source grouping retains the original types.

## Entry points and empty programs

The main sequence has one canonical shape per language, laid out by `ModuleLayout` for both printers. Snap marks it with a green flag, which is emitted even when no blocks follow. Java prints object classes, then `class Main` with static fields and methods and `main(String[] unusableArgs)`; empty source becomes `Main` with an empty `main`. Python prints classes, module declarations, functions and literal-initialised variables, then a called `def main()` ([`PythonEntryPoint`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/expressionpython/PythonEntryPoint.scala)). Every module variable assigned in `main` is declared with its type at module level (`x: float`, `static double x;`); Python's `main` declares it `global`, and a local variable is annotated on its first assignment only. When Python is converted to a BeExpression, a parameterless `main` that is called once, by the last top-level statement, becomes the main sequence again; any other `main` stays an ordinary function. Empty raw source in any language is therefore an empty program that survives every conversion (`StructuredProgrammingStateSpec`).

## Running Python

`ProgrammingStatePythonStructured.startExecution()(using ExecutionClient, ExecutionContext)` sends the native source to the existing `worker.runPython` command; supply the application's worker client. It returns `Future[RunPythonResult]` with captured stdout/stderr and also prints those streams to the caller's console when execution completes. Python exceptions fail the future; output emitted before the failure is retained in the worker diagnostic output and printed too.

```scala
given ExecutionClient = fullInfo.defaults.workerExecuter
val state = ProgrammingStatePythonStructured("from turtle import *\nforward(100)\nprintln(\"hai\")")
val result = state.startExecution() // stdout: "forward(100)\nhai\n"
```

This enables `RunPythonRequest.logTurtleCalls`: turtle methods log their called name and arguments instead of drawing, and `println` aliases `print`. Both imported and unqualified turtle calls work. The state API uses a fresh program namespace; explicit worker requests can retain globals with `resetGlobals = false`. Execution sets `__name__` to `__main__`, and restores the worker's previous turtle module even after an exception. Ordinary worker Python requests retain their existing behavior.
