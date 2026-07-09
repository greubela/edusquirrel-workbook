package it.evadid.homepage.control.model

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.AllWorkbookInfo.*
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.model.abstractions.WorkbookInteractionElement

case class AllWorkbookInfo(
                            loadedWorkbook: Workbook,
                            config: WorkbookConfig,
                            estimatedDurations: Map[WorkbookInteractionElement[?], Double]) {

  def getMetadata(): WorkbookMetadata = WorkbookMetadata(loadedWorkbook.workbookTitle, loadedWorkbook.availableLanguages)

  private val toString: String = s"AllWorkbookInfo(loadedWorkbook: ${loadedWorkbook.workbookTitle}, config: $config, estimatedDurations: $estimatedDurations)"

}

object AllWorkbookInfo {

  case class WorkbookMetadata(workbookId: LanguageMapContentId, availableLanguages: List[HumanLanguage])

}
