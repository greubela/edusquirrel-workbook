package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.todomove.`export`.snap.BeProgramSnapRenderer
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExercise
import it.evadid.workbook.interaction.sync.UpdateImportance
import org.scalajs.dom.HTMLCanvasElement

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(workbookElement: ProgrammingExercise): AtomarLineRendering = {
    val boundVar: Var[BeProgram] = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val editor = EvaCodeEditor(State(workbookElement.interactionVariable.currentValue))

    def buttonPressed(): Unit = {
      fullInfo.displayControl.setFullscreen(editor)
    }

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    val canvasSignal = boundVar.signal.map(prog => {
      val canvas: ReactiveHtmlElement[HTMLCanvasElement] = canvasTag(
        widthAttr := 500,
        heightAttr := 750,

        display := "block",
        // Perform your mounting/rendering side-effect safely
        onMountCallback { nodeCtx =>
          BeProgramSnapRenderer.render(prog, nodeCtx.thisNode.ref)
        }
      )
      div(
        styleAttr := "position: relative; width: 500px; height: 750px; margin: 0 auto;",
        position := "relative",
        margin := "0 auto",
        canvas
      )
    })
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), canvasSignal)

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, canvasCard))
  }


}
