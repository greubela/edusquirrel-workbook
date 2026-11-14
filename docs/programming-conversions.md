# Programming conversions

Language conversion belongs to [`it.evadid.vm.conversion`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion), in shared core. `ProgrammingState` and `SnapScalaModel` contain the representations and their current persistence codecs; editors select and invoke conversions without implementing them.

Import `it.evadid.vm.conversion.ProgrammingStateConversions.*` to use `state.toLanguage(language)`, `toBeExpression`, `toJava`, `toPython`, `toSnapXml` and `toSnapScalaModel`. These are VM extension methods, not methods implemented by individual state classes. Their dispatcher composes the converters below; there is no second set of converters in the workbook package.

| Transformation | Package under `vm.conversion` |
| --- | --- |
| Java source → expression / checked VM program | `javaexpression`, with checked lowering in `javaexpression.checked` |
| Python source → expression | `pythonexpression` |
| Snap XML → expression, with subset checks | `snapexpression` |
| Expression → Snap XML, preserving custom blocks and layout | `expressionsnap` |
| Expression → Java / JavaScript, Python, Lisp | `expressionjava`, `expressionpython`, `expressionlisp` |
| SQL source ↔ lossless clause blocks | `sqlblocks` |
| Python source → Snap XML | `pythonsnap` |
| Snap project → Python source and per-script views | `snappython` |
| Checked Java → editable Snap / Python | `javasnap`, `javapython` |
| Edited Java-semantic Snap / Python → Java | `snapjava`, `pythonjava` |
| Full Snap XML → immutable Snap document | `snapxmlmodel` |
| Immutable Snap document → lossless XML | `snapmodelxml` |
| TurtleStitch project / XML → expression and canvas layout | `turtlestitchexpression` |
| State → executable unit-test Python | `unittest` |

`javaediting` shares the metadata and type rules needed by the four Java editing directions. `snapxml` supplies XML scanning and entity handling; `printing` supplies the shared Java-like printer. Shared helpers are implementation support, not alternative conversion routes. Native Java/Python lexical and AST parsers remain in `vm.parsing`; execution remains in `vm.simulation`.

For full projects, `SnapScalaXmlCodec.parse(xml)` produces a `SnapScalaModel.Document`; import `vm.conversion.snapmodelxml.SnapModelToXml.*` to emit its original XML. This path preserves unknown nodes, authored assets and lexical details independently of the executable Snap subset. `ProgrammingStateSnapXml(xml).toSnapScalaModel` wraps that document as a programming state.

Java editing conversions retain their checked numeric, scope and evaluation-order semantics. This refactor changes ownership and imports, not supported language subsets or persisted state formats. See the [semantic design review](programming-conversion-review.md) for the remaining design tradeoffs.

The older TurtleStitch editor uses the same shared conversion layer. Its project adapter has no browser DOM dependency; every parse owns its variable-binding context. Browser mount, Snap runtime and drawing components remain in the client.

## Turtle source occurrences and execution

[`TurtleProgramAnalysis`](../modules/core/shared/src/main/scala/it/evadid/vm/simulation/turtle/TurtleProgramAnalysis.scala)
replaces the command-only expression interpreter. Both ordinary expressions and
checked Java roots produce `TurtleCommandExecutionInfo[Double]`:

```scala
import it.evadid.vm.conversion.ProgrammingStateConversions.*
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand

val info = state.toBeExpression.turtleCommandExecutionInfo
val forward = TurtleCommand.Forward(10.0)
val executionsWithDistance10 = info.nrOfCommandExecuted(forward)
val forwardExecutions = info.nrOfCommandExecutedIgnoreParameter(forward)
val forwardSites = info.nrOfCommandInCodeIgnoreParameter(forward)
val literalForward10Sites = info.nrOfLiteralCommandInCode(forward)
val symbolicForwardSites = info.nrOfUnresolvedCommandSitesInCode(forward)
```

`executedTurtleCommandsInOrder` contains evaluated commands, including repeated
loop/function executions. `turtleCommandSitesInCodeOrder` instead follows the
expression tree once, including both branches, zero-iteration loop bodies and
unused function definitions. Helper calls do not expand their definitions in
this traversal. Each site retains its structural path, canonical `BeEntityName`,
argument expressions and an optional concrete `literalCommand`. Paths distinguish
identical blocks within one source snapshot; they are not stable IDs across edits
or source line/column positions. Operator expressions and variables remain
symbolic; they are not counted as literal matches. Java signed/grouped/widened
numeric literals retain their literal values. Command aliases use the same
canonicalization for static and executed counts.

For `for i in range(3): forward(i * 10)` followed by `forward(10)`, there are two
source sites, one literal match for distance 10, one symbolic site, four forward
executions and two executions with distance 10. This is normalized program
analysis; exact native Snap block counts should use `SnapScalaModel` because
conversion can expand or normalize editor syntax.

`TurtleProgramAnalysis.commandSites(expression)` performs only the static walk.
Full analysis is synchronous; GUI consumers should run it in a Future as they do
other program execution. `executionStatus` distinguishes completion, limits,
cancellation and failure. Counts on an incomplete result describe only its
recorded prefix; source counts remain available. Rendering/grading uses
`completedCommands`, which rejects incomplete execution. The turtle-subset
interpreter bounds steps, commands, call depth and while iterations; checked Java
uses its existing bounded runtime and maps its status into the same result.
Limits and cancellation can be supplied to `TurtleProgramAnalysis.apply`.

The analysis result and call sites are runtime views holding AST references,
including checked Java nodes; they are not persisted answer schemas. Persist the
`ProgrammingState`, then analyze that snapshot when needed. The numeric result
container itself requires no `Fractional` instance and derives its counts from
its two ordered collections rather than storing parallel counters.
