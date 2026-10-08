package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.JavaTurtleCommandRunner
import it.evadid.vm.parsing.java.turtle.{JavaTurtleSource, JavaTurtleVmPrograms, JavaTurtleResolution}
import it.evadid.vm.simulation.java.{JavaTurtleRuntime, JavaTurtleEvaluation}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingStateJavaString, JavaTurtleTask}

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
  private class Attempt[A] {
    val promise: Promise[A] = Promise()
  }
  private var active = Option.empty[Attempt[?]]
  private var changingState = false

  def source: ProgrammingStateJavaString = currentSource

  def updateSource(next: ProgrammingStateJavaString): Unit =
    if next != currentSource then {
      currentSource = next
      invalidate(State.Idle)
    }

  def run(limits: JavaTurtleRuntime.Limits = JavaTurtleRuntime.Limits()): Future[JavaTurtleRuntime.Execution] =
    execute((program, owned, _) => owned.run(program, limits), State.Finished.apply)

  def checkTask(task: JavaTurtleTask, limits: JavaTurtleRuntime.Limits = JavaTurtleRuntime.Limits()): Future[Vector[JavaTurtleRuntime.Execution]] =
    execute((program, owned, current) => {
      val method = program.root.methods.find(_.binding.originalName == task.methodName)
        .filter(_ ne program.root.entryPoint).getOrElse(
          throw IllegalArgumentException(s"Define a static method named ${task.methodName} for this task."))
      if task.cases.isEmpty || task.cases.size > 16 || method.binding.parameters.size > 16 ||
        method.binding.parameters.exists(_.variable.valueType != JavaTurtleResolution.ValueType.IntValue) ||
        task.cases.exists(_.arguments.size != method.binding.parameters.size) then
        throw IllegalArgumentException(s"Check the integer parameters of ${task.methodName}.")
      task.cases.foldLeft(Future.successful(Vector.empty[JavaTurtleRuntime.Execution])) { (previous, example) =>
        previous.flatMap { executions =>
          if !current() then Future.failed(CancellationException("Java execution cancelled."))
          else owned.invoke(program, method.binding.id, example.arguments.map(JavaTurtleEvaluation.Value.IntValue.apply).toVector, limits)
            .map { execution => requireCompleted(execution); executions :+ execution }
        }
      }
    }, State.Checked.apply)

  private def execute[A](operation: (JavaTurtleVmPrograms.Program, Runner, () => Boolean) => Future[A], finished: A => State): Future[A] =
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
        val attempt = new Attempt[A]
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
            case Failure(error) => finish(attempt, Failure(error), finished)
            case Success(owned) if isCurrent(attempt) =>
              try operation(program, owned, () => isCurrent(attempt)).onComplete(result => finish(attempt, result, finished))
              catch { case NonFatal(error) => finish(attempt, Failure(error), finished) }
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

  private def isCurrent(attempt: Attempt[?]): Boolean = active.exists(_ eq attempt)

  private def finish[A](attempt: Attempt[A], result: Try[A], finished: A => State): Unit =
    if isCurrent(attempt) then {
      active = None
      attempt.promise.tryComplete(result)
      status.set(result match {
        case Success(execution) => finished(execution)
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
    case Checked(executions: Vector[JavaTurtleRuntime.Execution])
    case Failed(error: Throwable)
  }

  final case class ValidationFailure(diagnostic: JavaTurtleSource.Diagnostic)
      extends IllegalArgumentException(diagnostic.message)

  trait Runner {
    def run(program: JavaTurtleVmPrograms.Program, limits: JavaTurtleRuntime.Limits): Future[JavaTurtleRuntime.Execution]
    def invoke(program: JavaTurtleVmPrograms.Program, method: JavaTurtleResolution.MethodId,
        arguments: Vector[JavaTurtleEvaluation.Value], limits: JavaTurtleRuntime.Limits): Future[JavaTurtleRuntime.Execution] =
      Future.failed(UnsupportedOperationException("Java method execution is unavailable."))
    def cancel(): Boolean
    def close(): Unit
  }

  def defaultRunner(): Runner = new Runner {
    private val underlying = new JavaTurtleCommandRunner()
    override def run(program: JavaTurtleVmPrograms.Program, limits: JavaTurtleRuntime.Limits): Future[JavaTurtleRuntime.Execution] =
      underlying.run(program, limits)
    override def invoke(program: JavaTurtleVmPrograms.Program, method: JavaTurtleResolution.MethodId,
        arguments: Vector[JavaTurtleEvaluation.Value], limits: JavaTurtleRuntime.Limits): Future[JavaTurtleRuntime.Execution] =
      underlying.invoke(program, method, arguments, limits)
    override def cancel(): Boolean = underlying.cancel()
    override def close(): Unit = underlying.close()
  }

  def compile(source: String): Either[JavaTurtleSource.Diagnostic, JavaTurtleVmPrograms.Program] =
    ProgrammingStateJavaString(source).toJavaVmProgram

  private[code] def requireCompleted(execution: JavaTurtleRuntime.Execution): Unit = execution.status match {
    case JavaTurtleRuntime.Status.Completed => ()
    case JavaTurtleRuntime.Status.Cancelled => throw CancellationException("Java execution cancelled.")
    case JavaTurtleRuntime.Status.LimitExceeded =>
      throw IllegalStateException("Your program reached its execution limit. Check its loops or recursion.")
    case JavaTurtleRuntime.Status.Failed(JavaTurtleRuntime.Failure.Evaluation(JavaTurtleEvaluation.Failure.DivisionByZero)) =>
      throw IllegalStateException("Your program tried to divide by zero.")
    case JavaTurtleRuntime.Status.Failed(_) => throw IllegalStateException("Your Java program could not finish.")
  }
}
