package it.evadid.homepage.webElements.editor.code.program

import it.evadid.distribution.clients.WebWorkerExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult}
import it.evadid.homepage.webElements.editor.code.worker.LatestWorkerRequest
import it.evadid.vm.simulation.python.PythonTurtleCalls

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Dynamic execution: runs Python (as written, or converted from another language by [[ProgramAnalyzer]]) in
  * Pyodide inside the workbook worker, with console output, network access and real Python semantics.
  *
  * Turtle calls do nothing here; drawings are static and come from the BeExpression analysis. A new run or
  * [[cancel]] terminates the previous run's worker, which also stops an endless loop.
  */
final class ProgramRunner(createWorker: () => WebWorkerExecutionClient = () => LatestWorkerRequest.workbookWorker(120000)) {
  /** One run; identity keeps two runs of the same program from sharing a result. */
  private final class Run(val request: RunPythonRequest)

  private val request = LatestWorkerRequest[Run, RunPythonRequest, RunPythonResult](WorkerCommands.runPython, _.request, createWorker)

  def run(python: String): Future[ProgramRunner.Output] =
    request(Run(RunPythonRequest(python, turtleCalls = PythonTurtleCalls.Ignored))).map(result => ProgramRunner.Output(result.stdout, result.stderr))

  def cancel(): Unit = request.cancel()

  def close(): Unit = request.close()
}

object ProgramRunner {
  final case class Output(stdout: String, stderr: String)
}
