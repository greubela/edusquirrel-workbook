package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import it.evadid.util.logging.LoggingLevel.WARN
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.{SyncDestination, SyncDestinationHistory, SyncDestinationRaw}
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import org.scalajs.dom
import org.scalajs.dom.Storage
import org.scalajs.dom.experimental.storage

import java.time
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object LocalStorageSync {

  val instance: LocalStorageSync = LocalStorageSync()

  def fetchAllRawSync(logger: Logger): Map[String, String] = {
    val storage: Storage = dom.window.localStorage
    (0 until storage.length).map(i =>
      val browserKey = storage.key(i)
      val browserValue = storage.getItem(browserKey)
      browserKey -> browserValue
    ).toMap
  }

  def storeToRawSync(logger: Logger, key: String, value: String): Boolean = try {
    dom.window.localStorage.setItem(key.toString, value.toString)
    true
  } catch case (err: Throwable) => {
    false
  }

}

case class LocalStorageSync(maxValueCharakterSize: Long = 100000) extends SyncDestinationRaw with SyncDestinationHistory {

  private val historyKeyPrefix = "synced-variable"

  private val ec: ExecutionContext = ExecutionContext.global

  private val storage: Storage = dom.window.localStorage

  private val contextToBrowserKeySerializer: Serializer[SyncContext] = SyncContext.serializer

  override def storeTo(logger: SyncLogger, context: SyncContext, history: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess] = Future {
    try {
      val value: String = formatter.serialize(context, history)
      if (value.length > maxValueCharakterSize) {
        throw new IllegalArgumentException(s"Value exceeds ${maxValueCharakterSize} characters (has ${value.length}). Will not save to LocalStorage!")
      }
      val serializedKey: String = historyKeyPrefix + contextToBrowserKeySerializer.serialize(context)
      //println(s"###################### [DEBUG] storing to local storage: $serializedKey -> $value")
      storage.setItem(serializedKey.toString, value.toString)
      SyncSuccess(1, 0, 0, LocalDateTime.now())
    } catch case e: Exception => {
      logger.logExceptionWarn("LocalStorageSync, error at storeTo (ignoring write)", e)
      e.printStackTrace()
      throw e
    }
  }(using ec)

  override def shouldBePersistant(): Boolean = false

  override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = Future {
    resetCompleteStorage()
  }(using ec)


  override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = Future {
    resetCompleteStorage()
  }(using ec)

  def removeKey(key: String): Unit = {
    dom.window.localStorage.removeItem(key)
  }

  def resetCompleteStorage(): SyncSuccess = {
    println("[UGLY WARN IN LOCALSTORAGESYNC] clearing all local storage!")
    dom.window.localStorage.clear()
    SyncSuccess(0, 0, dom.window.localStorage.length, LocalDateTime.now())
  }

  private def transformBack(logger: SyncLogger, formatter: SyncFormatter, browserKey: String, browserValue: String): Option[(SyncContext, InteractionVariableHistorySerialized)] = try {
    if (!browserKey.startsWith(historyKeyPrefix)) None else {
      val browserKeyWithoutPrefix = browserKey.substring(historyKeyPrefix.length, browserKey.length)
      Some(contextToBrowserKeySerializer.deserialize(browserKeyWithoutPrefix) -> formatter.deserialize(browserValue))
    }
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

  override def toString: String = s"LocalStorageSync(${maxValueCharakterSize})"


  override protected def deserializeFromConstructorLikeString(from: ConstructorLikeReadResult): Option[SyncDestination] = {
    if (from.elementType == this.getClass.getSimpleName) Some(LocalStorageSync(from.jsonPayloads.head.toInt))
    else None
  }

  override protected def serializeToConstructorLikeString(): ConstructorLikeReadResult = {
    ConstructorLikeReadResult(this.getClass.getSimpleName, List(maxValueCharakterSize.toString))
  }

  override protected def readAllRaw(): Future[Map[String, String]] = try {
    val resMap = (0 until storage.length).map(i =>
      val browserKey = storage.key(i)
      val browserValue = storage.getItem(browserKey)
      browserKey -> browserValue
    ).toMap
    Future.successful(resMap)
  } catch case (err: Throwable) => {
    Future.failed(err)
  }

  override protected def storeToRaw(key: String, value: String): Future[Boolean] = try {
    storage.setItem(key.toString, value.toString)
    Future.successful(true)
  } catch case (err: Throwable) => {
    Future.failed(err)
  }


}
