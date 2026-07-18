package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.WorkbookLabel


case class RenderingWithLabel(workbookElement: WorkbookElement, containedElement: AtomarLineRendering, label: WorkbookLabel) extends AtomarLineRendering {

  private lazy val cssLabel: String = label.labelType.associatedCssString

  lazy val elementsWithoutContainer: DomElementCollection = {
    List(
      h3(
        cls := s"${cssLabel}__title labeled_container_label",
        text <-- laminarHelper.plaintextStringSignal(label.contentId)
      ),
      div(
        cls := s"${cssLabel}__body labeled_container_content",
        children <-- containedElement.elementsWithoutContainer.allElementsSignal
      )
    )
  }

  private lazy val lineRendering: RenderingWorkbookElementLine =
    RenderingWorkbookElementLine(workbookElement, elementsWithoutContainer, s"$cssLabel exercise-instruction")

  override lazy val render: Element = lineRendering.render
}

