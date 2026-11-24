# Program analysis and execution

Programs are handled in two ways, independently of the editor language:

| | Static | Dynamic |
| --- | --- | --- |
| Engine | shared BeExpression (bounded interpreter, no side effects) | Pyodide in the workbook worker |
| Provides | turtle commands, called libraries, unknown dependencies, unsupported source, the Python form of the program | console output (stdout/stderr), network access, real Python semantics |
| Entry point | [`ProgramAnalysis.of`](../modules/core/shared/src/main/scala/it/evadid/vm/analysis/ProgramAnalysis.scala) (core), `worker.programAnalysis` | `worker.runPython` with `PythonTurtleCalls.Ignored` |
| Client | [`ProgramAnalyzer`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/program/ProgramAnalyzer.scala) | [`ProgramRunner`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/program/ProgramRunner.scala) |

Drawings are always static: previews, step panels, graders and
`StaticTurtleDrawing` use the BeExpression turtle commands of every language,
Java classes and Snap projects included. No client component executes a program
to obtain a drawing. Console runs are always dynamic: Python runs as written; other
languages run as the Python their BeExpression prints. When no Python form exists,
`ProgramAnalysis.python` is a `Left` with the reason, and the console disables
Run and shows it.

Static work is bounded, so the whole page shares one lazily created analysis worker
([`SharedWorker.analysis`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/worker/SharedWorker.scala)):
`ProgramAnalyzer.shared` and the CodeMirror flowchart gutter use it through a
`SharedWorkerCache`, which shares identical requests, remembers recent results and
forgets failures. A stopped worker is replaced on the next request. Consumers ignore
results for outdated states. Runs may not end, so each mounted console's `ProgramRunner`
starts its own worker on Run through `LatestWorkerRequest`: a newer run, Stop or an
edit terminates it.

```scala
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.program.{ProgramAnalyzer, ProgramRunner}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

def consoleOutput(answer: Signal[ProgrammingState]): Element = {
  val analyzer = ProgramAnalyzer.shared
  val runner = ProgramRunner()
  val output = Var("")
  div(
    onUnmountCallback(_ => runner.close()),
    button("Run", onClick.compose(_.sample(analyzer.statusOf(answer))) --> {
      case ProgramAnalyzer.Status.Ready(analysis) =>
        analysis.python.foreach(code => runner.run(code).foreach(result => output.set(result.stdout)))
      case _ => ()
    }),
    pre(child.text <-- output.signal))
}
```

`ProgrammingExecutionPanel` is this console below Eva's editor, with Stop,
localized states and the stderr stream. Specs replace `worker.js` with
`LocalWorker`, which answers the same protocol through local execution clients.
