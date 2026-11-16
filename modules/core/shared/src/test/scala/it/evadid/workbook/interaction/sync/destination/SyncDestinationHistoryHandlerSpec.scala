package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.{SyncContext, SyncSuccess, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}
import upickle.default.*

class SyncDestinationHistoryHandlerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = UsageContext("program", "workbook", "user")
  private val key = context.toSyncContext("answer_history")
  private val at = LocalDateTime.of(2026, 1, 1, 12, 0)
  private val history = write(InteractionVariableHistory[String, Unit, Unit](Set(
    InteractionVariableState("saved", UpdateImportance.MAJOR, at))))

  private class Destination(values: Map[String, String], prohibitReads: Boolean = false) extends SyncDestinationHandlerBasic {
    val cleared = Promise[Unit]()
    val syncLogger = SyncLogger(BasicLogger())
    val underlyingDefinition = SyncDestinationDefinition.defaultBrowserHistory
    def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = {
      assert(!prohibitReads, "reset must not decode saved records")
      Future.successful(values)
    }
    def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = Future.successful(values(key))
    def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      cleared.success(())
      Future.successful(SyncSuccess.emptyNow())
    }
  }

  test("local history reads discard malformed records and isolate their usage context") {
    val values = Map(SyncContext.serializer.serialize(key) -> write(history),
      SyncContext.serializer.serialize(context.toSyncContext("invalid_history")) -> write("not JSON"),
      SyncContext.serializer.serialize(key.copy(userId = "other")) -> write(history))
    val handler = new Destination(values).forHistory()
    handler.readHistoriesJson(context).flatMap { fetched =>
      assertEquals(fetched.fetchedValues.size, 1)
      assertEquals(InteractionHistoryJson.lastTimestamp(fetched.fetchedValues.head.dataValue), Some(at))
      assert(fetched.timestampFetchResponse.isAfter(at))
      handler.readHistoryJson(key).map { single =>
        assertEquals(InteractionHistoryJson.lastTimestamp(single.fetchedValues.head.dataValue), Some(at))
        assertEquals(single.fetchedValues.head.dataValue, history)
      }
    }
  }

}
