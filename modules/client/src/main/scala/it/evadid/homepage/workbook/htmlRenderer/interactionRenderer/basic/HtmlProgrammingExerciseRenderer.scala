package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(workbookElement: ProgrammingExercise): AtomarLineRendering = {

    val editor = EvaCodeEditor(State(workbookElement.interactionVariable.currentValue))

    def buttonPressed(): Unit = {
      fullInfo.technical.makeFullscreen(editor)
    }

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel(LanguageMapContentId("basic/OpenEditor"), event => buttonPressed())
    AtomarLineRendering.basicLine(workbookElement, button.getDomElement())
  }


}
