package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.{HtmlRenderFactory, HtmlWorkbookElement}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, RenderingWithLabel}
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement

case class HtmlLabeledWorkbookElementRenderer[T <: WorkbookElement](entry: LabeledWorkbookElement[T]) extends LineBasedRenderingFactory[LabeledWorkbookElement[T]] {

  /*override protected def createDomElement(workbookElement: LabeledInstructionElement): Element = div(
    cls := "workbook-element exercise-instruction ${workbookElement.labelType.associatedCssString}",
    div(
      cls := s"${workbookElement.labelType.associatedCssString}__title",
      text <-- super.contentIdStringSignal(workbookElement.titleLable)
    ),
    div(
      cls := s"${workbookElement.labelType.associatedCssString}__body",
      text <-- super.contentIdStringSignal(workbookElement.bodyContent)
    )
  )*/

  override protected def createRendering(workbookElement: LabeledWorkbookElement[T]): AtomarLineRendering = {
    val base: HtmlWorkbookElement[WorkbookElement, AtomarLineRendering] = HtmlRenderFactory.renderWorkbookElement(workbookElement.baseElement)
    RenderingWithLabel(workbookElement, base.rendering, workbookElement.label)

  }

}

/*
object HtmlInstructionLabeledPairRenderer extends HtmlRenderFactory[LabeledInstructionElement] {



}*/
