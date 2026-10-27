package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import com.raquo.laminar.api.L.Element
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand

import scala.concurrent.Future

trait TurtleExecutionProvider {
  def turtleCommands(source: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] = None
  def cancel(): Unit = ()
  def close(): Unit = ()
}
