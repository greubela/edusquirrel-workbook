package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.div
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleExecutionProvider
import it.evadid.homepage.webElements.editor.code.JavaEditorSession
import it.evadid.vm.parsing.java.turtle.JavaTurtleResolution
import it.evadid.vm.simulation.java.JavaTurtleRuntime
import it.evadid.workbook.elements.interactionElements.programming.state.*

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Success, Try}

final class JavaTurtleEditorExtension(
    state: Var[ProgrammingState],
    runnerFactory: () => JavaEditorSession.Runner = JavaEditorSession.defaultRunner
) extends TurtleExecutionProvider {
  private val session = Var(Option.empty[JavaEditorSession])
  private class Attempt {
    val owner = new ManualOwner
    val invalidated = Var(false)
    def invalidate(): Unit = { invalidated.set(true); owner.killSubscriptions() }
  }
  private val running = Var(Option.empty[Attempt])

  override def turtleCommands(source: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] = source match {
    case java: ProgrammingStateJavaString => Some(() => run(java))
    case _ => None
  }

  def run(source: ProgrammingStateJavaString): Future[List[TurtleCommand[Double]]] = {
    if running.now().nonEmpty then return Future.failed(IllegalStateException("Java execution is already running."))
    val attempt = new Attempt
    running.set(Some(attempt))
    val original = state.now()
    state.signal.changes.foreach { next =>
      if next != original then close()
    }(using attempt.owner)
    Future(Try(source.isClassProgram).getOrElse(true)).flatMap { isClass =>
      if attempt.invalidated.now() then Future.failed(CancellationException("Java execution cancelled."))
      else if !isClass then Future(source.toLegacyTurtleCommands)
      else {
        val current = session.now().getOrElse {
          val created = new JavaEditorSession(source, runnerFactory)
          session.set(Some(created))
          created
        }
        current.updateSource(source)
        current.run().map(commandsFrom)
      }
    }.map { result =>
      if attempt.invalidated.now() then throw CancellationException("Java execution cancelled.")
      result
    }.andThen { case _ =>
      attempt.owner.killSubscriptions()
      if running.now().exists(_ eq attempt) then running.set(None)
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
    val pending = running.now()
    running.set(None)
    pending.foreach(_.invalidate())
    session.now().foreach(_.stop())
  }

  override def cancel(): Unit = stop()

  override def close(): Unit = {
    val pending = running.now()
    val discarded = session.now()
    running.set(None)
    session.set(None)
    pending.foreach(_.invalidate())
    discarded.foreach(_.release())
  }

}
