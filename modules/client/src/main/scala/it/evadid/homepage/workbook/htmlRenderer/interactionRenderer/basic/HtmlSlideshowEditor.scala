package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.util.MarkdownToHtml
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.*
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlBasicCheckboxRenderer.fullInfo
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.interaction.sync.UpdateImportance

/**
 * Renders core slideshow interactions using the legacy slide-deck HTML structure and styles.
 * The renderer owns navigation controls and panel layout, while transition-history updates are delegated to the core slideshow state.
 */
object HtmlSlideshowEditor extends LineBasedRenderingFactory[Slideshow] {

  /**
   * Creates the slideshow DOM with localized navigation buttons and a reactive current-panel view.
   * When navigation succeeds, the method updates the bound interaction state through the model-level transition recorder instead of constructing event data directly.
   */
  override protected def createRendering(workbookElement: Slideshow): AtomarLineRendering = {
    val currentIndex = Var(0)
    val stateVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl,UpdateImportance.MAJOR).toAirstreamVar
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

    def navigation(): Element =
      div(
        cls := "slide-deck-navigation",
        button(
          child.text <-- laminarHelper.plaintextStringSignal("PlantWorkshop/slideshowBack"),
          disabled <-- currentIndex.signal.map(_ == 0),
          onClick.mapTo(-1) --> navigateBy
        ),
        span(
          cls := "slide-deck-counter",
          child.text <-- currentIndex.signal.map(i => s"${i + 1}/$totalSlides")
        ),
        button(
          child.text <-- laminarHelper.plaintextStringSignal("PlantWorkshop/slideshowNext"),
          disabled <-- currentIndex.signal.map(_ >= totalSlides - 1),
          onClick.mapTo(1) --> navigateBy
        )
      )

    if (workbookElement.panels.isEmpty) {
      placeholder(workbookElement, "Slideshow with no panels!")
    } else {
      val dom = div(
        cls := "workbook-interaction",
        child <-- currentIndex.signal.map(index =>
          createSlideshowPanelDom(workbookElement.panels(index), navigation())
        )
      )
      AtomarLineRendering.basicLine(workbookElement, dom, "slide-deck")
    }
  }


  /**
   * Converts a core slideshow panel into the corresponding slide-deck DOM subtree.
   * Navigation is placed directly under the slide image. Unsupported panel implementations
   * receive an explicit fallback element so newly added panel types fail visibly instead of silently disappearing.
   */
  def createSlideshowPanelDom(panel: SlideshowPanel, navigation: Element): Element = {
    panel match {
      case s: SlideshowPanel.ImageSlide => createSlideshowPanel(s, navigation)
      case s: SlideshowPanel.TwoColumnImagePanel => createSlideshowPanel(s, navigation)
      case _ => div("not supported panel type: " + panel.getClass.getSimpleName)
    }
  }

  /**
   * Renders localized markdown content for slide text areas.
   * The language-map signal remains reactive, and the converted markdown is wrapped with the existing `markdown-content` CSS hook.
   */
  private def markdownContent(contentId: LanguageMapContentId): Element = {
    div(
      child <-- laminarHelper.plaintextStringSignal(contentId).map { markdownString =>
        val markdownHtml = MarkdownToHtml.transform(markdownString)
        foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<div class=\"markdown-content\">$markdownHtml</div>"))
      }
    )
  }

  /**
   * Renders a single-image slide with a title and body text using the legacy slide-deck class names.
   * Image loading is delegated to `HtmlImageElement` so file-backed and language-map-backed image sources share the existing image pipeline.
   */
  private def createSlideshowPanel(panel: SlideshowPanel.ImageSlide, navigation: Element): Element = {
    div(
      cls := "slide-deck-container",
      div(
        cls := "slide-deck-image",
        child <-- HtmlImageElement(panel.image).getDomSignal
      ),
      navigation,
      div(
        cls := "slide-deck-text one-column",
        div(
          cls := "slide-deck-column",
          div(
            cls := "slide-deck-column-title",
            markdownContent(panel.titleLabel)
          ),
          div(
            cls := "slide-deck-column-body",
            markdownContent(panel.description)
          )
        )
      )
    )
  }

  /**
   * Renders a two-column image slide using the same structure as the old HTML slideshow implementation.
   * Each column receives localized markdown title and body content while preserving the `slide-deck-text two-columns` styling hook.
   */
  private def createSlideshowPanel(panel: SlideshowPanel.TwoColumnImagePanel, navigation: Element): Element = {
     div(
      cls := "slide-deck-container",
      div(
        cls := "slide-deck-image",
        child <-- HtmlImageElement(panel.image).getDomSignal
      ),
      navigation,
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

/*

ALT CODE YANNECK (todo: remove)

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
*/
