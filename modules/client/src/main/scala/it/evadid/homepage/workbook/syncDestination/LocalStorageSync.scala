package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.sync.*
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

}
