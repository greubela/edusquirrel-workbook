package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.Var
import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.web.file.FileDescription
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}

object VisualNovelContent {

  def monkPanels(workbookInfo: AllWorkbookInfo): List[VisualNovelPanel] = {
    val defaultSource = LanguageMap.universalMap[HumanLanguage]("Created with Dall-E 3 by André Greubel")
    val defaultDescription = LanguageMap.universalMap[HumanLanguage]("Monks visual novel panel")

    List(
      VisualNovelPanel(
        image = FileDescription.relativeToResourceFolder("workbookresources/monks/Image01.jpg"),
        textContent = LanguageMap.universalMap[HumanLanguage]("A traveler enters the temple of Mons Computarius."),
        source = defaultSource,
        description = defaultDescription,
        workbookInfo = workbookInfo
      ),
      VisualNovelPanel(
        image = FileDescription.relativeToResourceFolder("workbookresources/monks/Image02.jpg"),
        textContent = LanguageMap.universalMap[HumanLanguage]("The monk silently observes and waits."),
        source = defaultSource,
        description = defaultDescription,
        workbookInfo = workbookInfo
      )
    )
  }

}
