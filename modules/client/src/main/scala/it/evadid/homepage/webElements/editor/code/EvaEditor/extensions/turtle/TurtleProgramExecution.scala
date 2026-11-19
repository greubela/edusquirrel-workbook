package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}

import com.raquo.airstream.state.Var
import java.util.concurrent.CancellationException
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleExecutionProvider
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

/** Runtime dispatch belongs to the turtle feature, independently of Eva's tab navigation. */
final class TurtleProgramExecution(providers: Seq[TurtleExecutionProvider] = Seq.empty, sharedInterpreter: Boolean = false) {
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
    if sharedInterpreter then Future(source.toBeExpression.turtleCommandExecutionInfo.completedCommands)
    else providers.iterator.flatMap(_.turtleCommands(source)).nextOption() match {
      case Some(execute) => Try(execute()).fold(Future.failed, identity)
      case None => source match {
        case xml @ ProgrammingStateRaw(SnapLanguage, _) => snap.commandsFor(xml)
        case model: ProgrammingStateSnapScalaModel => snap.commandsFor(model.toSnapXml)
        case ProgrammingStateRaw(Python, code) => python.execute(code)
        case value: ProgrammingStatePythonStructured => python.execute(value.toString)
        case value: ProgrammingStateJavaStructured => Future(value.toJava.toLegacyTurtleCommands)
        case java @ ProgrammingStateRaw(Java, _) => Future(java.toLegacyTurtleCommands)
        case _ => Future(source.toBeExpression.turtleCommandExecutionInfo.completedCommands)
      }
    }
  def cancel(): Unit = { revision.update(_ + 1); python.close(); providers.foreach(_.cancel()) }
  def close(): Unit = { revision.update(_ + 1); python.close(); providers.foreach(_.close()) }
}
