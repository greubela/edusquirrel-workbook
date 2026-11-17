package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.interaction.sync.{SyncContext, SyncSuccess, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}
import upickle.default.*

class SyncDestinationHistoryHandlerSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = UsageContext("program", "workbook", "user")
  private val key = context.toSyncContext("answer_history")
  private val at = LocalDateTime.of(2026, 1, 1, 12, 0)
  
  private val textFormat = InteractionFormat.TextInteractionFormat
  private val history = InteractionHistoryJson.fromHistory(textFormat,
    InteractionVariableHistory.empty[textFormat.type].addState(InteractionVariableState("saved", UpdateImportance.MAJOR, at))).serialized

  private class Destination(initial: Map[String, String], prohibitReads: Boolean = false) extends SyncDestinationHandlerBasic {
    val values = it.evadid.core.datastructures.state.State(initial)
    val cleared = Promise[Unit]()
    val syncLogger = SyncLogger(BasicLogger())
    val underlyingDefinition = SyncDestinationDefinition.defaultBrowserHistory
    def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = {
      assert(!prohibitReads, "reset must not decode saved records")
      Future.successful(values.now())
    }
    def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = Future.successful(values.now()(key))
    def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    def updateElement(key: String, update: Option[String] => String)(using ec: ExecutionContext): Future[SyncSuccess] = Future {
      values.update(current => current.updated(key, update(current.get(key))))
      SyncSuccess.emptyNow()
    }
    def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      cleared.success(())
      Future.successful(SyncSuccess.emptyNow())
    }
  }

  test("local history reads discard malformed records and isolate their usage context") {
    val values = Map(SyncContext.serializer.serialize(key) -> history,
      SyncContext.serializer.serialize(context.toSyncContext("invalid_history")) -> write("not JSON"),
      SyncContext.serializer.serialize(key.copy(userId = "other")) -> history)
    val handler = new Destination(values).forHistory()
    handler.readHistoriesJson(context).flatMap { fetched =>
      assertEquals(fetched.fetchedValues.size, 1)
      val historyJson = fetched.fetchedValues.head.dataValue
      val typedHistory = InteractionHistoryJson.deserialize(historyJson).toTyped
      assertEquals(typedHistory.timestampLastChanged, Some(at))
      assert(fetched.timestampFetchResponse.isAfter(at))
      handler.readHistoryJson(key).map { single =>
        val singleHistoryJson = single.fetchedValues.head.dataValue
        val singleTyped = InteractionHistoryJson.deserialize(singleHistoryJson).toTyped
        assertEquals(singleTyped.timestampLastChanged, Some(at))
        assertEquals(singleHistoryJson, history)
      }
    }
  }


  test("separate and concurrent local deltas merge instead of overwriting saved answer events") {
    import it.evadid.workbook.interaction.variable.events.*
    val destination = new Destination(Map.empty)
    val handler = destination.forHistory()
    def delta(value: String, offset: Long): String = InteractionHistoryJson.fromHistory(textFormat,
      InteractionVariableHistory.empty[textFormat.type].addEvent(AnswerChangedEvent[textFormat.type](value, at.plusNanos(offset), UpdateImportance.MAJOR))).serialized
    val deltas = (1 to 8).map(index => delta(index.toString, index))
    Future.traverse(deltas)(json => handler.storeHistoryJson(key, json)).flatMap { _ =>
      handler.readHistory[textFormat.type](key).map(restored => assertEquals(restored.size, deltas.size))
    }
  }
}
