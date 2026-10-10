package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.laminar.api.L.Element
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.Future

trait EvaEditorExtension {
  def turtleCommands(source: ProgrammingState): Option[() => Future[List[TurtleCommand[Double]]]] = None
  def reference(language: ProgrammingLanguage, currentSource: () => ProgrammingState): Option[() => Element] = None
  def close(): Unit = ()
}
