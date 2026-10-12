package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.Element
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future

trait EvaEditorExtension {
  def turtleCommands(source: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] = None
  def panel(context: EvaEditorExtension.Context): Option[Element] = None
  def cancel(): Unit = ()
  def close(): Unit = ()
}

object EvaEditorExtension {
  final case class Context(
      captureSource: () => ProgrammingState,
      execute: ProgrammingState => Future[List[TurtleCommand[Double]]],
      cancel: () => Unit
  )
}
