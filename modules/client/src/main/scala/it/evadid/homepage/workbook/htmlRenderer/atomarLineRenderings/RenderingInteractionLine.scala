package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.linePanelBottom.GradingInfoLine
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class RenderingInteractionLine[F <: InteractionFormat](
                                             interactionElement: WorkbookInteractionElement[F],
                                             content: DomElementCollection,
                                             additionalCssStr: String,
                                             showGradingLine: Signal[Boolean] = Var(false).signal
                                           ) extends AtomarLineRendering {

  lazy val gradingReporter: GradingReporter = fullInfo.signals.reportersFor(interactionElement).grading
  lazy val interactionInfoPanel: InteractionInfoPanel[F] = InteractionInfoPanel(interactionElement, gradingReporter)

  protected val lineCssStr: String = getCssString(interactionElement)

  override lazy val elementsWithoutContainer: DomElementCollection = createElementsWithoutContainer

  private val gradingLine = GradingInfoLine(interactionElement, gradingReporter)

  override lazy val render: Element = {
    div(
      cls := elementCssString + " " + interactionCssString,
      interactionInfoPanel.getDomElement(),
      createElementsWithoutContainer
    )
  }

  private def createElementsWithoutContainer: Element = div(
    cls := interactionContentCssString + " " + additionalCssStr, //
    children <-- content.allElementsSignal.combineWith(showGradingLine).map((regularContent, showGradingLine) => {
      if (showGradingLine) regularContent ++ List(gradingLine.getDomElement())
      else regularContent
    })
  )


}

