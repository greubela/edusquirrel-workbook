package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.change.HomepageDataControl.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.SyncControl
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.model.interaction.variable.{InteractionVariable, InteractionVariableHistory}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class HomepageDataControl(fullInfo: FullInfo) {


  private given ExecutionContext = ExecutionContext.global

  private def interactions: List[WorkbookInteraction[?]] = fullInfo.current.allAvailableInteractions

  def downloadAllAvailableData(): Unit = fullInfo.current.workbookUserData.foreach(_.downloadAllData())

  private lazy val cacheControl: CachedSyncControl = CachedSyncControl(fullInfo)

  private[change] def updateContext(func: HomepageInfo => HomepageInfo): Future[Unit] = fullInfo.synchronized {
    def beforeContextChanged(): Future[Unit] = {
      downloadAllAvailableData()
      cacheControl.requestStoreAll(interactions.map(_.interactionVariable), true)
    }

    def afterContextChange(): Future[Unit] = Future {
      val maxTime: LocalDateTime = LocalDateTime.now()
      interactions.foreach(_.interactionVariable.resetHistoryAndSyncControl(Some(cacheControl)))
      cacheControl.requestFetchAll(interactions.map(_.interactionVariable), maxTime)
    }

    beforeContextChanged().flatMap(res1 => {
      Future.traverse(fullInfo.current.currentSyncSources)(_.informAboutContextSwitch()).flatMap(res2 => {
        fullInfo.homepageInfoState.update(func)
        afterContextChange()
      })
    })
  }


  def changeWorkbook(factory: WorkbookFactory): Unit = fullInfo.synchronized {
    changeWorkbook(factory.createEverything)
  }

  def changeWorkbook(newWorkbook: AllWorkbookInfo): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    updateContext(_.copy(workbookInfo = Some(newWorkbook)))
  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoState.now().workbookInfo.nonEmpty) {
      val currentWorkbookInfo: AllWorkbookInfo = fullInfo.homepageInfoState.now().workbookInfo.get
      val newWorkbookInfo: AllWorkbookInfo = currentWorkbookInfo.copy(config = func(currentWorkbookInfo.config))
      fullInfo.homepageInfoState.update(_.copy(workbookInfo = Some(newWorkbookInfo)))
      cacheControl.requestFetchAll(interactions.map(_.interactionVariable), LocalDateTime.now())
    } else {
      fullInfo.loggerSystemInfo.workbookControlLogger.logWarn("[WARN] ignore updated workbook config because there is no workbook loaded!")
    }

  }

  def changeUser(userInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    updateContext(_.copy(userInfo = userInfo))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}

object HomepageDataControl {



}