package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlLangMapContentRenderer.contentIdStringSignal
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.WorkbookLabel


// Special
case class RenderingWithLabel(isInteraction: Boolean, containedElement: AtomarLineRendering, label: WorkbookLabel) extends AtomarLineRendering {

  override lazy val render: Element = RenderingLine(isInteraction, elementsWithoutContainer, "labeled_container").render

  private lazy val cssLabel: String = label.labelType.associatedCssString

  lazy val elementsWithoutContainer: Signal[List[Element]] = {
    Signal.fromValue(List(
      h3(
        cls := s"${cssLabel}__title labeled_container_label",
        text <-- contentIdStringSignal(label.contentId)
      ),
      div(
        cls := s"${cssLabel}__body labeled_container_content",
        children <-- containedElement.elementsWithoutContainer
      )
    ))
  }
}

