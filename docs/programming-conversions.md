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

Drawing and console execution are described in [analysis and execution](program-analysis-and-execution.md);
`JavaTurtleCompiler`/`JavaTurtleInterpreter` are a separate checked-Java runtime that never
selects conversion routes. Snap rejects object features without a supported representation. Source frontends own interpretation and
validation, printers own syntax: unsupported features are rejected or represented explicitly,
never silently dropped, and UI components do not duplicate conversion dispatch. Extend supported
subsets with semantic round-trip tests, not alternate interpreters or source replay.

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

Language-specific spellings are printer concerns: Snap slots hold a string's text (never its
quoted literal), `join`/`say`/`pick random` map to text `+`, printing and the shared `random()`
built-in, and Java uses `Math.*` where Python imports from `math`/`random`.
`NativeSnapRoundTripSpec` runs a project saved by native Snap through Python and Java.

## Project downloads

`ProjectExport` produces BlueJ Java projects, Python `main.py` or Snap XML with required
libraries and exercise data; single-file projects download as that file, others as a ZIP
(`ZipArchive`). `InfoUtil.downloadName` names every download (time, then subject).
