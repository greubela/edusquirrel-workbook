package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.plugins.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.model.interaction.sync.UpdateImportance

/**
 * Renders core slideshow interactions using the legacy slide-deck HTML structure and styles.
 * The renderer owns navigation controls and panel layout, while transition-history updates are delegated to the core slideshow state.
 */
object HtmlSlideshowEditor extends HtmlRenderFactory[Slideshow] {

  /**
   * Creates the slideshow DOM with localized navigation buttons and a reactive current-panel view.
   * When navigation succeeds, the method updates the bound interaction state through the model-level transition recorder instead of constructing event data directly.
   */
  override protected def createDomElement(workbookElement: Slideshow): Element = {
    val currentIndex = Var(0)
    val stateVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR).toAirstreamVar
    val totalSlides = workbookElement.panels.length

    def navigateBy(offset: Int): Unit = {
      if (totalSlides > 0) {
        val oldIndex = currentIndex.now()
        val newIndex = (oldIndex + offset).max(0).min(totalSlides - 1)
        if (newIndex != oldIndex) {
          currentIndex.set(newIndex)
          stateVar.update(_.recordTransitionByIndex(oldIndex, newIndex))
        }
      }
    }

    if (workbookElement.panels.isEmpty) {
      div(cls := "workbook-interaction slide-deck", div("Slideshow has no panels."))
    } else {
      div(
        cls := "workbook-interaction slide-deck",
        div(
          cls := "slide-deck-navigation",
          button(
            child.text <-- contentIdStringSignal(LanguageMapContentId("PlantWorkshop/slideshowBack")),
            disabled <-- currentIndex.signal.map(_ == 0),
            onClick.mapTo(-1) --> navigateBy
          ),
          span(
            cls := "slide-deck-counter",
            child.text <-- currentIndex.signal.map(i => s"${i + 1}/$totalSlides")
          ),
          button(
            child.text <-- contentIdStringSignal(LanguageMapContentId("PlantWorkshop/slideshowNext")),
            disabled <-- currentIndex.signal.map(_ >= totalSlides - 1),
            onClick.mapTo(1) --> navigateBy
          )
        ),
        child <-- currentIndex.signal.map(index => createSlideshowPanelDom(workbookElement.panels(index)))
      )
    }
  }

  /**
   * Converts a core slideshow panel into the corresponding slide-deck DOM subtree.
   * Unsupported panel implementations receive an explicit fallback element so newly added panel types fail visibly instead of silently disappearing.
   */
  def createSlideshowPanelDom(panel: SlideshowPanel): Element = {
    panel match {
      case s: SlideshowPanel.ImageSlide => createSlideshowPanel(s)
      case s: SlideshowPanel.TwoColumnImagePanel => createSlideshowPanel(s)
      case _ => div("not supported panel type: " + panel.getClass.getSimpleName)
    }
  }

  /**
   * Renders localized markdown content for slide text areas.
   * The language-map signal remains reactive, and the converted markdown is wrapped with the existing `markdown-content` CSS hook.
   */
  private def markdownContent(contentId: LanguageMapContentId): Element = {
    div(
      child <-- contentIdStringSignal(contentId).map { markdownString =>
        val markdownHtml = MarkdownToHtml.transform(markdownString)
        foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"markdown-content\">$markdownHtml</div>"))
      }
    )
  }

  /**
   * Renders a single-image slide with a title and body text using the legacy slide-deck class names.
   * Image loading is delegated to `HtmlImageElement` so file-backed and language-map-backed image sources share the existing image pipeline.
   */
  private def createSlideshowPanel(panel: SlideshowPanel.ImageSlide): Element = {
    div(
      cls := "slide-deck-container",
      div(
        cls := "slide-deck-image",
        child <-- HtmlImageElement(panel.image).getDomSignal
      ),
      div(
        cls := "slide-deck-description",
        markdownContent(panel.titleLabel)
      ),
      div(
        cls := "slide-deck-text",
        div(
          cls := "slide-deck-text-body",
          markdownContent(panel.description)
        )
      )
    )
  }

  /**
   * Renders a two-column image slide using the same structure as the old HTML slideshow implementation.
   * Each column receives localized markdown title and body content while preserving the `slide-deck-text two-columns` styling hook.
   */
  private def createSlideshowPanel(panel: SlideshowPanel.TwoColumnImagePanel): Element = {
    div(
      cls := "slide-deck-container",
      div(
        cls := "slide-deck-image",
        child <-- HtmlImageElement(panel.image).getDomSignal
      ),
      div(
        cls := "slide-deck-text two-columns",
        div(
          cls := "slide-deck-column",
          div(
            cls := "slide-deck-column-title",
            markdownContent(panel.leftLabel)
          ),
          div(
            cls := "slide-deck-column-body",
            markdownContent(panel.leftBody)
          )
        ),
        div(
          cls := "slide-deck-column",
          div(
            cls := "slide-deck-column-title",
            markdownContent(panel.rightLabel)
          ),
          div(
            cls := "slide-deck-column-body",
            markdownContent(panel.rightBody)
          )
        )
      )
    )
  }
}
