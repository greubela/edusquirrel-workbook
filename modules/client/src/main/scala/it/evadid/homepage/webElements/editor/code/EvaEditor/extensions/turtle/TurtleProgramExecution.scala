package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorExtension
import it.evadid.homepage.webElements.editor.code.SnapEditor.execution.{PyodideTurtleCommandRunner, SnapTurtleCommandExecution}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import scala.concurrent.Future
import scala.util.Try

/** Runtime dispatch belongs to the turtle feature, independently of Eva's tab navigation. */
final class TurtleProgramExecution(extensions: List[EvaEditorExtension] = Nil)
    extends it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorModule {
  override def onFullscreenClose(): Unit = close()
  private lazy val python = new PyodideTurtleCommandRunner()
  private lazy val snap = new SnapTurtleCommandExecution(python)
  def commandsFor(source: ProgrammingState): Future[List[TurtleCommand[Double]]] =
    extensions.iterator.flatMap(_.turtleCommands(source)).nextOption() match {
      case Some(execute) => Try(execute()).fold(Future.failed, identity)
      case None => source match {
        case xml: ProgrammingStateSnapXml => snap.commandsFor(xml)
        case model: ProgrammingStateSnapScalaModel => snap.commandsFor(model.toSnapXml)
        case ProgrammingStatePythonString(code) => python.execute(code)
        case java: ProgrammingStateJavaString => Try(java.toLegacyTurtleCommands).fold(Future.failed, Future.successful)
        case _ => Try(source.toBeExpressionState.deriveTurtleCommands).fold(Future.failed, Future.successful)
      }
    }
  def cancel(): Unit = { python.close(); extensions.foreach(_.cancel()) }
  def close(): Unit = { python.close(); extensions.foreach(_.close()) }
}
