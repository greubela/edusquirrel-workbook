package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.slideshow

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.InteractionVariableOnJS
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.plugins.slideshow.{Slideshow, SlideshowPanel, SlideshowState}
import it.evadid.workbook.model.interaction.sync.UpdateImportance

import java.time.LocalDateTime

object HtmlSlideshowRenderer extends HtmlRenderFactory[Slideshow] {

  override protected def createDomElement(slideshow: Slideshow): Element = {
    val panels = slideshow.panels
    require(panels.nonEmpty, "Slideshow requires at least one panel")

    val stateVar = slideshow.interactionVariable.createBoundVarWithUpdateImportance(UpdateImportance.MAJOR)
    val currentIndex = Var(0)

    def navigateBy(offset: Int): Unit = {
      val oldIndex = currentIndex.now()
      val newIndex = (oldIndex + offset).max(0).min(panels.length - 1)
      if (newIndex == oldIndex) return

      currentIndex.set(newIndex)

      val currentState = stateVar.now()
      val oldPanel = panels(oldIndex)
      val newPanel = panels(newIndex)
      val alreadySeen = currentState.events.exists(_.newPanel == newPanel)
      if (!alreadySeen) {
        val event = SlideshowState.SlideshowProceededEvent(oldPanel, newPanel, LocalDateTime.now())
        stateVar.set(currentState.copy(events = currentState.events + event))
      }
    }

    def markdownFromMapId(contentId: LanguageMapContentId): Element =
      div(
        child <-- contentIdStringSignal(contentId).map { markdownString =>
          val markdownHtml = MarkdownToHtml.transform(markdownString)
          foreignHtmlElement(
            DomApi.unsafeParseHtmlString(s"<div class=\"instruction-content markdown-content\">$markdownHtml</div>")
          )
        }
      )

    def renderImageSlide(panel: SlideshowPanel.ImageSlide): Element =
      div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          HtmlImageElement(panel.image.location).getDomElement()
        ),
        div(
          cls := "slide-deck-text",
          div(cls := "slide-deck-text-title", markdownFromMapId(panel.headerMapId)),
          div(cls := "slide-deck-text-body", markdownFromMapId(panel.bodyMapId))
        )
      )

    def renderTwoColumnSlide(panel: SlideshowPanel.TwoColumnImagePanel): Element =
      div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          HtmlImageElement(panel.image.location).getDomElement()
        ),
        div(
          cls := "slide-deck-text two-columns",
          div(
            cls := "slide-deck-column",
            div(cls := "slide-deck-column-title", markdownFromMapId(panel.leftLabel)),
            div(cls := "slide-deck-column-body", markdownFromMapId(panel.leftBody))
          ),
          div(
            cls := "slide-deck-column",
            div(cls := "slide-deck-column-title", markdownFromMapId(panel.rightLabel)),
            div(cls := "slide-deck-column-body", markdownFromMapId(panel.rightBody))
          )
        )
      )

    def renderPanel(panel: SlideshowPanel): Element = panel match {
      case p: SlideshowPanel.ImageSlide => renderImageSlide(p)
      case p: SlideshowPanel.TwoColumnImagePanel => renderTwoColumnSlide(p)
    }

    div(
      cls := "workbook-interaction slide-deck-exercise",
      div(
        cls := "slide-deck-navigation",
        button(
          child.text <-- contentIdStringSignal(LanguageMapContentId("PlantWorkshop/slideshowBack")),
          disabled <-- currentIndex.signal.map(_ == 0),
          onClick.mapTo(-1) --> navigateBy
        ),
        span(
          cls := "slide-deck-counter",
          child.text <-- currentIndex.signal.map(i => s"${i + 1}/${panels.length}")
        ),
        button(
          child.text <-- contentIdStringSignal(LanguageMapContentId("PlantWorkshop/slideshowNext")),
          disabled <-- currentIndex.signal.map(_ >= panels.length - 1),
          onClick.mapTo(1) --> navigateBy
        )
      ),
      child <-- currentIndex.signal.map(i => renderPanel(panels(i)))
    )
  }
}
