package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, VmToSvg}
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
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


    // preview based on the Editor (probably should be removed as it makes the editor way more complex -> instead just a second editor with a non-interactible config)
     val canvasCard = ElementCard(
      LanguageMapContentId("basic/canvas"),
      editor.previewCanvas
    )

    // static preview based on the custom display engine (not working yet, for test purposes)
    val shapeLogger = Logger.withNameAndPrefixes(Some("HtmlProgrammingExerciseRenderer::ShapeRenderingLogger"), PrintToStdLogger.printEverything)
    val staticRendering = ElementCard(
      LanguageMapContentId("basic/staticPreviewProgram"),
      SvgLaminarRenderer.render(shapeLogger, VmToSvg.renderBeExpression(shapeLogger, boundVar.now().fullProgram))
    )

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, staticRendering, canvasCard))
  }


}
