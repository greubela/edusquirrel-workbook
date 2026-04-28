package interactionPlugins.slideshow

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.FileDescription
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.info.FullInfo

case class SlidePanel(panelContent: HtmlAppElement)

object SlidePanel {

  def imageSlide(image: FileDescription, textMapId: String, sourceMapId: String, descriptionMapId: String, fullInfo: FullInfo): SlidePanel = {

    val imageElement = HtmlImageElement(image, fullInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "slide-deck-container",
        div(
          cls := "slide-deck-image",
          child <-- imageElement.getDomSignal
        ),
        div(
          cls := "slide-deck-source",
          child.text <-- fullInfo.signals.stringFromLanguageMapId(sourceMapId)
        ),
        div(
          cls := "slide-deck-description",
          child.text <-- fullInfo.signals.stringFromLanguageMapId(descriptionMapId)
        ),
        div(
          cls := "slide-deck-text",
          child.text <-- fullInfo.signals.stringFromLanguageMapId(textMapId)
        )
      )
    }

    SlidePanel(panelElement)
  }

  def apply(htmlContent: HtmlAppElement): SlidePanel = new SlidePanel(htmlContent)
}
