package it.evadid.workbook.interaction.sync

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.*
import it.evadid.workbook.interaction.sync.model.*
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import it.evadid.workbook.interaction.sync.destination.{SyncDestination, SyncDestinationHistory}
import it.evadid.workbook.interaction.variable.*
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.{read, write}
import it.evadid.core.util.io.serializer.DefaultSerializer.given

class SyncPackageSpec extends FunSuite {
  private def DataEntryReadFromServer[K, D](key: K, value: D, time: java.time.LocalDateTime): DataEntryToSync[K, D] =
    DataEntryToSync(key, value, time, SyncDirection.SERVER_TO_CLIENT)
  private def DataEntryToWriteToServer[K, D](key: K, value: D, time: java.time.LocalDateTime): DataEntryToSync[K, D] =
    DataEntryToSync(key, value, time, SyncDirection.CLIENT_TO_SERVER)

  private given ExecutionContext = ExecutionContext.global
  private val logger = SyncLogger(Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing))
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)
  private val usage = UsageContext("program", "scenario", "user")
  private val context = usage.toSyncContext("answer_history")

  private def event(value: String, importance: UpdateImportance, seconds: Int) =
    InteractionVariableState(value, importance, time.plusSeconds(seconds))

  private val major = event("major", UpdateImportance.MAJOR, 1)
  private val minor = event("minor", UpdateImportance.MINOR, 2)
  private val temporary = event("temporary", UpdateImportance.TEMPORARY, 3)
  private val default = event("default", UpdateImportance.DEFAULT, 4)
  private val history = InteractionVariableHistory[String, Any, Any](Set(major, minor, temporary, default), Map.empty, Map.empty)
  private val serialized = write(InteractionVariableHistory[String, Any, Any](Set(major, minor, temporary), Map.empty, Map.empty))

  private class Destination extends SyncDestinationHistory {
    var fetched: Future[FetchResponse[SyncContext, String]] =
      Future.successful(FetchResponse(time, Set(DataEntryReadFromServer(context, serialized, time))))
    var stored = List.empty[(SyncContext, String)]
    var fetchArguments = List.empty[UsageContext]
    var writeResult = Future.successful(SyncSuccess(1, 2, 3, time))
    var synchronousReadError: Option[Throwable] = None
    var synchronousWriteError: Option[Throwable] = None
    override def shouldBePersistant(): Boolean = true
    override def isLocal: Boolean = true
    override def fetchAll(logger: SyncLogger, context: UsageContext): Future[FetchResponse[SyncContext, String]] = {
      synchronousReadError.foreach(error => throw error)
      fetchArguments = fetchArguments :+ context
      fetched
    }
    override def storeTo(logger: SyncLogger, context: SyncContext, value: String): Future[SyncSuccess] = {
      synchronousWriteError.foreach(error => throw error)
      stored = stored :+ ((context, value))
      writeResult
    }
    override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
    override protected def deserializeFromConstructorLikeString(from: ConstructorLikeReadResult): Option[SyncDestination] = None
    override protected def serializeToConstructorLikeString(): ConstructorLikeReadResult = ConstructorLikeReadResult("TestDestination", Nil)
  }

  private def location(destination: Destination = new Destination, strategy: SyncStrategy = SyncStrategy.SYNC_EVERYTHING) =
    SyncInformation(destination, strategy).forContext(usage)

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
      assertEquals(history.filterWithStrategy(strategy).events, events)
      assertEquals(strategy.selectEventsToSync(Set.empty[InteractionVariableState[String]]), Set.empty[InteractionVariableState[String]])
    })
    val defaults = InteractionVariableHistory[String, Any, Any](Set(default), Map.empty, Map.empty)
    assertEquals(SyncStrategy.SYNC_LAST.selectEventsToSync(defaults.events), Set.empty[InteractionVariableState[String]])
    assertEquals(SyncStrategy.SYNC_LAST_AND_MAJOR.selectEventsToSync(defaults.events), Set.empty[InteractionVariableState[String]])
    assertEquals(SyncStrategy.SYNC_MAJOR.toString, "SYNC_ONLY(MAJOR)")
  }

  test("history storage retains the variable key, payload and latest timestamp") {
    val stored = HistoryJson.stored(context.keyForSerialisation, serialized)
    assertEquals(ujson.read(stored)("keyForSerialisation").str, context.keyForSerialisation)
    assertEquals(HistoryJson.payload(stored), ujson.read(serialized))
    assertEquals(HistoryJson.lastTimestamp(stored), Some(temporary.timestamp))
    assertEquals(HistoryJson.lastTimestamp(write(InteractionVariableHistory.empty[String, Any, Any])), None)
    intercept[Exception](HistoryJson.payload("not JSON"))
  }

  test("SyncInformation binds a usage context and fetch responses retain opaque JSON") {
    val info = SyncInformation(new Destination, SyncStrategy.SYNC_LAST)
    assertEquals(info.forContext(usage).syncInformation, info)
    assertEquals(info.forContext(usage).usageContext, usage)
    val entries = Set(DataEntryReadFromServer(context, serialized, time.minusSeconds(1)))
    val response = FetchResponse(time, entries)
    assertEquals(response.timestampFetchResponse, time)
    assertEquals(response.fetchedValues, entries)
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
          assertEquals(destination.fetchArguments, List.fill(3)(usage))
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
      assert(destination.stored.forall(entry => entry._2 == serialized))
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
    assertEquals(entries.head.timestamp, major.timestamp)
    assertEquals(variable.deserializeHistory(entries.head.dataValue).events, Set(major))
    variable.resetLocalHistory()
    assertEquals(bound.dataToStore(variable), Nil)
    bound.informAboutContextSwitch().map(result => assertEquals(result.elementsRemoved, 0))
  }

  test("InteractionVariableSyncReport merges histories and partitions synced locations at timestamp boundaries") {
    val synced = location()
    val stale = location()
    val missing = location()
    val local = InteractionVariableHistory[String, Any, Any](Set(temporary), Map.empty, Map.empty)
    val remote = InteractionVariableHistory[String, Any, Any](Set(major, minor), Map.empty, Map.empty)
    val statuses = Map(
      synced -> SyncStatus(Some(temporary.timestamp), context, Some(DataEntryReadFromServer(context, remote, time))),
      stale -> SyncStatus(Some(temporary.timestamp.minusNanos(1)), context, Some(DataEntryReadFromServer(context, remote, time))),
      missing -> SyncStatus[SyncContext, InteractionVariableHistory[String, Any, Any]](None, context, None))
    val report = InteractionVariableSyncReport(local, statuses, List(synced, stale, missing))
    assertEquals(report.lastRemoteStates.keySet, Set(synced, stale))
    assertEquals(report.lastRemoteStates.get(synced), Some(minor))
    assertEquals(report.lastRemoteStates.get(missing), None)
    assertEquals(report.latestStateIsSyncedTo, Set(synced))
    assertEquals(report.latestStateIsNotSyncedTo, Set(stale, missing))
    assertEquals(report.allRemoteStates, Set(major, minor))
    assertEquals(report.allStatesEverywhere, Set(major, minor, temporary))
    val emptyLocal = report.copy(curLocalHistory = InteractionVariableHistory.empty[String, Any, Any])
    assertEquals(emptyLocal.latestStateIsSyncedTo, Set(synced, stale, missing))
  }

  test("SyncCache retains JSON for the owner to decode and isolates context keys") {
    val cache = SyncCache(time, usage, Map(context -> serialized))
    val variable = TextInteraction("answer").interactionVariable
    assertEquals(variable.deserializeHistory(cache.contextMaps(context)).events, Set(major, minor, temporary))
    assertEquals(cache.contextMaps.get(context.copy(userId = "other")), None)
    assertEquals(read[SyncCache](write(cache)), cache)
  }
}
