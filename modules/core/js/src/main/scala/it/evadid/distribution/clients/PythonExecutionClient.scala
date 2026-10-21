package it.evadid.distribution.clients

import it.evadid.distribution.command.ExecutionCommand
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult}
import it.evadid.facades.Workers
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** A consumer-owned interpreter using the shared typed command protocol. */
trait PythonExecutionClient {
  def ready(): Future[Unit] = run(RunPythonRequest("")).map(_ => ())
  def run(request: RunPythonRequest): Future[RunPythonResult]
  def close(): Unit = ()
}

object PythonExecutionClient {
  final case class Failure(message: String, stdout: String, stderr: String) extends RuntimeException(message)

  def worker(url: String): PythonExecutionClient = new PythonExecutionClient {
    private val transport = WebWorkerExecutionClient.fromWorker(Workers.classic(url).createLoadedInstance())
    private val logger = Logger.withNameAndPrefixes(Some("Python"), PrintToStdLogger.printNothing)

    override def run(request: RunPythonRequest): Future[RunPythonResult] =
      transport.executeResponse(logger, ExecutionCommand(WorkerCommands.runPython.name, upickle.default.writeJs(request))).flatMap { reply =>
        reply.result match {
          case Left(error) => Future.failed(Failure(error.getMessage,
            reply.additionalOutput.loggerOut, reply.additionalOutput.loggerError))
          case Right(encoded) => Future.successful(encoded.getAs[RunPythonResult])
        }
      }

    override def close(): Unit = transport.close()
  }
}
