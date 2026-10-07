package it.evadid.homepage.webElements.editor.code.TurtleEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.{EvaEditor, EvaEditorConfig}
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingState

case class EvaTurtleEditor(boundVar: Var[ProgrammingState], evaConfig: EvaEditorConfig, card: ElementCard) extends HtmlAppElement with FullscreenLifecycle {

  val evaEditor = EvaEditor(boundVar, evaConfig)

  val domElement: Element = div(
    evaEditor.getDomElement(),
    card.getDomElement()
  )

  override def getDomElement(): Element = domElement

  override def onFullscreenClose(): Unit = evaEditor.onFullscreenClose()

  override def onFullscreenOpen(): Unit = evaEditor.onFullscreenOpen()
}
