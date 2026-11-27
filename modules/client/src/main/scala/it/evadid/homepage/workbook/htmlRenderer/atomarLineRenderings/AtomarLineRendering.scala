package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.basic.HtmlButtonElement
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

  def exerciseContainerTitleLine(fullInfo: FullInfo, content: LanguageMapContentId): AtomarLineRendering = {
    RenderingAsContainerTitle(LaminarRenderHelper(fullInfo).plaintextStringSignal(content), 1)
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

  def cardLine(fullInfo: FullInfo, workbookElement: WorkbookElement, cards: Seq[ElementCard]): AtomarLineRendering =
    basicLine(fullInfo, workbookElement, cardLineContent(workbookElement, cards), "element-card-grid")

  /** A preview card and the button opening the fullscreen editor. The editor is created on opening, so it starts
    * from the current answer and is released when fullscreen closes.
    */
  def editorCardLine(fullInfo: FullInfo, workbookElement: WorkbookElement, preview: ElementCard, openEditor: () => HtmlAppElement, editorFirst: Boolean = false)
      : AtomarLineRendering = {
    val open = ElementCard(fullInfo, LanguageMapContentId("basic/openEditor"),
      HtmlButtonElement.withTextLabel(fullInfo, "basic/OpenEditor", _ => fullInfo.displayControl.setFullscreen(openEditor())).getDomElement())
    cardLine(fullInfo, workbookElement, if editorFirst then List(open, preview) else List(preview, open))
  }

  def basicLine(fullInfo: FullInfo, workbookElement: WorkbookElement, content: DomElementCollection, additionalCssString: String = ""): AtomarLineRendering = workbookElement match {
    case d: WorkbookDisplayElement => RenderingDisplayLine(d, content, additionalCssString)
    case s: WorkbookStructureElement[?] => RenderingStructureLine(s, content, additionalCssString)
    case i: WorkbookInteractionElement[? <: InteractionFormat] => RenderingInteractionLine(fullInfo, i, content, additionalCssString)
  }



}
