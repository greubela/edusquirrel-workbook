package it.evadid.workbook.session

import it.evadid.workbook.elements.structure.Workbook

case class AllWorkbookInfo(
                            loadedWorkbook: Workbook,
                            config: WorkbookConfig,
                            estimatedDurations: Map[String, Double]) derives upickle.default.ReadWriter {

  override def toString: String = s"AllWorkbookInfo(loadedWorkbook: ${loadedWorkbook.metadata.workbookTitle}, config: $config, estimatedDurations: $estimatedDurations)"

}
