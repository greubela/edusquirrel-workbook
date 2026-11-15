package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.editorSidePanel

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.ElementCard
import it.evadid.workbook.abstractions.grading.GradedState
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookInteractionElementFull}
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case class InteractionInfoSidebar[T, GA, SA]
(
  underlyingInteraction: WorkbookInteractionElementFull[T, GA, SA],
  associatedInstruction: Option[WorkbookDisplayElement],
  gradingReporter: InteractionVariableGradingReporter[T, GA, SA],
) extends HtmlAppElement {


  def createInstructionElement: Option[Element] = associatedInstruction.map(displayElement => {
    val rendered = HtmlRenderFactory.renderWorkbookElement(displayElement).rendering.elementsWithoutContainer
    val card = ElementCard(LanguageMapContentId("basic/SidepanelInstructionTitle"), rendered)
    card.getDomElement()
  })

  def createGradingElement: Option[Element] = if (!gradingReporter.isGradingSupported) None else {
    val card = ElementCard(LanguageMapContentId("basic/SidepanelGradingTitle"),
      gradingReporter.currentGradingSignal.deriveValue(renderGrading).toEventStream()
    )
    Some(card.getDomElement())
  }

  def createScaffoldingElement: Option[Element] = if (!gradingReporter.isScaffoldingSupported) None else {
    val card = ElementCard(LanguageMapContentId("basic/SidepanelScaffoldingTitle"),
      gradingReporter.currentScaffoldingSignal.deriveValue(renderScaffolding).toEventStream()
    )
    Some(card.getDomElement())
  }

  def renderGrading(gradedState: GradedState[T, GA]): Element = {
    div(s"hai grading: ${gradedState.graderResultMap.size} graders (${gradedState.totalEvaluation()})")
  }

  def renderScaffolding(scaffoldingResultOp: Option[ScaffoldingResult[T, SA]]): Element = scaffoldingResultOp.match {
    case Some(scaffoldingResult) => div(s"hai scaff: ${scaffoldingResult.scaffoldingStatus.isError}")
    case None => div("no scaff")
  }

  override def getDomElement(): Element = {
    val children: Seq[Element] = (createInstructionElement ++ createGradingElement ++ createScaffoldingElement).toList
    div(
      cls := "side-panel-interaction",
      children
    )
  }


}