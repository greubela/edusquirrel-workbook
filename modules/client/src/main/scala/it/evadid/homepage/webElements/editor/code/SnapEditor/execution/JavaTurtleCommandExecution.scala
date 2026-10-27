package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.distribution.clients.PythonExecutionClient
import it.evadid.distribution.commandTypes.WorkerCommands.{RunPythonRequest, RunPythonResult}
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleExecutionReport, JavaTurtleRuntime as T}

import java.util.concurrent.{CancellationException, TimeoutException}
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

final class JavaTurtleCommandRunner(
    workerFactory: () => PythonExecutionClient = JavaTurtleCommandRunner.workerFactory,
    startupTimeoutMs: Int = 120000,
    executionTimeoutMs: Int = 10000
) {
  require(startupTimeoutMs > 0 && executionTimeoutMs > 0, "Java worker timeouts must be positive.")

  private case class WorkerSlot(worker: PythonExecutionClient, ready: Future[Unit])
  private class Run(val slot: WorkerSlot) {
    val promise: Promise[T.Execution] = Promise()
    var timer: Option[SetTimeoutHandle] = None
  }

  private var worker: Option[WorkerSlot] = None
  private var active: Option[Run] = None
  private var closed = false

  def run(program: P.Program, limits: T.Limits = T.Limits()): Future[T.Execution] =
    execute(program, None, Vector.empty, limits)

  def invoke(program: P.Program, method: R.MethodId, arguments: Vector[E.Value],
      limits: T.Limits = T.Limits()): Future[T.Execution] =
    execute(program, Some(method), arguments, limits)

  def cancel(): Boolean = active match {
    case Some(run) =>
      finish(run, Failure(new CancellationException("Java execution cancelled.")), discard = true)
      true
    case None => false
  }

  def close(): Unit = {
    closed = true
    if !cancel() then discardWorker()
  }

  private def current(run: Run): Boolean = active.exists(_ eq run)

  private def discardWorker(): Unit = {
    val discarded = worker
    worker = None
    discarded.foreach { slot =>
      // Cleanup must not leave the caller's promise pending if the transport is already broken.
      try slot.worker.close()
      catch { case NonFatal(_) => () }
    }
  }

  private def finish(run: Run, result: Try[T.Execution], discard: Boolean): Unit =
    if current(run) then {
      run.timer.foreach(clearTimeout)
      run.timer = None
      active = None
      run.promise.tryComplete(result)
      if discard then discardWorker()
    }

  private def deadline(run: Run, milliseconds: Int, phase: String): Unit = {
    run.timer.foreach(clearTimeout)
    run.timer = Some(setTimeout(milliseconds.toDouble) {
      finish(run, Failure(new TimeoutException(s"Java worker $phase timed out.")), discard = true)
    })
  }

  private def acquireWorker(): WorkerSlot = worker.getOrElse {
    val transport = workerFactory()
    val ready = try transport.ready() catch { case NonFatal(error) => Future.failed(error) }
    val slot = WorkerSlot(transport, ready)
    worker = Some(slot)
    slot
  }

  private def execute(program: P.Program, method: Option[R.MethodId], arguments: Vector[E.Value],
      limits: T.Limits): Future[T.Execution] = {
    if closed then Future.failed(new IllegalStateException("Java runner is closed."))
    else if active.nonEmpty then Future.failed(new IllegalStateException("Java execution is already running."))
    else if !limits.isValid then
      Future.successful(T.Execution(T.Status.Failed(T.Failure.InvalidLimits), Vector.empty, 0))
    else if !JavaTurtleCommandRunner.validInvocation(program, method, arguments) then
      Future.successful(T.Execution(T.Status.Failed(T.Failure.InvalidInvocation), Vector.empty, 0))
    else {
      val prepared = Try(JavaTurtleCommandRunner.code(program, method, arguments, limits) -> acquireWorker())
      prepared match {
        case Failure(error) => Future.failed(error)
        case Success((code, slot)) =>
          val run = new Run(slot)
          active = Some(run)
          deadline(run, startupTimeoutMs, "startup")
          slot.ready.onComplete {
            case Success(_) if current(run) =>
              deadline(run, executionTimeoutMs, "execution")
              val response = try slot.worker.run(RunPythonRequest(code, resetGlobals = false))
                catch { case NonFatal(error) => Future.failed(error) }
              response.onComplete {
                case Success(report) =>
                  if current(run) then {
                    val decoded = Try(JavaTurtleCommandRunner.decode(report, limits))
                    finish(run, decoded, discard = decoded.isFailure)
                  }
                case Failure(error) => finish(run, Failure(error), discard = true)
              }
            case Failure(error) => finish(run, Failure(error), discard = true)
            case _ => ()
          }
          run.promise.future
      }
    }
  }
}

object JavaTurtleCommandRunner {
  private def workerFactory(): PythonExecutionClient =
    PythonExecutionClient.worker(it.evadid.homepage.control.startup.ClientRuntimeConfig.workerUrl)

  private def validInvocation(program: P.Program, method: Option[R.MethodId], arguments: Vector[E.Value]): Boolean =
    method.fold(arguments.isEmpty) { id =>
      program.root.methods.find(_.binding.id == id).exists { method =>
        (method ne program.root.entryPoint) && method.binding.parameters.size == arguments.size &&
          method.binding.parameters.zip(arguments).forall { (parameter, value) =>
            (parameter.variable.valueType, value) match {
              case (R.ValueType.IntValue, _: E.Value.IntValue) => true
              case (R.ValueType.DoubleValue, _: E.Value.IntValue | _: E.Value.DoubleValue) => true
              case (R.ValueType.BooleanValue, _: E.Value.BooleanValue) => true
              case _ => false
            }
          }
      }
    }

  private def code(program: P.Program, method: Option[R.MethodId], arguments: Vector[E.Value], limits: T.Limits): String =
    JavaTurtlePythonExport.invocationSource(JavaTurtlePythonExport.render(program), method, arguments, limits)

  private[execution] def decode(report: RunPythonResult, limits: T.Limits): T.Execution = {
    if report.callbackOps.nonEmpty || report.stderr.nonEmpty then
      throw new IllegalArgumentException("Invalid Java execution result: unexpected Python output")
    JavaTurtleExecutionReport.decode(report.value, limits)
  }
}
