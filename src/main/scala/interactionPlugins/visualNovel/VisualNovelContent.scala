package interactionPlugins.visualNovel

import com.raquo.laminar.api.L.Var
import contentmanagement.model.file.FileDescription
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo

object VisualNovelContent {

  def monkPanels(workbookInfoVar: Var[WorkbookInfo]): List[VisualNovelPanel] = {
    val defaultSource = LanguageMap.universalMap[HumanLanguage]("Created with Dall-E 3 by André Greubel")
    val defaultDescription = LanguageMap.universalMap[HumanLanguage]("Monks visual novel panel")

    List(
      VisualNovelPanel(
        image = FileDescription.relativeToResourceFolder("workbookresources/monks/Image01.jpg"),
        textContent = LanguageMap.universalMap[HumanLanguage]("A traveler enters the temple of Mons Computarius."),
        source = defaultSource,
        description = defaultDescription,
        workbookInfoVar = workbookInfoVar
      ),
      VisualNovelPanel(
        image = FileDescription.relativeToResourceFolder("workbookresources/monks/Image02.jpg"),
        textContent = LanguageMap.universalMap[HumanLanguage]("The monk silently observes and waits."),
        source = defaultSource,
        description = defaultDescription,
        workbookInfoVar = workbookInfoVar
      )
    )
  }

}
