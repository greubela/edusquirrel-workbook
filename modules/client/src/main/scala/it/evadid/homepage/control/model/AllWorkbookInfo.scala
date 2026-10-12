package it.evadid.homepage.control.model

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.AllWorkbookInfo.*
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.elements.structureElements.Workbook

case class AllWorkbookInfo(
                            loadedWorkbook: Workbook,
                            config: WorkbookConfig,
                            estimatedDurations: Map[WorkbookInteractionElement[?], Double]) derives upickle.default.ReadWriter {

  private val toString: String = s"AllWorkbookInfo(loadedWorkbook: ${loadedWorkbook.metadata.workbookTitle}, config: $config, estimatedDurations: $estimatedDurations)"

}

object AllWorkbookInfo {


}
