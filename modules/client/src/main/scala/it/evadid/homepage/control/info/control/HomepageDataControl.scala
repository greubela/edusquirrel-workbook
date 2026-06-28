package it.evadid.homepage.control.info.control

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataStateFinished
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.info.control.HomepageDataControl.CachedSyncControl
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.SyncControl
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.model.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableState}

import java.time
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class HomepageDataControl(fullInfo: FullInfo) {

  private given ExecutionContext = ExecutionContext.global

  private def interactions: List[WorkbookInteraction[?]] = fullInfo.current.allAvailableInteractions

  def downloadAllAvailableData(): Unit = fullInfo.current.workbookUserData.foreach(_.downloadAllData())

  private lazy val cacheControl: CachedSyncControl = HomepageDataControl.CachedSyncControl(fullInfo)

  private[control] def updateContext(func: HomepageInfo => HomepageInfo): Future[Unit] = fullInfo.synchronized {
    def beforeContextChanged(): Future[Unit] = {
      downloadAllAvailableData()
      cacheControl.requestStoreAll(interactions.map(_.interactionVariable))
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
      println("[WARN] ignore updated workbook config because there is no workbook loaded!")
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


  case class CachedSyncControl(fullInfo: FullInfo) extends SyncControl {

    private given ExecutionContext = ExecutionContext.global

    // load
    private val requestCache: AsyncDataCache[SyncInformationWithContext, SyncCache] = new AsyncDataCache[SyncInformationWithContext, SyncCache]("syncRequestCache", false, true) {

      override protected def executeLoading(in: SyncInformationWithContext)(ec: ExecutionContext): Future[SyncCache] = {
        in.fetchAllFrom()
      }

      override protected def formatInputForLogging(in: SyncInformationWithContext): String = s"SyncInfoWithContext(${in.usageContext})"

      override protected def formatOutputForLogging(out: SyncCache): String = s"SyncCache(${out.createdAt}: ${out.values.size} values)"
    }

    private def executeLoadAll(maxAge: LocalDateTime = LocalDateTime.now()): Future[Map[SyncInformationWithContext, Either[Throwable, SyncCache]]] = {
      requestCache.loadAllAsFuture(fullInfo.current.currentSyncSources, maxAge)
    }

    def requestFetchAll(variables: List[InteractionVariable[?]], maxCacheAge: LocalDateTime): Unit = fullInfo.synchronized {
      variables.foreach(intVar => requestFetch(intVar, maxCacheAge))
    }

    override def requestFetch(interactionVariable: InteractionVariable[?], maxCacheAge: LocalDateTime): Unit = fullInfo.synchronized {

      def format(in: SyncInformationWithContext, out: Either[Throwable, SyncCache]): String = out.match {
        case Left(err) => "Failure(" + in.syncSource.getClass.getSimpleName + " -> " + err + ")"
        case Right(err) => "Success(" + in.syncSource.getClass.getSimpleName + " -> " + err.values.size + " elements)"

      }

      val futMap: Future[Map[SyncInformationWithContext, Either[Throwable, SyncCache]]] = executeLoadAll(maxCacheAge)
      futMap.onComplete {
        case Success(resMap) => {
          val formatted = resMap.map(format).mkString("FetchResults(", ",", ")")
          println("Successfully executed fetch : " + formatted)
          resMap.flatMap(_._2.toOption).foreach(cache => interactionVariable.executeLoad(List(cache)))
        }
        case Failure(exception) => println(s"Error while fetching sync data: $exception")
      }
    }

    override def requestStore[T](keyForSerialisation: String, history: InteractionVariableHistory[T], valueSerializer: Serializer[T], forceSyncNow: Boolean): Unit = fullInfo.synchronized {
      fullInfo.current.currentSyncSources.foreach(_.storeTo(keyForSerialisation, history, valueSerializer))
    }

    def requestStoreAll(interactionVariable: List[InteractionVariable[?]]): Future[Unit] = fullInfo.synchronized {
      Future.traverse(interactionVariable)(requestStoreAll).map(theList => {})
    }

    def requestStoreAll(interactionVariable: InteractionVariable[?]): Future[Unit] = fullInfo.synchronized {
      Future.traverse(fullInfo.current.currentSyncSources)((currentSyncSource: SyncInformationWithContext) => {
        currentSyncSource.storeTo(interactionVariable.keyForSerialization, interactionVariable.history, interactionVariable.underlyingInteraction.serializer)
      }).map(theList => {})
    }


  }

}