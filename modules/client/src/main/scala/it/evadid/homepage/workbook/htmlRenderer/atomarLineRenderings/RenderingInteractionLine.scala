package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.{InteractionFeedbackPanel, InteractionInfoPanel}
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.homepage.control.info.interaction.GradingReporter

case class RenderingInteractionLine[F <: InteractionFormat](
                                             fullInfo: FullInfo,
                                             interactionElement: WorkbookInteractionElement[F],
                                             content: DomElementCollection,
                                             additionalCssStr: String
                                           ) extends AtomarLineRendering {

  lazy val gradingReporter: GradingReporter = fullInfo.reporters.reportersFor(interactionElement).grading
  lazy val interactionInfoPanel: InteractionInfoPanel[F] = InteractionInfoPanel(fullInfo, interactionElement, gradingReporter)

  protected val lineCssStr: String = getCssString(interactionElement)

  override lazy val elementsWithoutContainer: DomElementCollection = createElementsWithoutContainer

  /** Every interaction with grading or hints shows the shared feedback panel below its content. */
  private lazy val feedbackPanel: Option[Element] =
    Option.when(gradingReporter.isGradingSupported || gradingReporter.isScaffoldingSupported)(
      InteractionFeedbackPanel(fullInfo, interactionElement, gradingReporter).getDomElement())

  override lazy val render: Element = {
    div(
      cls := elementCssString + " " + interactionCssString,
      dataAttr("element-id") := interactionElement.elementId,
      interactionInfoPanel.getDomElement(),
      createElementsWithoutContainer
    )
  }

  private def createElementsWithoutContainer: Element = div(
    cls := interactionContentCssString + " " + additionalCssStr, //
    children <-- content.allElementsSignal.map(_ ++ feedbackPanel.toList)
  )


}

