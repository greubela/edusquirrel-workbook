package interactionPlugins.visualNovel

import datastructures.web.file.FileDescription
import interactionPlugins.slideshow.SlidePanel
import workbook.model.info.AllWorkbookInfo

object VisualNovelContent {

  def monkPanels(workbookInfo: AllWorkbookInfo): List[SlidePanel] = {
    List(
      SlidePanel.imageSlide(
        image = FileDescription.relativeToResourceFolder("workbookresources/monks/Image01.jpg"),
        textMapId = "TestWorkbook/monkText1",
        sourceMapId = "TestWorkbook/monkSource",
        descriptionMapId = "TestWorkbook/monkDescription",
        workbookInfo = workbookInfo
      ),
      SlidePanel.imageSlide(
        image = FileDescription.relativeToResourceFolder("workbookresources/monks/Image02.jpg"),
        textMapId = "TestWorkbook/monkText2",
        sourceMapId = "TestWorkbook/monkSource",
        descriptionMapId = "TestWorkbook/monkDescription",
        workbookInfo = workbookInfo
      )
    )
  }

}
