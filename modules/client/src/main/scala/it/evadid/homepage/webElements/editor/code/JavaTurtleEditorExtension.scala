package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Element
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorExtension
import it.evadid.vm.parsing.java.turtle.JavaTurtleResolution
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import it.evadid.workbook.elements.interactionElements.programming.{JavaTurtleTask, ProgrammingState, ProgrammingStateJavaString, TurtleGraphic}

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Success, Try}

final class JavaTurtleEditorExtension(
    state: Var[ProgrammingState],
    target: Option[TurtleGraphic] = None,
    task: Option[JavaTurtleTask] = None,
    runnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner
) extends EvaEditorExtension {
  private var session = Option.empty[JavaEditorSession]
  private var panel = Option.empty[JavaTurtleExecutionPanel]
  private class Attempt {
    val owner = new ManualOwner
    var invalidated = false
    def invalidate(): Unit = { invalidated = true; owner.killSubscriptions() }
  }
  private var running = Option.empty[Attempt]

  override def turtleCommands(source: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] = source match {
    case java: ProgrammingStateJavaString => Some(() => run(java))
    case _ => None
  }

  def run(source: ProgrammingStateJavaString): Future[List[TurtleCommand[Double]]] = {
    if running.nonEmpty then return Future.failed(IllegalStateException("Java execution is already running."))
    Try(source.isClassProgram) match {
      case Success(false) => return Try(source.toLegacyTurtleCommands).fold(Future.failed, Future.successful)
      case _ => ()
    }
    execute(source)(_.run().map(commandsFrom))
  }

  def checkTask(source: ProgrammingStateJavaString, task: JavaTurtleTask): Future[Vector[List[TurtleCommand[Double]]]] =
    execute(source)(_.checkTask(task).map(_.map(commandsFrom)))

  def checkTaskDetailed(source: ProgrammingStateJavaString, task: JavaTurtleTask,
      limits: JavaTurtleRuntime.Limits = JavaTurtleRuntime.Limits()): Future[JavaEditorSession.TaskResult] =
    execute(source)(_.checkTaskDetailed(task, limits))

  private def execute[A](source: ProgrammingStateJavaString)(operation: JavaEditorSession => Future[A]): Future[A] = {
    if running.nonEmpty then return Future.failed(IllegalStateException("Java execution is already running."))
    val current = session.getOrElse {
      val created = new JavaEditorSession(source, runnerFactory)
      session = Some(created)
      created
    }
    current.updateSource(source)
    val attempt = new Attempt
    running = Some(attempt)
    val original = state.now()
    state.signal.changes.foreach { next =>
      if next != original then close()
    }(using attempt.owner)
    Try(operation(current)).fold(Future.failed, identity).map { result =>
      if attempt.invalidated then throw CancellationException("Java execution cancelled.")
      result
    }.andThen { case _ =>
      attempt.owner.killSubscriptions()
      if running.exists(_ eq attempt) then running = None
    }
  }

  private def commandsFrom(execution: JavaTurtleRuntime.Execution): List[TurtleCommand[Double]] = {
    JavaEditorSession.requireCompleted(execution)
    execution.commands.toList.map { command =>
      val name = command.command match {
        case JavaTurtleResolution.TurtleCommand.Forward => "forward"
        case JavaTurtleResolution.TurtleCommand.TurnRight => "right"
      }
      TurtleCommand[Double](name, List(command.value))
    }
  }

  def stop(): Unit = {
    val pending = running
    running = None
    pending.foreach(_.invalidate())
    session.foreach(_.stop())
  }

  override def close(): Unit = {
    val pending = running
    val discarded = session
    running = None
    session = None
    pending.foreach(_.invalidate())
    discarded.foreach(_.release())
    panel.foreach(_.reset())
  }

  override def reference(language: ProgrammingLanguage, currentSource: () => ProgrammingState): Option[() => Element] =
    Option.when(language == AppLanguage.Java)(() => {
      def source(): ProgrammingStateJavaString = currentSource() match {
        case java: ProgrammingStateJavaString => java
        case _ => throw IllegalStateException("Open the Java editor to check this task.")
      }
      val current = panel.getOrElse {
        val created = new JavaTurtleExecutionPanel(state, () => run(source()), () => stop(), target,
          task.map(example => example -> (() => checkTask(source(), example))))
        panel = Some(created)
        created
      }
      current.getDomElement()
    })
}
