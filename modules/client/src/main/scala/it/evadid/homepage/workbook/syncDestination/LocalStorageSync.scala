package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache
import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.interaction.sync.*
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

  override def storeTo(context: SyncContext, history: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncInformation.SyncSuccess] = Future {
    try {
      val value: String = formatter.serialize(context, history)
      val serializedKey: String = contextToBrowserKeySerializer.serialize(context)
      //println(s"###################### [DEBUG] storing to local storage: $serializedKey -> $value")
      storage.setItem(serializedKey.toString, value.toString)
      SyncSuccess(1, 0, 0, LocalDateTime.now())
    } catch case e: Exception => {
      e.printStackTrace()
      throw e
    }
  }(using ec)


  /*override def fetchAll(context: UsageContext): Future[Map[SyncContext, InteractionVariableHistorySerialized]] = Future {

  }(using ec)*/

  override def shouldBePersistant(): Boolean = false

  override def clearAllValues(context: UsageContext): Future[SyncSuccess] = Future {
    resetCompleteStorage()
  }(using ec)


  override def clearValues(context: SyncContext): Future[SyncSuccess] = Future {
    resetCompleteStorage()
  }(using ec)

  def resetCompleteStorage(): SyncSuccess = {
    println("[UGLY WARN IN LOCALSTORAGESYNC] clearing all local storage!")
    dom.window.localStorage.clear()
    SyncSuccess(0, 0, dom.window.localStorage.length, LocalDateTime.now())
  }

  override def fetchAll(context: UsageContext, formatter: SyncFormatter): Future[RemoteSyncDataCache.FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = {
    val resMap: Map[SyncContext, InteractionVariableHistorySerialized] = (0 until storage.length)
      .flatMap { i =>
        Option(storage.key(i)).flatMap { browserKey =>
          Option(storage.getItem(browserKey)).map(value => contextToBrowserKeySerializer.deserialize(browserKey) -> formatter.deserialize(value))
        }
      }
      .toMap

    val res = FetchResponse.fromMap[SyncContext, InteractionVariableHistorySerialized](time.LocalDateTime.now(), resMap, _.lastStateOption.map(_.timestamp))
    Future.successful(res)
  }
}
