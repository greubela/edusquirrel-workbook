package it.evadid.homepage.workbook.htmlRenderer.interactionEditors

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.model.interaction.plugins.slideshow.{Slideshow, SlideshowPanel}

object HtmlSlideshowEditor extends HtmlRenderFactory[Slideshow] {

  override protected def createDomElement(workbookElement: Slideshow): Element = {
    ???
  }

  def createSlideshowPanelDom(panel: SlideshowPanel): Element = {
    panel.match {
      case s@SlideshowPanel.ImageSlide(imageSrc, titleLabel, description) => createSlideshowPanel(s)
      case s@SlideshowPanel.TwoColumnImagePanel(imageSrc, leftLabel, rightLabel, leftBody, rightBody) => createSlideshowPanel(s)
      case _ => div("not supported panel type: " + panel.getClass.getSimpleName)
    }
  }

  private def createSlideshowPanel(panel: SlideshowPanel.ImageSlide): Element = {
    //val img = HtmlImageElement(panel.image
    ???
  }

  private def createSlideshowPanel(panel: SlideshowPanel.TwoColumnImagePanel): Element = {
    ???
/*
    div(
      cls := "slide-deck-container",
      div(
        cls := "slide-deck-image",
        child <-- HtmlImageElement(panel.image).getDomSignal
      ),
      if (sourceMapId.nonEmpty) {
        div(
          cls := "slide-deck-source",
          child.text <-- fullInfo.signals.stringFromLanguageMapId(sourceMapId)
        )
      } else emptyNode,
      div(
        cls := "slide-deck-text two-columns",
        div(
          cls := "slide-deck-column",
          div(
            cls := "slide-deck-column-title",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, leftLabel).getDomElement()
          ),
          div(
            cls := "slide-deck-column-body",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, leftBody).getDomElement()
          )
        ),
        div(
          cls := "slide-deck-column",
          div(
            cls := "slide-deck-column-title",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, rightLabel).getDomElement()
          ),
          div(
            cls := "slide-deck-column-body",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, rightBody).getDomElement()
          )
        )
      )
    )
    */
    
  }


}
