package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import com.raquo.airstream.state.Var
import java.util.concurrent.CancellationException
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleExecutionProvider
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

/** Runtime dispatch belongs to the turtle feature, independently of Eva's tab navigation. */
final class TurtleProgramExecution(providers: List[TurtleExecutionProvider] = Nil) {
  private val revision = Var(0L)
  private lazy val python = new PyodideTurtleCommandRunner()
  private lazy val snap = new SnapTurtleCommandExecution(python)
  def commandsFor(source: ProgrammingState): Future[List[TurtleCommand[Double]]] = {
    val requested = revision.now()
    Future.unit.flatMap { _ =>
      if requested != revision.now() then Future.failed(CancellationException("Execution cancelled."))
      else dispatch(source)
    }
  }
  private def dispatch(source: ProgrammingState): Future[List[TurtleCommand[Double]]] =
    providers.iterator.flatMap(_.turtleCommands(source)).nextOption() match {
      case Some(execute) => Try(execute()).fold(Future.failed, identity)
      case None => source match {
        case xml: ProgrammingStateSnapXml => snap.commandsFor(xml)
        case model: ProgrammingStateSnapScalaModel => snap.commandsFor(model.toSnapXml)
        case ProgrammingStatePythonString(code) => python.execute(code)
        case java: ProgrammingStateJavaString => Future(java.toLegacyTurtleCommands)
        case _ => Future(source.toBeExpressionState.deriveTurtleCommands)
      }
    }
  def cancel(): Unit = { revision.update(_ + 1); python.close(); providers.foreach(_.cancel()) }
  def close(): Unit = { revision.update(_ + 1); python.close(); providers.foreach(_.close()) }
}
