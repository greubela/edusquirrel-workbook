package it.evadid.homepage.webElements.editor.code.program

import com.raquo.laminar.api.L.*
import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.homepage.webElements.editor.code.worker.{LatestResult, LatestWorkerRequest}
import it.evadid.vm.analysis.ProgramAnalysis
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success}

/** Static [[ProgramAnalysis]] of programming states, computed in the workbook worker so that parsing and the
  * bounded BeExpression interpreter never block the page. Only the latest state is analysed; its result is cached.
  * The owner closes the analyzer, e.g. when its component unmounts.
  */
final class ProgramAnalyzer(createWorker: () => WebWorkerExecutionClient = () => LatestWorkerRequest.workbookWorker()) {
  import ProgramAnalyzer.Status

  private val request = LatestWorkerRequest(WorkerCommands.programAnalysis, identity[ProgrammingState], createWorker,
    Some(LatestResult[ProgrammingState, ProgramAnalysis]()))

  def analyse(state: ProgrammingState): Future[ProgramAnalysis] = request(state)

  /** The analysis of `source`'s current state; a result for an earlier state is never shown. */
  def statusOf(source: Signal[ProgrammingState]): Signal[Status] =
    source.flatMapSwitch { state =>
      val completed = analyse(state).transform(outcome => Success(outcome match {
        case Success(analysis) => Status.Ready(analysis)
        case Failure(error) => Status.Failed(Option(error.getMessage).getOrElse(error.toString))
      }))
      Signal.fromFuture(completed, Status.Pending).map(state -> _)
    }.combineWith(source).map((analysed, status, current) => if analysed == current then status else Status.Pending)

  def close(): Unit = request.close()
}

object ProgramAnalyzer {
  enum Status {
    case Pending
    case Ready(analysis: ProgramAnalysis)
    case Failed(reason: String)
  }
}
