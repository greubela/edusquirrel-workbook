package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.web.file.FileDescription
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

case class VisualNovelPanel(panelContent: HtmlAppElement)

object VisualNovelPanel {

  def apply(image: FileDescription,
            textContent: LanguageMap[HumanLanguage],
            source: LanguageMap[HumanLanguage],
            description: LanguageMap[HumanLanguage],
            workbookInfo: AllWorkbookInfo): VisualNovelPanel = {

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
          child.text <-- workbookInfo.stringSignalFromLanguageMap(source)
        ),
        div(
          cls := "visual-novel-description",
          child.text <-- workbookInfo.stringSignalFromLanguageMap(description)
        ),
        div(
          cls := "visual-novel-text",
          child.text <-- workbookInfo.stringSignalFromLanguageMap(textContent)
        )
      )
    }

    new VisualNovelPanel(panelElement)
  }

  def apply(htmlContent: HtmlAppElement): VisualNovelPanel = {
    new VisualNovelPanel(htmlContent)
  }

}
