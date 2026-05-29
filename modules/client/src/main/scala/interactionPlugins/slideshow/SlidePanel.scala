package interactionPlugins.slideshow

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.FileDescription
import workbook.htmlElements.basic.HtmlInstructionElement
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.info.FullInfo

case class SlidePanel(panelContent: HtmlAppElement)

object SlidePanel {

  def imageSlide(image: FileDescription, textMapId: String, descriptionMapId: String, fullInfo: FullInfo): SlidePanel =
    imageSlide(image, textMapId, "", descriptionMapId, fullInfo)

  def imageSlide(image: FileDescription, textMapId: String, sourceMapId: String, descriptionMapId: String, fullInfo: FullInfo): SlidePanel = {

    val imageElement = HtmlImageElement(image, fullInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          child <-- imageElement.getDomSignal
        ),
        if (sourceMapId.nonEmpty) {
          div(
            cls := "slide-deck-source",
            child.text <-- fullInfo.signals.stringFromLanguageMapId(sourceMapId)
          )
        } else emptyNode,
        div(
          cls := "slide-deck-text",
          div(
            cls := "slide-deck-text-title",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, descriptionMapId).getDomElement()
          ),
          div(
            cls := "slide-deck-text-body",
            HtmlInstructionElement.fromMarkdownLanguageMapId(fullInfo, textMapId).getDomElement()
          )
        )
      )
    }

    SlidePanel(panelElement)
  }

  def imageSlideTwoColumns(image: FileDescription,
                          leftLabel: String,
                          rightLabel: String,
                          leftBody: String,
                          rightBody: String,
                          fullInfo: FullInfo): SlidePanel =
    imageSlideTwoColumns(image, "", leftLabel, rightLabel, leftBody, rightBody, fullInfo)

  def imageSlideTwoColumns(image: FileDescription,
                          sourceMapId: String,
                          leftLabel: String,
                          rightLabel: String,
                          leftBody: String,
                          rightBody: String,
                          fullInfo: FullInfo): SlidePanel = {

    val imageElement = HtmlImageElement(image, fullInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          child <-- imageElement.getDomSignal
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
    }

    SlidePanel(panelElement)
  }

  def apply(htmlContent: HtmlAppElement): SlidePanel = new SlidePanel(htmlContent)
}
