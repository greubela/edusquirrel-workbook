package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.{DomElementCollection, LaminarRenderHelper}
import it.evadid.workbook.abstractions.*

trait AtomarLineRendering extends HtmlAppElement {

  def getDomElement(): Element = render

  lazy val render: Element
  lazy val elementsWithoutContainer: DomElementCollection

  protected val elementCssString: String = "workbook-element"
  protected val displayCssString: String = "display-element"
  protected val interactionCssString: String = "interaction-element"
  protected val interactionContentCssString: String = "interaction-element-content"
  protected val structureCssString: String = "structure-element"

  protected def getCssString(workbookElement: WorkbookElement): String = workbookElement match {
    case d: WorkbookDisplayElement => displayCssString
    case i: WorkbookInteractionElement[? <: InteractionFormat] => interactionCssString
    case s: WorkbookStructureElement[?] => structureCssString
  }
}

object AtomarLineRendering {

  def exerciseContainerTitleLine(content: LanguageMapContentId): AtomarLineRendering = {
    RenderingAsContainerTitle(LaminarRenderHelper.singleton.plaintextStringSignal(content), 1)
  }

  def cardLineContent(workbookElement: WorkbookElement, cards: Seq[ElementCard]): Element = {
    div(
      cls := "element-cards",
      div(cls := "element-card-line label-line",
        cards.map(_.labelElement)),
      div(cls := "element-card-line content-line",
        cards.map(_.contentElement))
    )
  }

  def cardLine(workbookElement: WorkbookElement, cards: Seq[ElementCard], showBottomGradingPanel: Signal[Boolean] = Var(false).signal): AtomarLineRendering = {
    val cardBasedContent = cardLineContent(workbookElement, cards)
    basicLine(workbookElement, cardBasedContent, "element-card-grid", showBottomGradingPanel)
  }

  def basicLine(workbookElement: WorkbookElement, content: DomElementCollection, additionalCssString: String = "", showBottomGradingPanel: Signal[Boolean] = Var(false).signal): AtomarLineRendering = workbookElement match {
    case d: WorkbookDisplayElement => RenderingDisplayLine(d, content, additionalCssString)
    case s: WorkbookStructureElement[?] => RenderingStructureLine(s, content, additionalCssString)
    case i: WorkbookInteractionElement[? <: InteractionFormat] => RenderingInteractionLine(i, content, additionalCssString, showBottomGradingPanel)
  }


  def lineWithGrader[F <: InteractionFormat](workbookInteractionElement: WorkbookInteractionElement[F], content: DomElementCollection, additionalCssString: String = ""): AtomarLineRendering = {
    basicLine(workbookInteractionElement, content, additionalCssString, Var(true).signal)
  }

}
