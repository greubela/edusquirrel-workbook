package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.WorkbookLabel


// Special
case class RenderingWithLabel(workbookElement: WorkbookElement, containedElement: AtomarLineRendering, label: WorkbookLabel) extends AtomarLineRendering {

  override lazy val render: Element = div("todo!") //RenderingLine(workbookElement, elementsWithoutContainer, "labeled_container").render

  private lazy val cssLabel: String = label.labelType.associatedCssString

  lazy val elementsWithoutContainer: DomElementCollection = {
    val list = List(
      h3(
        cls := s"${cssLabel}__title labeled_container_label",
        text <-- laminarHelper.plaintextStringSignal(label.contentId)
      ),
      div(
        cls := s"${cssLabel}__body labeled_container_content",
        children <-- containedElement.elementsWithoutContainer.allElementsSignal
      )
    )
    list
  }
}

