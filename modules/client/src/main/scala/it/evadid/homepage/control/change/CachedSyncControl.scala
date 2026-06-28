package it.evadid.homepage.control.change

import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.model.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.model.interaction.sync.{SyncContext, SyncControl}
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext, SyncSuccess}
import it.evadid.workbook.model.interaction.variable.{InteractionVariable, InteractionVariableHistory, InteractionVariableHistorySerialized}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


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
        //println("Successfully executed fetch : " + formatted)
        resMap.flatMap(_._2.toOption).foreach(cache => interactionVariable.executeLoad(List(cache)))
      }
      case Failure(exception) => println(s"Error while fetching sync data: $exception")
    }
  }

  override def requestStore[T](from: InteractionVariable[T]): Unit = fullInfo.synchronized {
    fullInfo.current.currentSyncSources.foreach(curInfo => requestStore(curInfo, from))
  }

  def requestStoreAll(interactionVariable: List[InteractionVariable[?]]): Future[Unit] = fullInfo.synchronized {
    Future.traverse(interactionVariable)(requestStoreAll).map(theList => {})
  }

  def requestStoreAll(interactionVariable: InteractionVariable[?]): Future[Unit] = fullInfo.synchronized {
    Future.traverse(fullInfo.current.currentSyncSources)((currentSyncSource: SyncInformationWithContext) => {
      requestStore(currentSyncSource, interactionVariable)
    }).map(theList => {})
  }

  def requestStore(syncSource: SyncInformationWithContext, interactionVariable: InteractionVariable[?]): Future[?] = fullInfo.synchronized {
    val cache: Option[SyncCache] = requestCache.getSyncIfInCache(syncSource, false)

    val historySerialized: InteractionVariableHistorySerialized = interactionVariable.history.serializedWithStrategy(syncSource.syncStrategy, interactionVariable.underlyingInteraction.serializer)
    val lastEventToSync: LocalDateTime = historySerialized.lastState.timestamp
    val skippedEvents: Int = interactionVariable.history.events.size - historySerialized.states.size

    if (cache.nonEmpty && lastEventToSync.isBefore(cache.get.createdAt)) {
      println(s"[INFO] skip storing ${interactionVariable.keyForSerialization} with latest event at $lastEventToSync ($skippedEvents events skipped with strategy ${syncSource.syncStrategy.getClass.getSimpleName}) because it is already stored (${cache.get.createdAt})!")
      Future.successful(())
    } else {
      println(s"[INFO] storing ${interactionVariable.keyForSerialization} with latest event at $lastEventToSync to destination ($skippedEvents events skipped with strategy ${syncSource.syncStrategy.getClass.getSimpleName})")
      val syncContext = syncSource.usageContext.toSyncContext(interactionVariable.keyForSerialization)
      //storeTo(interactionVariable.keyForSerialization, interactionVariable.history, interactionVariable.underlyingInteraction.serializer)
      syncSource.syncSource.storeTo(syncContext, historySerialized, syncSource.formatter)
    }

  }


}
