package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.JavaTurtleCommandRunner
import it.evadid.vm.parsing.java.turtle.{JavaTurtleSource, JavaTurtleVmPrograms}
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingStateJavaString

import java.util.concurrent.CancellationException
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

final class JavaEditorSession(
    initialSource: ProgrammingStateJavaString,
    runnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner
) {
  import JavaEditorSession.*

  val status: Var[State] = Var(State.Idle)
  private var currentSource = initialSource
  private var runner = Option.empty[Runner]
  private class Attempt {
    val promise: Promise[JavaTurtleRuntime.Execution] = Promise()
  }
  private var active = Option.empty[Attempt]
  private var changingState = false

  def source: ProgrammingStateJavaString = currentSource

  def updateSource(next: ProgrammingStateJavaString): Unit =
    if next != currentSource then {
      currentSource = next
      invalidate(State.Idle)
    }

  def run(limits: JavaTurtleRuntime.Limits = JavaTurtleRuntime.Limits()): Future[JavaTurtleRuntime.Execution] =
    if changingState then Future.failed(IllegalStateException("Java execution is changing state."))
    else if active.nonEmpty then Future.failed(IllegalStateException("Java execution is already running."))
    else Try(compile(currentSource.code)) match {
      case Failure(error) =>
        status.set(State.Failed(error))
        Future.failed(error)
      case Success(Left(diagnostic)) =>
        status.set(State.Invalid(diagnostic))
        Future.failed(ValidationFailure(diagnostic))
      case Success(Right(program)) =>
        val attempt = new Attempt
        active = Some(attempt)
        status.set(State.Running)
        if isCurrent(attempt) then {
          val acquired = Try(runner.getOrElse {
            val created = Option(runnerFactory()).getOrElse(throw IllegalStateException("Java runner is unavailable."))
            if isCurrent(attempt) then runner = Some(created)
            else safely(created.close())
            created
          })
          acquired match {
            case Failure(error) => finish(attempt, Failure(error))
            case Success(owned) if isCurrent(attempt) =>
              try owned.run(program, limits).onComplete(result => finish(attempt, result))
              catch { case NonFatal(error) => finish(attempt, Failure(error)) }
            case _ => ()
          }
        }
        attempt.promise.future
    }

  def stop(): Unit = invalidate(State.Stopped)

  def release(): Unit = duringStateChange {
    if active.nonEmpty then invalidate(State.Stopped)
    discardRunner()
  }

  private def isCurrent(attempt: Attempt): Boolean = active.exists(_ eq attempt)

  private def finish(attempt: Attempt, result: Try[JavaTurtleRuntime.Execution]): Unit =
    if isCurrent(attempt) then {
      active = None
      attempt.promise.tryComplete(result)
      status.set(result match {
        case Success(execution) => State.Finished(execution)
        case Failure(_: CancellationException) => State.Stopped
        case Failure(error) => State.Failed(error)
      })
    }

  private def invalidate(next: State): Unit = duringStateChange {
    val cancelled = active
    active = None
    cancelled.foreach(_.promise.tryFailure(CancellationException("Java execution cancelled.")))
    if cancelled.nonEmpty then runner.foreach { owned =>
      try owned.cancel()
      catch { case NonFatal(_) => discardRunner() }
    }
    if status.now() != next then status.set(next)
  }

  private def duringStateChange[A](operation: => A): A = {
    val wasChanging = changingState
    changingState = true
    try operation
    finally changingState = wasChanging
  }

  private def discardRunner(): Unit = {
    val discarded = runner
    runner = None
    discarded.foreach(owned => safely(owned.close()))
  }

  private def safely(cleanup: => Unit): Unit =
    try cleanup catch { case NonFatal(_) => () }
}

object JavaEditorSession {
  enum State {
    case Idle, Running, Stopped
    case Invalid(diagnostic: JavaTurtleSource.Diagnostic)
    case Finished(execution: JavaTurtleRuntime.Execution)
    case Failed(error: Throwable)
  }

  final case class ValidationFailure(diagnostic: JavaTurtleSource.Diagnostic)
      extends IllegalArgumentException(diagnostic.message)

  trait Runner {
    def run(program: JavaTurtleVmPrograms.Program, limits: JavaTurtleRuntime.Limits): Future[JavaTurtleRuntime.Execution]
    def cancel(): Boolean
    def close(): Unit
  }

  def defaultRunner(): Runner = new Runner {
    private val underlying = new JavaTurtleCommandRunner()
    override def run(program: JavaTurtleVmPrograms.Program, limits: JavaTurtleRuntime.Limits): Future[JavaTurtleRuntime.Execution] =
      underlying.run(program, limits)
    override def cancel(): Boolean = underlying.cancel()
    override def close(): Unit = underlying.close()
  }

  def compile(source: String): Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program] =
    ProgrammingStateJavaString(source).toJavaVmProgram
}
