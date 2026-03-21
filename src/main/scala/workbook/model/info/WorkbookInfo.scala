package workbook.model.info

import contentmanagement.model.language.*
import contentmanagement.storage.DataStorage
import workbook.model.abstractions.WorkbookInteraction
import workbook.user.User
import workbook.htmlElements.container.HtmlFullScreenContainerElement

case class WorkbookInfo(
                         availableLanguages: List[HumanLanguage],
                         fullscreenElement: HtmlFullScreenContainerElement,
                         config: WorkbookConfig,
                         estimatedDurations: Map[WorkbookInteraction[_], Double]
                       ) {


  def languageStringFromMap(languageMap: LanguageMap[HumanLanguage]): String = languageMap.getInLanguage(config.currentWorkbookLanguage)
  
  

}
