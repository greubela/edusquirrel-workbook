package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.*
import contentmanagement.model.file.FileDescription
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.HtmlAppElement
import workbook.htmlElements.basic.HtmlImageElement
import workbook.model.info.WorkbookInfo

case class VisualNovelPanel(panelContent: HtmlAppElement)

object VisualNovelPanel {

  def apply(image: FileDescription,
            textContent: LanguageMap[HumanLanguage],
            source: LanguageMap[HumanLanguage],
            description: LanguageMap[HumanLanguage],
            workbookInfoVar: Var[WorkbookInfo]): VisualNovelPanel = {

    val imageElement = HtmlImageElement(image, workbookInfoVar)

    val panelElement = new HtmlAppElement {
      override def getDomElement(): Element = div(
        cls := "visual-novel-container",
        div(
          cls := "visual-novel-image",
          child <-- imageElement.getDomSignal
        ),
        div(
          cls := "visual-novel-source",
          child.text <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(source.getInLanguage)
        ),
        div(
          cls := "visual-novel-description",
          child.text <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(description.getInLanguage)
        ),
        div(
          cls := "visual-novel-text",
          child.text <-- workbookInfoVar.signal.map(_.config.currentWorkbookLanguage).map(textContent.getInLanguage)
        )
      )
    }

    new VisualNovelPanel(panelElement)
  }

  def apply(htmlContent: HtmlAppElement): VisualNovelPanel = {
    new VisualNovelPanel(htmlContent)
  }

}
