package it.evadid.homepage.control.change

import it.evadid.workbook.session.{AllWorkbookInfo, WorkbookConfig}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import scala.concurrent.{ExecutionContext, Future}

/** Workbook choice and section/configuration changes, independent of account persistence. */
final class WorkbookNavigationControl(fullInfo: FullInfo) {
  private given ExecutionContext = ExecutionContext.global
  private val logger = fullInfo.loggerSystemInfo.contentControlLogger

  def changeWorkbook(factory: WorkbookFactory): Unit = fullInfo.synchronized {
    changeWorkbook(Some(factory.createEverything))
  }

  def changeWorkbook(newWorkbook: Option[AllWorkbookInfo]): Unit = fullInfo.synchronized {
    val previous = fullInfo.homepageInfoNow().workbookInfo
    val sameInstance = previous.zip(newWorkbook).forall((a, b) => a.loadedWorkbook eq b.loadedWorkbook)
    if (previous != newWorkbook || !sameInstance) {
      fullInfo.homepageInfoState.update(_.copy(workbookInfo = newWorkbook))
      // Var updates inside a UI observer run in a child transaction. Select the binding after that update.
      if (newWorkbook.nonEmpty) Future.unit.flatMap(_ => fullInfo.syncControl.ensureSynced())
    }
  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoState.now().workbookInfo.nonEmpty) {
      fullInfo.homepageInfoState.update(curInfo => curInfo.copy(workbookInfo = curInfo.workbookInfo.map(info => info.copy(config = func(info.config)))))
    } else {
      logger.logWarn("[WARN] ignore updated workbook config because there is no workbook loaded!")
    }
  }

}
