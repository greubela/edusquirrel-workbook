package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapProgramDerivation
import it.evadid.distribution.clients.PythonExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands.{PythonCallback, RunPythonRequest}
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonHelpers
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog

import java.util.concurrent.{CancellationException, TimeoutException}
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.{Failure, Success, Try}

/** Asynchronous boundary used by the editor to execute its derived Python. */
trait TurtleCommandRunner:
  def execute(python: String): Future[List[TurtleCommand[Double]]]

/** Derives and executes a Snap project without modifying editor state. */
final class SnapTurtleCommandExecution(runner: TurtleCommandRunner):
  def commandsFor(state: ProgrammingStateSnapXml): Future[List[TurtleCommand[Double]]] =
    if it.evadid.workbook.elements.interactionElements.programming.state.snap.JavaTurtleEditingBridge.hasSnapMetadata(state.snapXml) then
      return Try(state.toPython.code).fold(Future.failed, runner.execute)
    val derived = SnapProgramDerivation.fromState(state)
    if !derived.pythonCompatible then
      Future.failed(IllegalArgumentException(
        derived.applyBlockedMessage.getOrElse("The Snap project cannot be converted to Python")
      ))
    else runner.execute(derived.python)

/** Executes derived Snap Python in the integrated Pyodide Web Worker. The
  * returned callbacks therefore describe runtime calls (including loop and
  * function expansion), rather than statically present blocks.
  */
final class PyodideTurtleCommandRunner(
    executorFactory: () => PythonExecutionClient = () => PyodideTurtleCommandRunner.workerExecutor(),
    startupTimeoutMs: Int = 120000,
    executionTimeoutMs: Int = 10000
) extends TurtleCommandRunner:
  def this(executor: PythonExecutionClient) = this(() => executor)

  require(startupTimeoutMs > 0 && executionTimeoutMs > 0, "Worker timeouts must be positive.")
  private class Run(val executor: PythonExecutionClient):
    val result = Promise[List[TurtleCommand[Double]]]()
    var timer = Option.empty[SetTimeoutHandle]

  private var executor = Option.empty[PythonExecutionClient]
  private var active = Option.empty[Run]

  def close(): Unit = active match {
    case Some(run) => finish(run, Failure(CancellationException("Execution cancelled.")), discard = true)
    case None => discardExecutor()
  }

  private def discardExecutor(): Unit = {
    val previous = executor
    executor = None
    previous.foreach(value => Try(value.close()))
  }

  private def current(run: Run): Boolean = active.exists(_ eq run)

  private def finish(run: Run, result: Try[List[TurtleCommand[Double]]], discard: Boolean): Unit = if current(run) then {
    run.timer.foreach(clearTimeout)
    active = None
    if discard then discardExecutor()
    run.result.tryComplete(result)
  }

  private def deadline(run: Run, milliseconds: Int, phase: String): Unit = {
    run.timer.foreach(clearTimeout)
    run.timer = Some(setTimeout(milliseconds.toDouble) {
      finish(run, Failure(TimeoutException(s"Python worker $phase timed out.")), discard = true)
    })
  }

  override def execute(python: String): Future[List[TurtleCommand[Double]]] = {
    if active.nonEmpty then return Future.failed(IllegalStateException("Execution is already running."))
    Try(executor.getOrElse {
      val created = executorFactory()
      executor = Some(created)
      created
    }) match {
      case Failure(error) => Future.failed(error)
      case Success(transport) =>
        val run = new Run(transport)
        active = Some(run)
        deadline(run, startupTimeoutMs, "startup")
        Try(transport.ready())
          .fold(Future.failed, identity).onComplete {
            case Success(_) if current(run) =>
              deadline(run, executionTimeoutMs, "execution")
              Try(transport.run(RunPythonRequest(s"${JavaTurtlePythonHelpers.installationSource}from ${PyodideTurtleCommandRunner.ModuleName} import *\n$python",
                callbacks = Map(PyodideTurtleCommandRunner.ModuleName -> SnapTurtleCatalog.AllowedPythonNames.toList.sorted)))).fold(Future.failed, identity).onComplete {
                  case Success(report) if current(run) =>
                    val commands = Try {
                      if report.callbackOps.size > 10000 then throw IllegalArgumentException("Your drawing contains too many commands.")
                      PyodideTurtleCommandRunner.commandsFrom(report.callbackOps)
                    }
                    finish(run, commands, discard = commands.isFailure)
                  case Failure(error) => finish(run, Failure(error), discard = true)
                  case _ => ()
                }
            case Failure(error) => finish(run, Failure(error), discard = true)
            case _ => ()
          }
        run.result.future
    }
  }

object PyodideTurtleCommandRunner:
  private[execution] val ModuleName = "turtle"

  private def workerExecutor(): PythonExecutionClient =
    PythonExecutionClient.worker(it.evadid.homepage.control.startup.ClientRuntimeConfig.workerUrl)

  private[execution] def commandsFrom(callbacks: Seq[PythonCallback]): List[TurtleCommand[Double]] =
    callbacks.iterator
      .filter(_.module == ModuleName)
      .map(toCommand)
      .toList

  private def toCommand(callback: PythonCallback): TurtleCommand[Double] =
    val canonicalName = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(
      callback.method,
      throw IllegalArgumentException(s"Unsupported turtle callback: ${callback.method}")
    )
    val (numeric, textual) = callback.args.foldLeft((List.empty[Double], List.empty[String])) {
      case ((numbers, strings), ujson.Num(number)) =>
        if !number.isFinite then
          throw IllegalArgumentException(s"Non-finite argument for ${callback.method}: $number")
        (numbers :+ number, strings)
      case ((numbers, strings), ujson.Str(value)) =>
        (numbers, strings :+ value)
      case ((numbers, strings), ujson.Bool(value)) =>
        (numbers, strings :+ value.toString)
      case (_, value) =>
        throw IllegalArgumentException(
          s"Unsupported argument for ${callback.method}: ${value.getClass.getSimpleName}"
        )
    }
    TurtleCommand(canonicalName, numeric, textual)
