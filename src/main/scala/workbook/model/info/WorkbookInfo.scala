package workbook.model.info

import contentmanagement.model.language.HumanLanguage
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class WorkbookInfo(fullscreenElement: HtmlFullScreenElement, config: WorkbookConfig, estimatedDurations: Map[WorkbookInteraction[_], Double]) {

}
