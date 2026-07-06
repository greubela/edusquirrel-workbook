package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.workbook.htmlRenderer.basicRenderer.HtmlLangMapContentRenderer.contentIdStringSignal
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.RenderingWithCards.ElementCard

case class RenderingWithCards[T](isInteraction: Boolean, cards: List[ElementCard]) extends AtomarLineRendering {

  private def renderSingleCard(card: ElementCard): Element =
    div(
      cls := "labeled_container preview-card",
      h3(
        cls := "labeled_container_label preview-label",
        text <-- contentIdStringSignal(card.headline)
      ),
      div(
        cls := "labeled_container_content preview-content",
        children <-- card.elementsWithoutContainer
      )
    )

  override lazy val render: Element = div(
    cls := lineCssStr + " preview-line",
    cards.map(renderSingleCard)
  )

  override lazy val elementsWithoutContainer: Signal[List[Element]] = {
    val mapped: List[Signal[List[Element]]] = cards.map(_.elementsWithoutContainer)
    Signal.combineSeq(mapped).map(_.flatten.toList)
  }
}

object RenderingWithCards {

  object ElementCard {
    def apply(headline: LanguageMapContentId, elementsWithoutContainer: Element): ElementCard = ElementCard(headline, List(elementsWithoutContainer))

    def apply(headline: LanguageMapContentId, elementsWithoutContainer: List[Element]): ElementCard = ElementCard(headline, Signal.fromValue(elementsWithoutContainer))
  }

  case class ElementCard(headline: LanguageMapContentId, elementsWithoutContainer: Signal[List[Element]])


}

