package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{LanguageMapContentId, AppLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.sql.DatabaseHistoryFormat.BlobFormat
import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structureElements.WorkbookSection.WorkbookSectionMetadata
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.AnswerChangedEvent
import it.evadid.workbook.meta.ExecutionContextInformation
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import upickle.default.*
import java.time.LocalDateTime

/** Exercise the real controller, shared buffers and HTTP adapter together. */
class RemoteInteractionCacheControlSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val at = LocalDateTime.of(2026, 1, 1, 12, 0)
  private val global = js.Dynamic.global.globalThis

  private class Fixture {
    val requests = Var(Vector.empty[ExecutionCommand])
    val failBlobReads = Var(false)
    val failBlobReset = Var(false)
    val afterStore = Var[() => Unit](() => ())
    val elements = Seq(TextInteraction("first"), TextInteraction("second"))
    val user = AllUserInfo.createNewUser("Student", "fixture@example.invalid", UserConfig(Seq(
      SyncInformation(defaultDatabase, EventSyncStrategy.SYNC_EVERYTHING),
      SyncInformation(defaultBlobDatabase, EventSyncStrategy.SYNC_EVERYTHING)), true))
    val section = WorkbookSection("section", WorkbookSectionMetadata(LanguageMapContentId("fixture/section")), elements)
    val workbook = Workbook("workbook", WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("fixture/title"), Seq(AppLanguage.English)), Seq(section))
    val info = FullInfo(HomepageInfo(AppLanguage.English,
      Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)), Some(user),
      AllDisplayInfo(false, None), LanguageMapStorage.empty))
    private val saved = Var(Map.empty[(it.evadid.sql.DatabaseHistoryFormat, SyncContext), InteractionHistoryJson])

    private def history(text: String, index: Int) = InteractionHistoryJson.fromHistory(TextInteractionFormat,
      InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(
        AnswerChangedEvent[TextInteractionFormat.type](text, at.plusSeconds(index), UpdateImportance.MAJOR)))
    Seq(defaultDatabase.format, defaultBlobDatabase.format).foreach { format =>
      elements.zipWithIndex.foreach { (element, index) =>
        val key = info.homepageInfoNow().toContext.toSyncContext(element.interactionVariable.keyForSerialization)
        saved.update(_ + ((format -> key) -> history(s"saved-$index", index)))
      }
    }

    private def response(command: ExecutionCommand): ExecutionClientResponse = {
      requests.update(_ :+ command)
      val now = LocalDateTime.now()
      val times = ExecutionClientResponseTimestamps(now, now, now)
      command.name match {
        case name if name == fetchEventsFromDbCommand.name =>
          val request = read[FetchEventsFromDbRequest](command.json)
          if failBlobReads.now() && request.databaseFormat == BlobFormat then
            ExecutionClientResponse.forError(times, SerializedException("blob destination offline"), command)
          else {
            val values = saved.now().collect {
              case ((format, key), value) if format == request.databaseFormat && key.toUsageContext == request.usageContext &&
                (request.keys.isEmpty || request.keys.contains(key.keyForSerialisation)) =>
                key -> value.cleanWithStrategy(request.strategy).between(request.starting, request.until)
            }
            ExecutionClientResponse.forValue(times, DbFetchResponse(values), command)
          }
        case name if name == bulkStoreToDbCommand.name =>
          val request = read[BulkStoreToDbRequest](command.json)
          request.entries.foreach(entry => saved.update { previous =>
            val key = request.databaseFormat -> entry.syncContext
            previous.updated(key, previous.get(key).fold(entry.history)(_.merge(entry.history)))
          })
          afterStore.now()()
          ExecutionClientResponse.forValue(times, SyncSuccess.emptyNow(), command)
        case name if name == resetUserHistoriesDbCommand.name =>
          val request = read[DeleteInDbRequest](command.json)
          assert(request.allWorkbooks)
          if failBlobReset.now() && request.databaseFormat == BlobFormat then
            return ExecutionClientResponse.forError(times, SerializedException("reset destination offline"), command)
          saved.update(_.filterNot { case ((format, key), _) =>
            format == request.databaseFormat && key.programId == request.usageContext.programId && key.userId == request.usageContext.userId
          })
          ExecutionClientResponse.forValue(times, SyncSuccess.emptyNow(), command)
        case other => throw IllegalArgumentException(s"Unexpected request: $other")
      }
    }

    def withHttp[A](run: => Future[A]): Future[A] = {
      val oldFetch = global.selectDynamic("fetch")
      val oldCompression = global.selectDynamic("CompressionStream")
      val fetch: js.Function2[String, js.Dynamic, js.Promise[js.Dynamic]] = (_, init) => {
        val command = read[ExecutionCommand](init.body.asInstanceOf[String])
        val text = write(response(command))
        val body: js.Function0[js.Promise[String]] = () => js.Promise.resolve(text)
        js.Promise.resolve(js.Dynamic.literal(status = 200, statusText = "OK", ok = true, text = body))
      }
      global.updateDynamic("CompressionStream")(js.undefined)
      global.updateDynamic("fetch")(fetch)
      scala.util.Try(run).fold(Future.failed, identity).andThen { case _ =>
        global.updateDynamic("fetch")(oldFetch)
        global.updateDynamic("CompressionStream")(oldCompression)
      }
    }
  }

  test("one phase batches all interaction keys, and subsequent polls use inclusive incremental windows") {
    val fixture = new Fixture
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        val firstReads = fixture.requests.now().filter(_.name == fetchEventsFromDbCommand.name).map(command => read[FetchEventsFromDbRequest](command.json))
        assertEquals(firstReads.size, 2)
        assert(firstReads.forall(request => request.keys.size == 2 && request.starting == LocalDateTime.MIN))
        assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("saved-0", "saved-1"))
        fixture.requests.set(Vector.empty)
        fixture.info.syncControl.ensureSynced()
      }.flatMap { _ =>
        val reads = fixture.requests.now().filter(_.name == fetchEventsFromDbCommand.name).map(command => read[FetchEventsFromDbRequest](command.json))
        assertEquals(reads.size, 2)
        assert(reads.forall(_.starting == at))
        fixture.requests.set(Vector.empty)
        fixture.elements.zipWithIndex.foreach((element, index) => element.interactionVariable.setStateFromUserInteraction(
          s"edited-$index", UpdateImportance.MAJOR, context = ExecutionContextInformation.default))
        fixture.info.syncControl.storeAll(true)
      }.map { _ =>
        val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name).map(command => read[BulkStoreToDbRequest](command.json))
        assertEquals(writes.size, 2)
        assert(writes.forall(_.histories.size == 2))
      }
    }
  }

  test("an unavailable read destination does not block writes to a healthy destination") {
    val fixture = new Fixture
    fixture.failBlobReads.set(true)
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().failed.map { _ =>
        val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name).map(command => read[BulkStoreToDbRequest](command.json))
        assert(writes.exists(_.databaseFormat == defaultDatabase.format))
        assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("saved-0", "saved-1"))
      }
    }
  }

  test("reopening a reused workbook for another user clears its answers and feedback ownership") {
    val fixture = new Fixture
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        val previousContext = fixture.info.workbookContextInformation
        val workbook = fixture.info.homepageInfoNow().workbookInfo
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
        val other = AllUserInfo.createNewUser("Other student", "other@example.invalid", fixture.user.config)
        fixture.info.invalidateFeedbackSession()
        fixture.info.homepageInfoState.update(_.copy(userInfo = Some(other)))
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = workbook))
        assert(!previousContext.isCurrent)
        assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("", ""))
        fixture.requests.set(Vector.empty)
        fixture.info.syncControl.storeAll(true).map { _ =>
          val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name).map(command => read[BulkStoreToDbRequest](command.json))
          assert(writes.nonEmpty)
          assert(writes.flatMap(_.syncContexts).forall(_.userId == other.user.personId))
          assert(writes.flatMap(_.histories).forall(_.typedAs(TextInteractionFormat).allStates.forall(_.newState.isEmpty)))
        }
      }
    }
  }

  test("full reset resolves configured destinations even when the workbook catalogue is open") {
    val fixture = new Fixture
    fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
    fixture.withHttp {
      fixture.info.syncControl.resetAllData().map { _ =>
        val resets = fixture.requests.now().filter(_.name == resetUserHistoriesDbCommand.name)
          .map(command => read[DeleteInDbRequest](command.json))
        assertEquals(resets.size, 2)
        assert(resets.forall(request => request.allWorkbooks && request.usageContext.userId == fixture.user.user.personId))
        assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Completed)
      }
    }
  }

  test("leaving a workbook flushes its queued writes instead of abandoning the old handlers") {
    val fixture = new Fixture
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        fixture.requests.set(Vector.empty)
        val remaining = Var(2)
        val finished = Promise[Unit]()
        fixture.afterStore.set(() => {
          remaining.update(_ - 1)
          if remaining.now() == 0 then finished.trySuccess(())
        })
        fixture.elements.foreach(element => element.interactionVariable.setStateFromUserInteraction(
          "last edit before navigation", UpdateImportance.MAJOR, context = fixture.info.workbookContextInformation))
        assert(!finished.isCompleted)
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
        finished.future.map { _ =>
          val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name)
          assertEquals(writes.size, 2)
          assert(writes.forall(command => read[BulkStoreToDbRequest](command.json).histories.size == 2))
        }
      }
    }
  }

  test("a failed catalogue reset does not leave the next user paused") {
    val fixture = new Fixture
    fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
    fixture.failBlobReset.set(true)
    fixture.withHttp {
      fixture.info.syncControl.resetAllData().failed.map { _ =>
        assert(fixture.info.syncControl.resetState.now().isInstanceOf[SyncResetState.Failed])
        fixture.info.invalidateFeedbackSession()
        val other = AllUserInfo.createNewUser("Next user", "next@example.invalid", fixture.user.config)
        fixture.info.homepageInfoState.update(_.copy(userInfo = Some(other)))
        assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Idle)
      }
    }
  }

  test("captured execution contexts expire when the user session is invalidated") {
    val fixture = new Fixture
    val captured = fixture.info.workbookContextInformation
    assert(captured.isCurrent)
    fixture.info.invalidateFeedbackSession()
    assert(!captured.isCurrent)
    assert(fixture.info.workbookContextInformation.isCurrent)
  }
}
