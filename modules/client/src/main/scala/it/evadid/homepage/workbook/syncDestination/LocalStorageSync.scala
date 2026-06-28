package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncFormatter.InteractionSyncRequest
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized
import org.scalajs.dom
import org.scalajs.dom.Storage

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object LocalStorageSync extends SyncDestination {

  private val ec: ExecutionContext = ExecutionContext.global

  private val storage: Storage = dom.window.localStorage

  private val contextToBrowserKeySerializer: Serializer[SyncContext] = SyncContext.serializer

  override def storeTo(context: SyncContext, request: InteractionSyncRequest, formatter: SyncFormatter): Future[SyncInformation.SyncSuccess] = Future {
    val value = formatter.serialize(request)
    val serializedKey: String = contextToBrowserKeySerializer.serialize(context)
    storage.setItem(serializedKey, value)
    SyncSuccess(1, 0, 0, LocalDateTime.now())
  }(using ec)


  override def fetchAll(context: UsageContext): Future[Map[SyncContext, String]] = Future {
    (0 until storage.length)
      .flatMap { i =>
        Option(storage.key(i)).flatMap { browserKey =>
          Option(storage.getItem(browserKey)).map(value => contextToBrowserKeySerializer.deserialize(browserKey) -> value)
        }
      }
      .toMap
  }(using ec)

  override def shouldBePersistant(): Boolean = false

  override def clearAllValues(context: UsageContext): Future[SyncSuccess] = Future {
    resetStorage()
  }(using ec)


  override def clearValues(context: SyncContext): Future[SyncSuccess] = Future {
    resetStorage()
  }(using ec)

  private def resetStorage(): SyncSuccess = {
    dom.window.localStorage.clear()
    SyncSuccess(0, 0, dom.window.localStorage.length, LocalDateTime.now())
  }

}
