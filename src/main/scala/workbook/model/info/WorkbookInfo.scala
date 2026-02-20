package workbook.model.info

import contentmanagement.model.language.*
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class WorkbookInfo(fullscreenElement: HtmlFullScreenElement, config: WorkbookConfig, estimatedDurations: Map[WorkbookInteraction[_], Double]) {


  def languageStringFromMap(languageMap: LanguageMap[HumanLanguage]): String = languageMap.getInLanguage(config.currentWorkbookLanguage)


}
