# Programming conversions

Each immutable state owns `ProgrammingStateDerivations`, shared by views and graders.
`convertToLanguage` returns a structured target state plus diagnostics; direct
projections include `toBeExpression`, `toJava`, `toPython` and `toSnapXml`.

```
source state → SourceParsing stages → BeExpression → target printer → target state
```

Typed Snap documents enter the converter directly; XML/model conversion is lossless
and needs no executable tree. Prefer structured Java/Python and typed Snap answers;
`ProgrammingStateRaw(language, code)` serves text/XML boundaries and preserves native
text. See [source parsing](source-parsing.md) for the single textual entry point.

| Responsibility | Location under `vm` |
| --- | --- |
| Source stages for Java, Python and Snap | `parsing.source` |
| Shared Java/Python model and binding | `conversion.parsed` |
| Language adapters | `conversion.javaexpression`, `pythonexpression`, `snapexpression` |
| Printers and module layout | `conversion.printing`, `expressionjava`, `expressionpython`, `expressionsnap` |
| Class hoisting / structured states | `conversion.structured` |
| Lossless Snap XML writer / program mapping | `conversion.snapmodelxml`, `turtlestitchexpression` |
| Layout-preserving Python apply / SQL blocks | `conversion.pythonsnap`, `sqlblocks` |

## Neutral object model

`BeExpression` represents definitions, calls, values and control flow independently
of source language. `BeFunctionCall.target` distinguishes free calls, static methods,
instance methods and constructors; receivers and members are explicit nodes. Printers
supply target syntax such as Python's `self`. The tree contains no checked-Java runtime
nodes or metadata that selects alternate semantics.

All drawings use the shared interpreter. `JavaTurtleCompiler`/`JavaTurtleInterpreter`
are a separate checked-Java runtime; Java unit-test instrumentation does not select
editable conversion routes. Console execution uses Pyodide
([analysis and execution](program-analysis-and-execution.md)). Snap rejects object
features without a supported representation. Extend supported subsets with semantic
round-trip tests, not alternate interpreters or source replay.

## Libraries and built-ins

`BeIntrinsic` identifies built-in behavior independently of names. `BeDependency`
holds library definitions; `KnownDependencies` supplies them. Printers add required
libraries, and parsers mask only exact definitions (ignoring layout). Edited copies
remain user code. `JavaSpelling` owns Java operator/built-in spellings in both directions.

Unresolved paths bind to `BeUnknownDependency`: for example, `System.out2.println(x)`
requires a root `System`, an `out2` attribute and its receiver's method. Generated
stubs make the exported structure compile; they do not implement unknown behavior.

### Exercise environments

`ProgrammingEnvironment` selects available libraries. `state.inEnvironment(env)`
shares derivations for that environment; ordinary state projections use all libraries.
Exercises pass their environment to graders, Eva conversions and worker analysis.
Language operators/built-ins always exist; unavailable library calls bind as unknown
code. Snap's palette controls available blocks. See `ProgrammingLibrary` and
`ProgrammingExerciseEnvironment` for library names and exercise defaults.

## Loops

`ParsedStatement.countingLoop` lowers both languages consistently: unused literal
counters become `BeRepeatNr`, unit steps become inclusive `BeFor`, and other steps
become initialization plus `BeWhile`. Empty literal ranges remain empty; mutable
bounds/counters must preserve their original behavior. `CountingLoopSpec` and
`ParsedStructureSpec` cover these decisions.

## Modules and the shared interpreter

`BeModule[C]` owns typed library calls, intrinsic identities and dependency definitions
for Turtle, NumberList and Sudoku. `BeProgramInterpreter` executes startup or a chosen
`ProgramEntry` on a `DataWorld`, recording module calls in order. Expression calls run
before their consumer; boolean operators short-circuit and recursive calls isolate locals.

```scala
state.toBeExpression.moduleCommands(BeModules.Turtle)
run.completedModuleCommands(BeModules.NumberList) // refuses partial executions
```

Snap module blocks use ordinary runnable definitions and saved data. Import recognizes
them as library functionality; export restores them. `turtleCommandExecutionInfo`
provides the shared turtle status and commands used by drawing and grading.

## Types of untyped functions

`ParsedTypeInference` combines call-site and usage evidence for unannotated parameters
and return values. Explicit annotations survive; conflicting evidence remains `Any`
(Java `Object`). Snap numeric types do not preserve Java's integer/double distinction.

## Functions that return a value

Declared results round-trip between Java and Python. Snap uses reporter/predicate
blocks, with `report` ending the function. Unused reporter results, bare returns and
undeclared return types without a supported Snap form are rejected. See
`ReturningFunctionConversionSpec` and `ParsedTypeInferenceSpec`.

## Unfinished and unsupported source

`BeExpressionUnparsable` retains unreadable source; `BeExpressionUnsupported` retains
recognized constructs without a shared form. Native editing preserves both.
Cross-language conversion rejects the first such construct with its category and
available source location, rather than returning a partial program. Malformed XML
fails syntax validation. Drawing stops at placeholders. `IncompleteSourceConversionSpec`
checks every language pair.

## Entry points and empty programs

`ModuleLayout` gives startup one canonical form: Snap's green flag, Java's static
`Main.main`, and a called Python `main`. Object classes precede module code. Literal
module initializers may become fields; other initialization stays in execution order.
Python module assignments retain annotations and `global` declarations. A parameterless
`main` called once as the final top-level statement lowers back to startup.

Detached Snap scripts use the reserved, uncalled `unusedCode` function and export as
loose blocks. Empty source remains an empty program across all conversions, including
a green flag in Snap. `StructuredProgrammingStateSpec` tests these contracts.

Snap slots hold a string's text, never its quoted literal (`StringLiteral.content`); Java re-quotes Python's
`'...'` strings the same way.
`say` and `say … for … secs` print (the duration is dropped); `join` is a text `+` whose
non-text items are `str(...)`, and a text `+` or `str(...)` is written back as `join`.
`NativeSnapRoundTripSpec` runs a project saved by native Snap through Python and Java.

`sqrt` and `random()` are shared built-ins: Python `from math import sqrt` / `from random import
random`, Java `Math.sqrt` / `Math.random()`, Snap `sqrt of` and `pick random 0 to 0.9999999999999999`
(a fractional bound draws fractions). `pow(a, b)` reads as `a ** b`. Python strings print
double-quoted, and an exact Python `56 / 8` prints as Java `56.0 / 8` (read back as `56 / 8`).

## Running Python

`ProgrammingStatePythonStructured.startExecution` uses `worker.runPython` and returns
captured stdout/stderr. Its turtle calls are logged; Eva's console ignores turtle calls
because drawing is separate. Exceptions fail execution with diagnostic output retained.
See [program analysis and execution](program-analysis-and-execution.md).

## Project downloads

`ProjectExport` produces BlueJ Java projects, Python `main.py` or Snap XML, adding
required libraries and exercise data. `ExportedProject.zip` uses the dependency-free
`ZipArchive`; client `ProjectDownload` saves it. Cross-language round-trip tests compare
behavior after formatting normalization; checked-Java runtime tests remain separate.
