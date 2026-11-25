# Programming conversions

Each immutable programming state owns one [`ProgrammingStateDerivations`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/ProgrammingStateDerivations.scala), shared by views and graders. The central `convertToLanguage` method returns a `ConversionResult` containing the native structured state plus diagnostics. Direct state methods expose `toBeExpression`, `toJava`, `toPython`, `toSnapXml`, `toJavaStructured` and `toPythonStructured`.

Cross-language conversion follows one route:

```
source state → source frontend → BeExpression → target printer → target state
```

`ProgrammingStateRaw(language, code)` is the single source transport type. Prefer structured Java/Python states and the typed Snap model for persisted answers; raw source is useful for imports and unfinished text editing, and remains permitted in histories. Raw source projections preserve the original text. Snap XML ↔ the full Snap model remains a lossless representation conversion; it does not need an executable expression tree. Structured state constructors retain unfinished source for editing, but cross-language conversion requires supported syntax.

**Consistency requirement.** A program behaves the same whichever state it starts in: every frontend produces the shared tree, every printer reads it, and Python and Java share one binder. A language subset that cannot be represented is rejected or kept as an explicit unsupported node rather than converted differently.

`BeExpression` stores language-neutral definitions, calls, values and control flow. It contains no checked-Java runtime nodes, original Java source, target-language names, metadata-selected semantic modes or implicit Python receiver parameters. Printers supply target syntax, including Python's `self`. Explicit `BeExpressionUnsupported` and `BeExpressionUnparsable` nodes can retain unsupported source for diagnostics.

Source text is read only through `vm.parsing.source.SourceParsing.parse` ([source parsing](source-parsing.md)); the packages below consume its steps.

| Responsibility | Package below `vm.conversion` |
| --- | --- |
| Shared parsed model and binding for Python and Java ([source parsing](source-parsing.md)) | `parsed` |
| Python frontend (fastparse syntax tree → parsed model) | `pythonexpression` |
| Java frontend (fastparse syntax tree → parsed model) | `javaexpression` |
| Snap frontend, startup and detached scripts | `snapexpression.SnapToExpression`, using `turtlestitchexpression` |
| Shared-tree printers and the shared module layout | `printing`, `expressionpython`, `expressionjava`, `expressionsnap`, `expressionlisp` |
| Structured-state grouping and class hoisting over step-1 declarations | `structured` |
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

Further work should extend the supported type and runtime subsets together with semantic roundtrip tests. Structured and raw sources of a language must use the same execution backend. Drawings of every language come from the shared BeExpression; console runs execute the Python form in Pyodide ([program analysis and execution](program-analysis-and-execution.md)).

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

Calls that neither the program nor a known dependency defines bind to placeholder functions named after their written path. [`UnknownDependencies`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/dependencies/UnknownDependencies.scala) collects them into a [`BeUnknownDependency`](../modules/core/shared/src/main/scala/it/evadid/vm/code/defining/BeUnknownDependency.scala) (`ProgrammingStateBeExpression.unknownDependency`): `System.out2.println(x)` needs a static root class `System` whose attribute `out2` holds a `SystemOut2` with a `println` method, and `log(x)` a free function. `javaClasses`/`javaFunctions` and `pythonDefinitions` print variadic stubs that make the program compile; `UnknownDependencySpec` checks the Java stubs with `javac`. A stub named like a `java.lang` class shadows it (`System` keeps `out` and `err`). Unknown attribute reads and writes such as `Config.screen.limit` and `Config.screen.limit = 3` stay unsupported expressions (keeping their source) in the tree, but when their root is bound nowhere in the program they become attributes of the same synthesized classes (stubbed as `null`/`None`).

### Exercise environments

A [`ProgrammingEnvironment`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/dependencies/ProgrammingEnvironment.scala) is the set of `ProgrammingLibrary`s (`Turtle`, `System`, `Web`, `NumberList`, `Sudoku`) an exercise provides by default; language built-ins (operators, `int`, `max`, …) always exist. `ParseContext.resolveFunction` binds library calls only to provided libraries, so a call into another library (`forward(10)` without `Turtle`, `System.out.println` without `System`) binds like undefined code under its written path and becomes part of the `BeUnknownDependency`.

- Exercises implement `ProgrammingExerciseEnvironment`; content may set `environment` (stored as library names, written only when it differs from the default). Defaults: `TurtleRecreateShapeInteraction` Turtle + System, `SortListExercise` NumberList + System, `ProgrammingExerciseFullJava` and `ProgrammingExercise` all libraries.
- `state.inEnvironment(environment)` gives the state's derivations (parsing, conversions) in that environment, computed once per environment; `state.derivations`/`toBeExpression` mean all libraries.
- Exercises hand their environment to their graders (`ProgrammingTestGrader.inEnvironment`); static graders read the program in it, while `ProgrammingUnitTestGrader` runs it as written in Pyodide.
- The client reads it from the exercise: Eva's conversions and console (`EvaEditorEnvironment.programming`), the Java editor's analysis, previews and step panels (`ProgramAnalyzer` requests carry it, `worker.programAnalysis`).
- Snap reads blocks from the exercise's palette, so its conversion does not apply an environment.

The Java `Turtle` methods are generated from the shared turtle signatures. Java uses the turtle API of the checked exercise (`turnLeft`/`turnRight`), and `goto` is spelled `setPosition` because `goto` is reserved in Java. Library classes are runtime, not student code. The checked Java runner masks them with whitespace before compiling, which keeps offsets valid, and the Java method editor does not list them. An edited copy is listed again. [`JavaSpelling`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/expressionjava/JavaSpelling.scala) is the single table for Java operators (`&&`, `||`, `!`) and built-ins in both directions:

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

## Modules and the shared interpreter

Predefined modules ([`BeModules`](../modules/core/shared/src/main/scala/it/evadid/vm/modules/BeModule.scala)) are libraries of static functions: `Turtle`, `NumberList` (sorting exercises) and `Sudoku`. A `BeModule[C]` names its functions (`library`), marks them with its `BeIntrinsic`, holds the `BeDependency` definitions printers add (the turtle's), and types its recorded calls as `C` (`TurtleCommand[Double]`, `ListCommand`, `SudokuCommand`). Data-module calls bind only when qualified (`NumberList.get(i)`) and print qualified in Java and Python.

In Snap, module functions are predefined custom blocks ([`SnapModuleBlocks`](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapModuleBlocks.scala)), currently for `NumberList`: `item %n of number list`, `set item %n of number list to %n`, `swap items %n and %n of number list` and `length of number list`, with positions from 0 as in Python and Java. Their definitions work on the project variable `number list` (Snap lists), so the blocks also run in Snap. The reader identifies a block by its label words and slot count and binds its calls to the module function; the definitions and the variable are not program code. The writer adds all of the module's definitions and the variable whenever the program calls the module.

[`BeProgramInterpreter`](../modules/core/shared/src/main/scala/it/evadid/vm/simulation/BeProgramInterpreter.scala) executes the shared tree: numbers (booleans are 1/0) and text, control flow, functions with return values and recursion, built-ins and module functions. A run starts at startup or at one function (`ProgramEntry`) on a `DataWorld`; its `ProgramExecution` records every module call in program order. Extraction takes the module:

```scala
expression.moduleCalls(BeModules.Sudoku)                 // call sites in the code
state.toBeExpression.moduleCommands(BeModules.Turtle)    // startup run on the default data
run.completedModuleCommands(BeModules.NumberList)        // List[ListCommand]; refuses partial runs
```

`turtleCommandExecutionInfo` (via `TurtleProgramAnalysis`) remains the turtle view with call sites and status. Calls inside an expression run first; `and`/`or` skip their right operand once the left one decides. Only module variables flow back from a call, so recursion keeps its caller's locals.

## Types of untyped functions

Python functions need no annotations, but Java does. Before the shared tree is built,
[ParsedTypeInference](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/parsed/ParsedTypeInference.scala)
gives an unannotated parameter the one type its evidence agrees on (arguments passed to it, its use as a drawing
argument or arithmetic operand), and a function returning a value the type its returns share, else `Any`. Annotations
are kept; conflicting evidence leaves `Any` (Java `Object`). See `ParsedTypeInferenceSpec`.

## Functions that return a value

A function with a declared result (`-> float`, `-> bool`, a Java return type) converts between Python and Java
as is. In Snap it is a custom `reporter` block, or a `predicate` when it returns a truth value; `return v`
becomes `report`, and calls are reporter inputs. Reading Snap back, a reporter's result type is the one type all
its reports share (arithmetic is a number, comparisons and logic a truth value), otherwise `Any`. Snap has no
command form of a reporter, so a call whose value is unused, a bare `return` and a function that returns a value
without declaring a result are reported as unsupported for Snap. The drawing simulation runs these functions:
a call's own drawing happens before the statement using its value, and `return` ends the function, also
inside loops. See `ReturningFunctionConversionSpec` and the `py-reporters` native Snap fixture.

## Unfinished and unsupported source

Front ends keep source they cannot read as `BeExpressionUnparsable` and recognised source without a shared form as `BeExpressionUnsupported`. Placeholders keep their text, so editing in the same language never loses code.

[`ProgrammingStateDerivations.convertToLanguage`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/ProgrammingStateDerivations.scala) rejects conversion to another language at the first placeholder, whatever the source language. The diagnostic gives:

- the kind (`Unparsable` or `UnsupportedConstruct`);
- the line and column in the student's text, since parsers mask library definitions rather than remove them;
- no partial program.

Malformed Snap XML is unparsable. The drawing interpreter stops with a failure at a placeholder instead of skipping it. `IncompleteSourceConversionSpec` covers these rules for every pair of languages and checks that conversion never throws.

Python's `import turtle`, `from turtle import *` and `from turtle import …` all mean the turtle library. `import turtle as t` is reported as unsupported.

## Entry points and empty programs

The main sequence has one canonical shape per language, laid out by `ModuleLayout` for both printers. Snap marks it with a green flag, which is emitted even when no blocks follow. Java prints object classes, then `class Main` with static fields and methods and `main(String[] unusableArgs)`; empty source becomes `Main` with an empty `main`. Python prints classes, module declarations, functions and literal-initialised variables, then a called `def main()` ([`PythonEntryPoint`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/expressionpython/PythonEntryPoint.scala)). Every module variable assigned in `main` is declared with its type at module level (`x: float`, `static double x;`); Python's `main` declares it `global`, and a local variable is annotated on its first assignment only. When Python is converted to a BeExpression, a parameterless `main` that is called once, by the last top-level statement, becomes the main sequence again; any other `main` stays an ordinary function. Empty raw source in any language is therefore an empty program that survives every conversion (`StructuredProgrammingStateSpec`).

## Running Python

`ProgrammingStatePythonStructured.startExecution()(using ExecutionClient, ExecutionContext)` sends the native source to the existing `worker.runPython` command; supply the application's worker client. It returns `Future[RunPythonResult]` with captured stdout/stderr and also prints those streams to the caller's console when execution completes. Python exceptions fail the future; output emitted before the failure is retained in the worker diagnostic output and printed too.

```scala
given ExecutionClient = fullInfo.defaults.workerExecuter
val state = ProgrammingStatePythonStructured("from turtle import *\nforward(100)\nprintln(\"hai\")")
val result = state.startExecution() // stdout: "forward(100)\nhai\n"
```

This is `RunPythonRequest.turtleCalls = PythonTurtleCalls.Logged`: turtle methods log their called name and arguments instead of drawing (`Ignored` makes them no-ops, as the Eva console uses), and `println` aliases `print`. Both imported and unqualified turtle calls work. The state API uses a fresh program namespace; explicit worker requests can retain globals with `resetGlobals = false`. Execution sets `__name__` to `__main__`, and restores the worker's previous turtle module even after an exception. Ordinary worker Python requests retain their existing behavior.

## Project downloads

[`ProjectExport`](../modules/core/shared/src/main/scala/it/evadid/vm/projectexport/ProjectExport.scala) turns a programming state into an `ExportedProject` (one folder of text files) for another IDE: Java as a BlueJ project (`JavaProjectExport`: `package.bluej`, `README.TXT`, one `<Class>.java` per structured class with all imports and no `package` declaration), Python as `main.py`, Snap as the project XML. Programs in another language are converted first. Library classes the program calls are added unless the program defines that class: `Turtle` (with forwarding methods for the turtle spellings it uses, e.g. `right`), `Web`, and `NumberList`/`Sudoku` from `DataLibrarySources` filled with the exercise's `DataWorld`. `ExportedProject.zip` uses the dependency-free `ZipArchive` (stored entries, CRC-32, UTF-8 names), so browser and JVM produce the same archive; the client's `ProjectDownload` saves it through a Blob link.
