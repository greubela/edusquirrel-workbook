package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.{DomElementCollection, LaminarRenderHelper}
import it.evadid.workbook.abstractions.WorkbookElement

trait AtomarLineRendering extends HtmlAppElement {

  def getDomElement(): Element = render

  lazy val render: Element
  lazy val elementsWithoutContainer: DomElementCollection

  protected val elementCssString: String = "workbook-element"
  protected val displayCssString: String = "display-element"
  protected val interactionCssString: String = "interaction-element"
  protected val interactionContentCssString: String = "interaction-element-content"
  protected val structureCssString: String = "structure-element"

}

object AtomarLineRendering {

  def exerciseContainerTitleLine(content: LanguageMapContentId): AtomarLineRendering = {
    RenderingAsContainerTitle(LaminarRenderHelper.singleton.plaintextStringSignal(content), 1)
  }

  def cardLine(workbookElement: WorkbookElement, cards: List[ElementCard]): AtomarLineRendering = {

    val cardContent = div(
      cls := "element-cards",
      div(cls := "element-card-line label-line",
        cards.map(_.labelElement)),
      div(cls := "element-card-line content-line",
        cards.map(_.contentElement))
    )

    RenderingWorkbookElementLine(workbookElement, cardContent, "element-card-grid")
  }

  def basicLine(workbookElement: WorkbookElement, content: DomElementCollection, additionalCssString: String = ""): RenderingWorkbookElementLine = RenderingWorkbookElementLine(workbookElement, content, additionalCssString)

  /* HELPER */


}
