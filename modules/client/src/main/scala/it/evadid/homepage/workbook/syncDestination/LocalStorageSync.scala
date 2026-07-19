package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.LoggingLevel.WARN
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import org.scalajs.dom
import org.scalajs.dom.Storage

import java.time
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object LocalStorageSync extends SyncDestination {

  private val ec: ExecutionContext = ExecutionContext.global

  private val storage: Storage = dom.window.localStorage

  private val contextToBrowserKeySerializer: Serializer[SyncContext] = SyncContext.serializer

  override def storeTo(logger: SyncLogger, context: SyncContext, history: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncInformation.SyncSuccess] = Future {
    try {
      val value: String = formatter.serialize(context, history)
      val serializedKey: String = contextToBrowserKeySerializer.serialize(context)
      //println(s"###################### [DEBUG] storing to local storage: $serializedKey -> $value")
      storage.setItem(serializedKey.toString, value.toString)
      SyncSuccess(1, 0, 0, LocalDateTime.now())
    } catch case e: Exception => {
      logger.logExceptionWarn("LocalStorageSync, error at storeTo (ignoring write)", e)
      e.printStackTrace()
      throw e
    }
  }(using ec)


  /*override def fetchAll(context: UsageContext): Future[Map[SyncContext, InteractionVariableHistorySerialized]] = Future {

  }(using ec)*/

  override def shouldBePersistant(): Boolean = false

  override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = Future {
    resetCompleteStorage()
  }(using ec)


  override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = Future {
    resetCompleteStorage()
  }(using ec)

  def resetCompleteStorage(): SyncSuccess = {
    println("[UGLY WARN IN LOCALSTORAGESYNC] clearing all local storage!")
    dom.window.localStorage.clear()
    SyncSuccess(0, 0, dom.window.localStorage.length, LocalDateTime.now())
  }

  private def transformBack(logger: SyncLogger, formatter: SyncFormatter, browserKey: String, browserValue: String): Option[(SyncContext, InteractionVariableHistorySerialized)] = try {
    Some(contextToBrowserKeySerializer.deserialize(browserKey) -> formatter.deserialize(browserValue))
  } catch case (e: Exception) => {
    logger.log(s"LocalStorageSync: Ignore tuple (${browserKey}, ${browserValue}) because it was unparsable: ${e.getMessage}", WARN, Option(false))
    None
  }

  override def fetchAll(logger: SyncLogger, context: UsageContext, formatter: SyncFormatter): Future[RemoteSyncDataCache.FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = {
    val resMap: Map[SyncContext, InteractionVariableHistorySerialized] = (0 until storage.length).flatMap(i =>
      val browserKey = storage.key(i)
      val browserValue = storage.getItem(browserKey)
      transformBack(logger, formatter, browserKey, browserValue)
    ).toMap

    val res = FetchResponse.fromMap[SyncContext, InteractionVariableHistorySerialized](time.LocalDateTime.now(), resMap, _.lastStateOption.map(_.timestamp))
    Future.successful(res)
  }

  override def isLocal: Boolean = true
}
