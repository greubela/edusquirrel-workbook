package workbook.model.info

import contentmanagement.model.language.*
import contentmanagement.storage.DataStorage
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class WorkbookInfo(
                         availableLanguages: List[HumanLanguage],
                         fullscreenElement: HtmlFullScreenElement,
                         config: WorkbookConfig,
                         estimatedDurations: Map[WorkbookInteraction[_], Double]
                       ) {


  def languageStringFromMap(languageMap: LanguageMap[HumanLanguage]): String = languageMap.getInLanguage(config.currentWorkbookLanguage)

  /*
  def languageStringFromMapName(languageMapName: String): Signal[String] = {
    DataStorage.languageMapStorage.loadIntoVariable(languageMapName).signal.combineWith(config.)
    
  }*/


}
