package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(workbookElement: ProgrammingExercise): AtomarLineRendering = {
    val boundVar: Var[BeProgram] = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    //val editor = EvaCodeEditor(State(workbookElement.interactionVariable.currentValue))
    val editor: SnapCodeEditor = SnapCodeEditor(boundVar)

    def buttonPressed(): Unit = {
      // setFullscreen only publishes the editor here. HtmlWorkbookDomElement
      // then inserts editor.getDomElement() into the already-mounted dialog;
      // SnapCodeEditor creates WorldMorph from the canvas' subsequent mount
      // callback, never during this button event or while it is detached.
      fullInfo.displayControl.setFullscreen(editor)
    }

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    // Keep the canvas mounted while the program changes. The Scala.js renderer
    // owns sizing, high-DPI scaling and redraw scheduling; replacing the canvas
    // on every update used to create a new Morphic world each time and left its
    // internally-positioned canvas as a tiny strip in the upper-left corner.
    val canvasCard = ElementCard(
      LanguageMapContentId("basic/canvas"),
      editor.previewCanvas
    )

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, canvasCard))
  }


}
