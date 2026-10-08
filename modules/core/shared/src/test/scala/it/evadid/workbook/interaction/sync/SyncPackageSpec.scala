package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import it.evadid.workbook.interaction.sync.SyncInformation.{InteractionVariableFetchResponse, SyncFetchedHistory}
import it.evadid.workbook.interaction.sync.destination.{SyncDestination, SyncDestinationHistory}
import it.evadid.workbook.interaction.variable.*
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.{read, write}

class SyncPackageSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing))
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)
  private val usage = UsageContext("program", "scenario", "user")
  private val context = usage.toSyncContext("answer_history")
  private val serializer = Serializer.stringIO
  private val formatter = SyncFormatter.serializeHistory

  private def event(value: String, importance: UpdateImportance, seconds: Int) =
    InteractionVariableState(value, importance, time.plusSeconds(seconds))

  private val major = event("major", UpdateImportance.MAJOR, 1)
  private val minor = event("minor", UpdateImportance.MINOR, 2)
  private val temporary = event("temporary", UpdateImportance.TEMPORARY, 3)
  private val default = event("default", UpdateImportance.DEFAULT, 4)
  private val history = InteractionVariableHistory(Set(major, minor, temporary, default))
  private val serialized = InteractionVariableHistory(Set(major, minor, temporary)).serialized(serializer)

  private class Destination extends SyncDestinationHistory {
    var fetched: Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] =
      Future.successful(InteractionVariableFetchResponse(time, Set(DataEntryReadFromServer(context, serialized, time))))
    var stored = List.empty[(SyncContext, InteractionVariableHistorySerialized, SyncFormatter)]
    var fetchArguments = List.empty[(UsageContext, SyncFormatter)]
    var writeResult = Future.successful(SyncSuccess(1, 2, 3, time))
    var synchronousReadError: Option[Throwable] = None
    var synchronousWriteError: Option[Throwable] = None
    override def shouldBePersistant(): Boolean = true
    override def isLocal: Boolean = true
    override def fetchAll(logger: SyncLogger, context: UsageContext, formatter: SyncFormatter): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = {
      synchronousReadError.foreach(error => throw error)
      fetchArguments = fetchArguments :+ (context, formatter)
      fetched
    }
    override def storeTo(logger: SyncLogger, context: SyncContext, value: InteractionVariableHistorySerialized, formatter: SyncFormatter): Future[SyncSuccess] = {
      synchronousWriteError.foreach(error => throw error)
      stored = stored :+ ((context, value, formatter))
      writeResult
    }
    override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    override protected def deserializeFromConstructorLikeString(from: ConstructorLikeReadResult): Option[SyncDestination] = None
    override protected def serializeToConstructorLikeString(): ConstructorLikeReadResult = ConstructorLikeReadResult("TestDestination", Nil)
  }

  private def location(destination: Destination = new Destination, strategy: SyncStrategy = SyncStrategy.SYNC_EVERYTHING) =
    SyncInformation(destination, strategy, formatter).forContext(usage)

  test("UsageContext and SyncContext convert without losing identifiers and serialize special characters") {
    assertEquals(context.toUsageContext, usage)
    assertEquals(context.keyForSerialisation, "answer_history")
    val special = SyncContext("program\"", "scenario\n", "用户", "key/with spaces")
    assertEquals(SyncContext.serializer.deserialize(SyncContext.serializer.serialize(special)), special)
  }

  test("SyncSuccess combines counters and keeps the latest committed timestamp in either order") {
    val first = SyncSuccess(1, 2, 3, time)
    val second = SyncSuccess(4, 5, 6, time.plusSeconds(1))
    val expected = SyncSuccess(5, 7, 9, time.plusSeconds(1))
    assertEquals(first.combine(second), expected)
    assertEquals(second.combine(first), expected)
    assertEquals(first.combine(first.copy(elementsAdded = 0, elementsChanged = 0, elementsRemoved = 0)), first)
    val empty = SyncSuccess.emptyNow()
    assertEquals((empty.elementsAdded, empty.elementsChanged, empty.elementsRemoved), (0, 0, 0))
    assertEquals(read[SyncSuccess](write(first)), first)
  }

  test("UpdateImportance round-trips every level") {
    UpdateImportance.values.foreach(level => assertEquals(read[UpdateImportance](write(level)), level))
  }

  test("every SyncStrategy selects the specified relevance and never mistakes a default for the latest update") {
    val expected = List(
      SyncStrategy.SYNC_EVERYTHING -> history.events,
      SyncStrategy.SYNC_MAJOR -> Set(major),
      SyncStrategy.SYNC_MINOR -> Set(major, minor),
      SyncStrategy.SYNC_LAST -> Set(temporary),
      SyncStrategy.SYNC_LAST_AND_MAJOR -> Set(major, temporary))
    expected.foreach((strategy, events) => {
      assertEquals(strategy.selectEventsToSync(history).events, events)
      assertEquals(strategy.selectEventsToSync(InteractionVariableHistory.empty[String]).events, Set.empty[InteractionVariableState[String]])
    })
    val defaults = InteractionVariableHistory(Set(default))
    assertEquals(SyncStrategy.SYNC_LAST.selectEventsToSync(defaults).events, Set.empty[InteractionVariableState[String]])
    assertEquals(SyncStrategy.SYNC_LAST_AND_MAJOR.selectEventsToSync(defaults).events, Set.empty[InteractionVariableState[String]])
    assertEquals(SyncStrategy.SYNC_MAJOR.toString, "SYNC_ONLY(MAJOR)")
  }

  test("SyncFormatter serializes histories including empty histories and safely rejects malformed data") {
    assertEquals(formatter.deserialize(formatter.serialize(context, serialized)), serialized)
    assertEquals(formatter.deserialize(formatter.serialize(context, InteractionVariableHistorySerialized.empty)), InteractionVariableHistorySerialized.empty)
    assertEquals(formatter.tryDeserialize("not JSON"), None)
    assertEquals(formatter.tryDeserialize(formatter.serialize(context, serialized)), Some(serialized))
  }

  test("RichInteractionVariableFormatter derives metadata from the latest state regardless of set order") {
    import SyncFormatter.*
    val richFormatter = RichInteractionVariableFormatter()
    val text = richFormatter.serialize(context, serialized)
    val rich = richFormatter.deserializeAsRich(text)
    assertEquals(rich, RichInteractionVariableHistorySerialized(context.keyForSerialisation, temporary.timestamp,
      serializer.serialize(temporary.value), serialized))
    assertEquals(richFormatter.deserialize(text), serialized)
    assertEquals(richFormatter.tryDeserialize("{}"), None)
    intercept[NoSuchElementException](richFormatter.serialize(context, InteractionVariableHistorySerialized.empty))
    val request = InteractionSyncRequest(context, rich.fullHistory)
    assertEquals(request.syncContext, context)
    assertEquals(request.history, serialized)
  }

  test("SyncInformation binds a usage context and fetch response classes retain typed and unparsed data") {
    val destination = new Destination
    val info = SyncInformation(destination, SyncStrategy.SYNC_LAST, formatter)
    val bound = info.forContext(usage)
    assertEquals(bound.syncInformation, info)
    assertEquals(bound.usageContext, usage)
    val entries = Set(DataEntryReadFromServer(context, serialized, time.minusSeconds(1)))
    val response = InteractionVariableFetchResponse(time, entries)
    assertEquals(response.timestampFetchResponse, time)
    assertEquals(response.fetchedValues, entries)
    val unparsed = InteractionVariableHistorySerialized(Set(InteractionVariableStateSerialized("broken", UpdateImportance.MINOR, time)))
    val fetched = SyncFetchedHistory(history, time, unparsed)
    assertEquals(fetched.typedElements, history)
    assertEquals(fetched.fetchedAt, time)
    assertEquals(fetched.unparsableElements, unparsed)
  }

  test("SyncInformationWithContext fetches through its configured destination and reader") {
    val destination = new Destination
    val bound = location(destination)
    bound.fetchAllFrom(logger).flatMap { response =>
      assertEquals(response.fetchedValues, Set(DataEntryReadFromServer(context, serialized, time)))
      bound.reader.fetchByKey(logger, context).flatMap { byKey =>
        assertEquals(byKey.timestampFetchResponse, time)
        bound.reader.fetchAll(logger).map { all =>
          assertEquals(all.fetchedValues, response.fetchedValues)
          assertEquals(destination.fetchArguments, List.fill(3)((usage, formatter)))
        }
      }
    }
  }

  test("SyncInformationWithContext turns synchronous destination exceptions into failed futures") {
    val destination = new Destination
    val error = new IllegalStateException("destination unavailable")
    destination.synchronousReadError = Some(error)
    destination.synchronousWriteError = Some(error)
    val bound = location(destination)
    bound.reader.fetchAll(logger).failed.flatMap { readError =>
      assert(readError eq error)
      bound.writer.writeForKey(logger, context, serialized).failed.map(writeError => assert(writeError eq error))
    }
  }

  test("SyncInformationWithContext preserves asynchronous read and write failures") {
    val destination = new Destination
    val error = new IllegalStateException("offline")
    destination.fetched = Future.failed(error)
    destination.writeResult = Future.failed(error)
    val bound = location(destination)
    bound.fetchAllFrom(logger).failed.flatMap { readError =>
      assert(readError eq error)
      bound.writer.writeAll(logger, Map(context -> serialized)).failed.map(writeError => assert(writeError eq error))
    }
  }

  test("SyncInformationWithContext batches writes and combines their acknowledgements") {
    val destination = new Destination
    val bound = location(destination)
    val otherKey = usage.toSyncContext("other")
    bound.writer.writeAll(logger, Map(context -> serialized, otherKey -> serialized)).flatMap { success =>
      assertEquals((success.elementsAdded, success.elementsChanged, success.elementsRemoved), (2, 4, 6))
      assertEquals(destination.stored.map(_._1).toSet, Set(context, otherKey))
      assert(destination.stored.forall(entry => entry._2 == serialized && entry._3 == formatter))
      bound.writer.writeAll(logger, Map.empty).map { empty =>
        assertEquals(empty.elementsAdded, 0)
        assertEquals(destination.stored.size, 2)
      }
    }
  }

  test("SyncInformationWithContext serializes selected variable events and skips an empty selection") {
    val variable = InteractionVariable(TextInteraction("answer"))
    variable.updateHistory(_ => history)
    val bound = location(strategy = SyncStrategy.SYNC_MAJOR)
    val entries = bound.dataToStore(variable)
    assertEquals(entries.size, 1)
    assertEquals(entries.head.dataKey, context)
    assertEquals(entries.head.timestampDataCreated, major.timestamp)
    assertEquals(entries.head.dataValue.deserializeIgnoreErrors(serializer).events, Set(major))
    variable.resetLocalHistory()
    assertEquals(bound.dataToStore(variable), Nil)
    bound.informAboutContextSwitch().map(result => assertEquals(result.elementsRemoved, 0))
  }

  test("InteractionVariableSyncReport merges histories and partitions synced locations at timestamp boundaries") {
    val synced = location()
    val stale = location()
    val missing = location()
    val local = InteractionVariableHistory(Set(temporary))
    val remote = InteractionVariableHistory(Set(major, minor)).serialized(serializer)
    val statuses = Map(
      synced -> SyncStatus(Some(temporary.timestamp), context, Some(DataEntryReadFromServer(context, remote, time))),
      stale -> SyncStatus(Some(temporary.timestamp.minusNanos(1)), context, Some(DataEntryReadFromServer(context, remote, time))),
      missing -> SyncStatus[SyncContext, InteractionVariableHistorySerialized](None, context, None))
    val report = InteractionVariableSyncReport(serializer, local, statuses, List(synced, stale, missing))
    assertEquals(report.typedMap.keySet, Set(synced, stale))
    assertEquals(report.lastRemoteStates(synced), Some(minor))
    assertEquals(report.lastRemoteStates(missing), None)
    assertEquals(report.latestStateIsSyncedTo, Set(synced))
    assertEquals(report.latestStateIsNotSyncedTo, Set(stale, missing))
    assertEquals(report.allRemoteStates, Set(major, minor))
    assertEquals(report.allStatesEverywhere, Set(major, minor, temporary))
    val emptyLocal = report.copy(curLocalHistory = InteractionVariableHistory.empty[String])
    assertEquals(emptyLocal.latestStateIsSyncedTo, Set(synced, stale, missing))
  }

  test("InteractionVariableSyncReport ignores invalid serialized values when collecting remote history") {
    val bound = location()
    val invalid = InteractionVariableStateSerialized("not an integer", UpdateImportance.MINOR, time)
    val valid = InteractionVariableStateSerialized("42", UpdateImportance.MAJOR, time.plusSeconds(1))
    val remote = InteractionVariableHistorySerialized(Set(invalid, valid))
    val report = InteractionVariableSyncReport(Serializer.intDecimalIO, InteractionVariableHistory.empty[BigInt],
      Map(bound -> SyncStatus(Some(time), context, Some(DataEntryReadFromServer(context, remote, time)))), List(bound))
    assertEquals(report.allRemoteStates, Set(InteractionVariableState(BigInt(42), UpdateImportance.MAJOR, time.plusSeconds(1))))
  }

  test("SyncCache separates typed and unparsed events and returns empty histories for missing contexts") {
    val invalid = InteractionVariableStateSerialized("not an integer", UpdateImportance.MINOR, time)
    val valid = InteractionVariableStateSerialized("42", UpdateImportance.MAJOR, time.plusSeconds(1))
    val cache = SyncCache(time.plusSeconds(2), usage, Map(context -> InteractionVariableHistorySerialized(Set(valid, invalid))))
    val typed = cache.typedHistory("answer_history", Serializer.intDecimalIO)
    assertEquals(typed, cache.typedHistory(context, Serializer.intDecimalIO))
    assertEquals(typed.fetchedAt, time.plusSeconds(2))
    assertEquals(typed.typedElements.events, Set(InteractionVariableState(BigInt(42), UpdateImportance.MAJOR, time.plusSeconds(1))))
    assertEquals(typed.unparsableElements.states, Set(invalid))
    val missing = cache.typedHistory("missing", serializer)
    assertEquals(missing.typedElements, InteractionVariableHistory.empty[String])
    assertEquals(missing.unparsableElements, InteractionVariableHistorySerialized.empty)
    assertEquals(cache.typedHistory(context.copy(userId = "other"), serializer).typedElements.events, Set.empty[InteractionVariableState[String]])
  }
}
