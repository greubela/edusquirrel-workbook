package workbook.model.info

import datastructures.core.language.HumanLanguage
import workbook.model.WorkbookSection
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.interaction.sync.{LocalStorageSync, SyncInformation, SyncStrategy}
import workbook.user.User


case class WorkbookConfig(activeSection: Option[WorkbookSection]) {


}
