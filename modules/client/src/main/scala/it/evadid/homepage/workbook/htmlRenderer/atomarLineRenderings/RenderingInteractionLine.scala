package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class RenderingInteractionLine[T, GA, SA](
                                             interactionElement: WorkbookInteractionElementFull[T, GA, SA],
                                             content: DomElementCollection,
                                             additionalCssStr: String,
                                             showGradingLine: Signal[Boolean] = Var(false).signal
                                           ) extends AtomarLineRendering {


  private val gradingReporter: InteractionVariableGradingReporter[T, GA, SA] = InteractionVariableGradingReporter(interactionElement)
  lazy val interactionInfoPanel: InteractionInfoPanel = InteractionInfoPanel(interactionElement, gradingReporter)

  protected val lineCssStr: String = getCssString(interactionElement)

  override lazy val elementsWithoutContainer: DomElementCollection = createElementsWithoutContainer

  private def gradingElements(): Signal[Seq[Element]] = {
    val res: Seq[Element] = List[Element](div(cls := "hai"))
    Var(res).signal
  }

  override lazy val render: Element = {
    div(
      cls := elementCssString + " " + interactionCssString,
      interactionInfoPanel.getDomElement(),
      createElementsWithoutContainer
    )
  }

  private def createElementsWithoutContainer: Element = div(
    cls := interactionContentCssString + " " + additionalCssStr, //
    children <-- content.allElementsSignal.combineWith(gradingElements()).combineWith(showGradingLine).map((regularContent, gradingContent, showGradingLine) => {
      if (showGradingLine) regularContent ++ gradingContent
      else regularContent
    })
  )


}

