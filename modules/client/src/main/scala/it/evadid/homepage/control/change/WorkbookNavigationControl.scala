package it.evadid.homepage.control.change

import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import scala.concurrent.{ExecutionContext, Future}

/** Workbook choice and section/configuration changes, independent of account persistence. */
final class WorkbookNavigationControl(fullInfo: FullInfo) {
  private given ExecutionContext = ExecutionContext.global
  private val logger = fullInfo.loggerSystemInfo.contentControlLogger

  def changeWorkbook(factory: WorkbookFactory): Unit = changeWorkbook(Some(factory.createEverything))

  /** Opens `newWorkbook`; its answers are bound and read from every destination. */
  def changeWorkbook(newWorkbook: Option[AllWorkbookInfo]): Future[Unit] = fullInfo.state.switchWorkbook(newWorkbook)

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = {
    if (fullInfo.homepageInfoNow().workbookInfo.nonEmpty) {
      fullInfo.homepage.updateWorkbookConfig(func)
    } else {
      logger.logWarn("[WARN] ignore updated workbook config because there is no workbook loaded!")
    }
  }

}
