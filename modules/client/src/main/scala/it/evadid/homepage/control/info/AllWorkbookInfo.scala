package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import AllWorkbookInfo.WorkbookMetadata
import it.evadid.workbook.model.elements.Workbook
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class AllWorkbookInfo(
                            loadedWorkbook: Workbook,
                            config: WorkbookConfig,
                            estimatedDurations: Map[WorkbookInteraction[?], Double]) {

  def getMetadata(): WorkbookMetadata = WorkbookMetadata(loadedWorkbook.workbookTitle, loadedWorkbook.availableLanguages)

  private val toString: String = s"AllWorkbookInfo(loadedWorkbook: ${loadedWorkbook.workbookTitle}, config: $config, estimatedDurations: $estimatedDurations)"

}

object AllWorkbookInfo {

  case class WorkbookMetadata(workbookId: LanguageMapContentId, availableLanguages: List[HumanLanguage])

}
