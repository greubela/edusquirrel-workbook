package workbook.model.info

import datastructures.core.language.{AppLanguage, HumanLanguage}
import workbook.factory.WorkbookMetadataJson
import workbook.model.Workbook
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.AllWorkbookInfo.WorkbookMetadata

case class AllWorkbookInfo(
                            loadedWorkbook: Workbook, 
                            config: WorkbookConfig, 
                            estimatedDurations: Map[WorkbookInteraction[_], Double]) {

  def getMetadata(): WorkbookMetadata = WorkbookMetadata(loadedWorkbook.titleLanguageMapId, loadedWorkbook.availableInLanguages)

}

object AllWorkbookInfo {

  case class WorkbookMetadata(workbookId: String, availableLanguages: List[HumanLanguage])

}
