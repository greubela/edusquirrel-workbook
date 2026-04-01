package interactionPlugins.visualNovel

import contentmanagement.webElements.HtmlAppElement
import datastructures.web.file.FileDescription
import interactionPlugins.slideshow.SlidePanel
import workbook.model.info.AllWorkbookInfo

@deprecated("Renamed to SlidePanel in interactionPlugins.slideshow", "2026-04-01")
type VisualNovelPanel = SlidePanel

@deprecated("Renamed to SlidePanel in interactionPlugins.slideshow", "2026-04-01")
object VisualNovelPanel {
  def imageSlide(
                  image: FileDescription,
                  textMapId: String,
                  sourceMapId: String,
                  descriptionMapId: String,
                  workbookInfo: AllWorkbookInfo
                ): SlidePanel =
    SlidePanel.imageSlide(image, textMapId, sourceMapId, descriptionMapId, workbookInfo)

  def apply(htmlContent: HtmlAppElement): SlidePanel = SlidePanel(htmlContent)
}
