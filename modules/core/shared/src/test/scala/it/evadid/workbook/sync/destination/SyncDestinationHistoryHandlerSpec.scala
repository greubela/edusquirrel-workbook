package it.evadid.workbook.sync.destination

import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.sync.{SyncContext, SyncSuccess, UpdateImportance, UsageContext}
import it.evadid.workbook.history.{InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.sync.model.InteractionHistoryJson
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
    def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] =
      updateElement(key, previous => value -> SyncSuccess.written(key, previous, value))
    def updateElement[R](key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R] = Future {
      synchronized { // atomic, like the browser stores
        val (value, result) = update(values.now().get(key))
        values.update(_.updated(key, value))
        result
      }
    }
    def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = {
      val removed = values.now().keySet.intersect(Set(key))
      values.update(_ - key)
      Future.successful(SyncSuccess.removed(removed))
    }
    def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = {
      val removed = values.now().keySet
      values.set(Map.empty)
      cleared.success(())
      Future.successful(SyncSuccess.removed(removed))
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
    import it.evadid.workbook.history.events.*
    val destination = new Destination(Map.empty)
    val handler = destination.forHistory()
    def delta(value: String, offset: Long): String = InteractionHistoryJson.fromHistory(textFormat,
      InteractionVariableHistory.empty[textFormat.type].addEvent(AnswerChangedEvent[textFormat.type](value, at.plusNanos(offset), UpdateImportance.MAJOR))).serialized
    val deltas = (1 to 8).map(index => delta(index.toString, index))
    Future.traverse(deltas)(json => handler.storeHistoryJson(key, json)).flatMap { _ =>
      handler.readHistory[textFormat.type](key).map(restored => assertEquals(restored.size, deltas.size))
    }
  }
  test("full local reset clears corrupt records without decoding histories or keys") {
    val destination = new Destination(Map("unreadable key" -> "invalid JSON"), prohibitReads = true)
    destination.forHistory().clearAllHistories(context).map { result =>
      assert(destination.cleared.isCompleted)
      assertEquals(result.elementsRemoved, Set.empty[SyncContext], "only history keys are reported")
    }
  }

  test("local stores confirm the sent events they hold after merging, distinguishing new from known events") {
    import it.evadid.workbook.history.events.*
    val handler = new Destination(Map.empty).forHistory()
    def answer(value: String, importance: UpdateImportance, offset: Long): InteractionEvent[textFormat.type] =
      AnswerChangedEvent[textFormat.type](value, at.plusSeconds(offset), importance)
    def json(events: InteractionEvent[textFormat.type]*): String =
      InteractionHistoryJson.fromHistory(textFormat, InteractionVariableHistory(InteractionEvents(events.toSet))).serialized
    val first = answer("first", UpdateImportance.MAJOR, 1)
    val superseded = answer("draft", UpdateImportance.TEMPORARY, 2)
    val latest = answer("latest", UpdateImportance.MAJOR, 3)
    handler.storeHistoryJson(key, json(first)).flatMap { stored =>
      assertEquals((stored.elementsAdded, stored.elementsUnchanged), (Set(first.eventId), Set.empty[EventId]))
      handler.storeHistoryJson(key, json(first, superseded, latest))
    }.map { stored =>
      assertEquals(stored.elementsAdded, Set(latest.eventId))
      assertEquals(stored.elementsUnchanged, Set(first.eventId))
      assert(!stored.elementsStored.contains(superseded.eventId), "the cleaned merge did not keep the temporary answer")
    }
  }

  test("a local store under a changed format replaces the earlier history") {
    import it.evadid.workbook.history.events.*
    val handler = new Destination(Map.empty).forHistory()
    val checkbox = InteractionFormat.LabeledCheckboxInteractionFormat
    val changed = InteractionVariableHistory.empty[checkbox.type].addEvent(AnswerChangedEvent[checkbox.type](true, at, UpdateImportance.MAJOR))
    handler.storeHistoryJson(key, history).flatMap { _ =>
      handler.storeHistoryJson(key, InteractionHistoryJson.fromHistory(checkbox, changed).serialized)
    }.flatMap(_ => handler.readHistoryJson(key)).map { read =>
      assertEquals(InteractionHistoryJson.deserialize(read.fetchedValues.head.dataValue).typedAs(checkbox), changed)
    }
  }

  test("local removal reports the removed history contexts") {
    val other = context.toSyncContext("other")
    val handler = new Destination(Map(SyncContext.serializer.serialize(key) -> history, SyncContext.serializer.serialize(other) -> history)).forHistory()
    handler.clearValue(key).flatMap { removed =>
      assertEquals(removed.elementsRemoved, Set(key))
      handler.clearAllHistories(context)
    }.map(removed => assertEquals(removed.elementsRemoved, Set(other)))
  }

}
