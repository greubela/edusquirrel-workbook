package it.evadid.homepage.webElements.editor.code.program

import com.raquo.laminar.api.L.*
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.code.worker.{SharedWorker, SharedWorkerCache}
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.workbook.interactions.programming.state.ProgrammingState

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}

/** Static [[ProgramAnalysis]] of programming states, computed asynchronously in the page's shared analysis
  * worker, so parsing and the bounded BeExpression interpreter never block the page. Results are shared by all
  * consumers (console, previews) and remembered per state.
  */
final class ProgramAnalyzer(worker: SharedWorker) {
  import ProgramAnalyzer.Status

  private val results = SharedWorkerCache(WorkerCommands.programAnalysis, identity[WorkerCommands.ProgramAnalysisRequest], worker)

  /** The analysis of `state` in an exercise providing `environment`'s libraries. */
  def analyse(state: ProgrammingState, environment: ProgrammingEnvironment): Future[ProgramAnalysis] =
    results(WorkerCommands.ProgramAnalysisRequest(state, environment))

  /** The analysis of `source`'s current state; a result for an earlier state is never shown. */
  def statusOf(source: Signal[ProgrammingState], environment: ProgrammingEnvironment): Signal[Status] =
    source.flatMapSwitch { state =>
      val completed = analyse(state, environment).transform(outcome => Success(outcome match {
        case Success(analysis) => Status.Ready(analysis)
        case Failure(error) => Status.Failed(Option(error.getMessage).getOrElse(error.toString))
      }))
      Signal.fromFuture(completed, Status.Pending).map(state -> _)
    }.combineWith(source).map((analysed, status, current) => if analysed == current then status else Status.Pending)
}

object ProgramAnalyzer {
  lazy val shared: ProgramAnalyzer = ProgramAnalyzer(SharedWorker.analysis)

  /** `view` of the latest ready analysis in `status`: kept while the next one is pending, `empty` after a failure. */
  def latest[A](status: Signal[Status], empty: A)(view: ProgramAnalysis => A): Signal[A] = {
    def step(last: A, next: Status): A = next match {
      case Status.Ready(analysis) => view(analysis)
      case Status.Pending => last
      case Status.Failed(_) => empty
    }
    status.scanLeft(step(empty, _))(step)
  }

  enum Status {
    case Pending
    case Ready(analysis: ProgramAnalysis)
    case Failed(reason: String)
  }
}
