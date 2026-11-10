package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.interaction.variable.InteractionVariable.InteractionVariableUntyped
import it.evadid.workbook.meta.{ExecutionContextInformation, InteractionVariableSyncReport}
import java.time.LocalDateTime
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

class SyncControlSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global

  private class ControlledSync extends SyncControl {
    val loadStarted = Promise[Unit]()
    val loadFinished = Promise[Unit]()
    val storeStarted = Promise[Unit]()
    val storeFinished = Promise[Unit]()
    override val controlLogger = Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing)
    override val syncLogger = SyncLogger(controlLogger)
    override def ensureCachesAreAtLeastThisRecent(time: LocalDateTime): Future[Unit] = Future.unit
    override def ensureCachesContainLastElementsToWrite(values: List[InteractionVariableUntyped]): Future[Unit] = Future.unit
    override def load(values: List[InteractionVariableUntyped]): Future[Unit] = {
      loadStarted.success(())
      loadFinished.future
    }
    override def requestStore(values: List[InteractionVariableUntyped]): Future[Unit] = {
      storeStarted.success(())
      storeFinished.future
    }
    override def createCurrentReport[T, GA, SA](value: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] =
      throw AssertionError("This operation must not request a report")
    override def createObservableReport[T, GA, SA](value: InteractionVariable[T, GA, SA]): ObservableValue[InteractionVariableSyncReport[T, GA, SA]] =
      throw AssertionError("This operation must not subscribe")
  }

  private def editedAnswer(): InteractionVariable[String, Unit, Unit] = {
    val answer = InteractionVariable(TextInteraction("answer"))
    answer.updateStateFromUserInteraction(_ => "saved draft", UpdateImportance.MAJOR,
      context = ExecutionContextInformation.default)
    answer
  }

  test("fetch and load completes only after loading finishes") {
    val sync = new ControlledSync
    val result: Future[Unit] = sync.ensureFetchAndLoad(Nil)
    sync.loadStarted.future.flatMap { _ =>
      assert(!result.isCompleted)
      sync.loadFinished.success(())
      result.map(_ => assert(!sync.storeStarted.isCompleted))
    }
  }

  test("store and reset waits for loading and storing before clearing local history") {
    val sync = new ControlledSync
    val answer = editedAnswer()
    val result: Future[Unit] = sync.storeAndReset(List(answer))
    sync.loadStarted.future.flatMap { _ =>
      assert(!sync.storeStarted.isCompleted)
      assertEquals(answer.currentValue, "saved draft")
      sync.loadFinished.success(())
      sync.storeStarted.future.flatMap { _ =>
        assert(!result.isCompleted)
        assertEquals(answer.currentValue, "saved draft")
        sync.storeFinished.success(())
        result.map(_ => assertEquals(answer.currentValue, ""))
      }
    }
  }

  test("a load failure propagates and prevents storing or resetting") {
    val sync = new ControlledSync
    val answer = editedAnswer()
    val before = answer.currentHistory
    val failure = IllegalStateException("load failed")
    val result = sync.storeAndReset(List(answer))
    sync.loadStarted.future.flatMap { _ =>
      sync.loadFinished.failure(failure)
      result.failed.map { error =>
        assert(error eq failure)
        assert(!sync.storeStarted.isCompleted)
        assertEquals(answer.currentHistory.historyStates, before.historyStates)
        assertEquals(answer.currentValue, "saved draft")
      }
    }
  }

  test("a store failure propagates and preserves local history") {
    val sync = new ControlledSync
    val answer = editedAnswer()
    val before = answer.currentHistory
    val failure = IllegalStateException("store failed")
    val result = sync.storeAndReset(List(answer))
    sync.loadStarted.future.flatMap { _ =>
      sync.loadFinished.success(())
      sync.storeStarted.future.flatMap { _ =>
        sync.storeFinished.failure(failure)
        result.failed.map { error =>
          assert(error eq failure)
          assertEquals(answer.currentHistory.historyStates, before.historyStates)
          assertEquals(answer.currentValue, "saved draft")
        }
      }
    }
  }
}
