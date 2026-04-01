package interactionPlugins.slideshow

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.FileDescription
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.info.AllWorkbookInfo

case class SlidePanel(panelContent: HtmlAppElement)

object SlidePanel {

  def imageSlide(
                  image: FileDescription,
                  textMapId: String,
                  sourceMapId: String,
                  descriptionMapId: String,
                  workbookInfo: AllWorkbookInfo
                ): SlidePanel = {

    val imageElement = HtmlImageElement(image, workbookInfo)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "visual-novel-container",
        div(
          cls := "visual-novel-image",
          child <-- imageElement.getDomSignal
        ),
        div(
          cls := "visual-novel-source",
          child.text <-- workbookInfo.stringSignalFromLanguageMapId(sourceMapId)(scala.concurrent.ExecutionContext.global)
        ),
        div(
          cls := "visual-novel-description",
          child.text <-- workbookInfo.stringSignalFromLanguageMapId(descriptionMapId)(scala.concurrent.ExecutionContext.global)
        ),
        div(
          cls := "visual-novel-text",
          child.text <-- workbookInfo.stringSignalFromLanguageMapId(textMapId)(scala.concurrent.ExecutionContext.global)
        )
      )
    }

    SlidePanel(panelElement)
  }

  def apply(htmlContent: HtmlAppElement): SlidePanel = new SlidePanel(htmlContent)
}
