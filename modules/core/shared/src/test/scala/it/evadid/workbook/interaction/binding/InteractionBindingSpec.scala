package it.evadid.workbook.interaction.binding

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.abstractions.InteractionFormat.BinaryPixelInteractionFormat
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHandlerBasic}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.{AnswerChangedEvent, InteractionEvents}
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.model.pixel.BinaryPixelImage
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

/** One binding per sync context: connections change, history and context do not. */
class InteractionBindingSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = UsageContext("program", "workbook", "user")
  private val logger = SyncLogger(BasicLogger())

  /** A key-value store whose operations wait for `gate` while it is set. */
  private class Store extends SyncDestinationHandlerBasic {
    val values = State(Map.empty[String, String])
    val gate = State(Option.empty[Promise[Unit]])
    val syncLogger: SyncLogger = logger
    val underlyingDefinition = SyncDestinationDefinition.defaultBrowserHistory
    private def gated[A](operation: => A): Future[A] = gate.now().fold(Future.unit)(_.future).map(_ => synchronized(operation))
    def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = gated(values.now())
    def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = gated(values.now()(key))
    def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] =
      updateElement(key, previous => value -> SyncSuccess.written(key, previous, value))
    def updateElement[R](key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R] = gated {
      val (value, result) = update(values.now().get(key))
      values.update(_.updated(key, value))
      result
    }
    def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = gated {
      values.update(_ - key)
      SyncSuccess.removed(Set(key))
    }
    def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess[String]] = gated {
      val removed = values.now().keySet
      values.set(Map.empty)
      SyncSuccess.removed(removed)
    }
  }

  private def connection(store: Store): SyncConnection = {
    val information = SyncInformation(SyncDestinationDefinition.defaultBrowserHistory, EventSyncStrategy.SYNC_EVERYTHING)
    SyncConnection(Set(information.forContext(context, _ => store.forHistory())), ExecutionContextInformation.default, logger)
  }

  private def confirmed(binding: InteractionBinding[?]): Int = binding.report.now().get.destinationsWithSufficientWrites.size

  test("a stored and read answer is confirmed; a new connection keeps the history but not the confirmation") {
    val variable = TextInteraction("text").interactionVariable
    val binding = variable.bindTo(context, connection(Store()))
    variable.setStateFromUserInteraction("answer", UpdateImportance.MAJOR)
    binding.store(forceFlush = true).flatMap(_ => binding.fetch(forceFlush = true)).map { _ =>
      assertEquals(confirmed(binding), 1)
      binding.connect(connection(Store()))
      assertEquals(confirmed(binding), 0)
      assertEquals(variable.currentValue, "answer")
      assert(variable.binding eq binding)
    }
  }

  test("results of a replaced connection neither confirm nor change the history") {
    val variable = TextInteraction("text").interactionVariable
    val (retired, replacement) = (Store(), Store())
    val binding = variable.bindTo(context, connection(retired))
    variable.setStateFromUserInteraction("answer", UpdateImportance.MAJOR)
    val held = Promise[Unit]()
    retired.gate.set(Some(held))
    retired.values.set(Map(SyncContext.serializer.serialize(binding.syncContext) -> InteractionHistoryJson.fromHistory(
      variable.underlyingInteraction.interactionFormat, InteractionVariableHistory.empty[variable.underlyingInteraction.interactionFormat.type]
        .addEvent(AnswerChangedEvent("retired remote answer", LocalDateTime.now().plusDays(1), UpdateImportance.MAJOR))).serialized))
    val write = binding.store(forceFlush = true)
    val read = binding.fetch(forceFlush = true)
    binding.connect(connection(replacement))
    held.success(())
    write.flatMap(_ => read).map { _ =>
      assertEquals(confirmed(binding), 0)
      assertEquals(variable.currentValue, "answer")
    }
  }

  test("a read answer that the exercise rejects is discarded") {
    val exercise = BinaryPixelInteraction("pixels", LanguageMapContentId("spec/pixels"), BinaryPixelImage.blank(2, 2))
    val variable = exercise.interactionVariable
    val store = Store()
    val binding = variable.bindTo(context, connection(store))
    val resized = InteractionVariableHistory[BinaryPixelInteractionFormat.type](InteractionEvents(Set(
      AnswerChangedEvent[BinaryPixelInteractionFormat.type](BinaryPixelImage.blank(3, 3), LocalDateTime.now(), UpdateImportance.MAJOR))))
    store.values.set(Map(SyncContext.serializer.serialize(binding.syncContext) ->
      InteractionHistoryJson.fromHistory(BinaryPixelInteractionFormat, resized).serialized))
    binding.fetch(forceFlush = true).map(_ => assertEquals(variable.currentValue, BinaryPixelImage.blank(2, 2)))
  }
}
