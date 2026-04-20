package workbook.model.info

import datastructures.core.language.{AppLanguage, HumanLanguage}
import workbook.model.Workbook
import workbook.model.abstractions.WorkbookInteraction

case class AllWorkbookInfo(
                            availableLanguages: List[HumanLanguage],
                            loadedWorkbook: Workbook, 
                            config: WorkbookConfig, 
                            estimatedDurations: Map[WorkbookInteraction[_], Double]) {

  
  
  
}
