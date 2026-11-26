package it.evadid.homepage.control.change

import com.raquo.laminar.api.L.*
import com.raquo.airstream.ownership.ManualOwner
import it.evadid.core.datastructures.language.{LanguageMapContentId, AppLanguage}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig, UserCredentials, UserTokenInfo}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.distribution.formats.ExecutionClientResponse
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.info.interaction.SyncReporter
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.syncDestination.InMemoryHistoryDatabase
import it.evadid.sql.{DatabaseConfigWithCredentials, DatabaseHistoryFormat}
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
import scala.scalajs.js.JSConverters.*
import upickle.default.*
import java.time.LocalDateTime
import org.scalajs.dom

/** Exercise the real controller, shared buffers and HTTP adapter together. */
class RemoteInteractionCacheControlSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private val at = LocalDateTime.of(2026, 1, 1, 12, 0)
  private val global = js.Dynamic.global.globalThis

  private def signed(user: AllUserInfo, value: String): SignedToken =
    SignedToken(UserTokenInfo(user.user, at, at.plusYears(2)), value)
  /** Backend synchronization requires a signed-in account. */
  private def signedIn(user: AllUserInfo): AllUserInfo =
    user.copy(credentials = user.credentials.copy(signedBackendToken = Some(signed(user, s"${user.user.id}-token"))))

  /** Freeze both destination responses at a known point; no sleeps or scheduler assumptions. */
  private class ResponseGate {
    private val count = Var(0)
    private val reached = Promise[Unit]()
    private val released = Promise[Unit]()
    def arrived: Future[Unit] = reached.future
    def hold(): Future[Unit] = {
      count.update(_ + 1)
      if count.now() == 2 then reached.trySuccess(())
      released.future
    }
    def release(): Unit = { released.trySuccess(()); () }
  }

  private class Fixture {
    val requests = Var(Vector.empty[ExecutionCommand])
    val failBlobReads = Var(false)
    val failBlobReset = Var(false)
    val failStores = Var(false)
    val afterStore = Var[() => Unit](() => ())
    val beforeResponse = Var[ExecutionCommand => Future[Unit]](_ => Future.unit)
    val authorization = Var(Vector.empty[(ExecutionCommand, String)])
    val elements = Seq(TextInteraction("first"), TextInteraction("second"))
    val user = signedIn(AllUserInfo.createNewUser("Student", "fixture@example.invalid", UserConfig(Seq(
      SyncInformation(defaultDatabase, EventSyncStrategy.SYNC_EVERYTHING),
      SyncInformation(defaultBlobDatabase, EventSyncStrategy.SYNC_EVERYTHING)), true)))
    val section = WorkbookSection("section", WorkbookSectionMetadata(LanguageMapContentId("fixture/section")), elements)
    val workbook = Workbook("workbook", WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("fixture/title"), Seq(AppLanguage.English)), Seq(section))
    val info = FullInfo(HomepageInfo(AppLanguage.English,
      Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)), Some(user),
      AllDisplayInfo(false, None), LanguageMapStorage.empty))
    val database = InMemoryHistoryDatabase()

    private def history(text: String, index: Int) = InteractionHistoryJson.fromHistory(TextInteractionFormat,
      InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(
        AnswerChangedEvent[TextInteractionFormat.type](text, at.plusSeconds(index), UpdateImportance.MAJOR)))
    Seq(defaultDatabase.format, defaultBlobDatabase.format).foreach { format =>
      elements.zipWithIndex.foreach { (element, index) =>
        val key = info.homepageInfoNow().toContext.toSyncContext(element.interactionVariable.keyForSerialization)
        database.rows.update(_ + ((format -> key) -> history(s"saved-$index", index)))
      }
    }

    def seed(context: UsageContext, text: String, timestamp: LocalDateTime): Unit = {
      val value = InteractionHistoryJson.fromHistory(TextInteractionFormat,
        InteractionVariableHistory.empty[TextInteractionFormat.type].addEvent(
          AnswerChangedEvent[TextInteractionFormat.type](text, timestamp, UpdateImportance.MAJOR)))
      Seq(defaultDatabase.format, defaultBlobDatabase.format).foreach { format =>
        elements.foreach { element =>
          val key = context.toSyncContext(element.interactionVariable.keyForSerialization)
          database.rows.update(_ + ((format -> key) -> value))
        }
      }
    }

    def anotherWorkbook(id: String): (AllWorkbookInfo, Seq[TextInteraction]) = {
      // Deliberately reuse interaction IDs so the test must isolate the whole usage context.
      val interactions = Seq(TextInteraction("first"), TextInteraction("second"))
      val otherSection = section.copy(sectionContent = interactions)
      val other = workbook.copy(elementId = id, sections = Seq(otherSection))
      AllWorkbookInfo(other, WorkbookConfig(Some(otherSection)), Map.empty) -> interactions
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
          else ExecutionClientResponse.forValue(times, database.fetch(request), command)
        case name if name == bulkStoreToDbCommand.name =>
          val request = read[BulkStoreToDbRequest](command.json)
          if failStores.now() then
            return ExecutionClientResponse.forError(times, SerializedException("retired write failed"), command)
          database.store(request.databaseFormat, request.entries.map(entry => entry.syncContext -> entry.history))
          afterStore.now()()
          ExecutionClientResponse.forValue(times, SyncSuccess.emptyNow(), command)
        case name if name == resetUserHistoriesDbCommand.name =>
          val request = read[DeleteInDbRequest](command.json)
          assert(request.allWorkbooks)
          if failBlobReset.now() && request.databaseFormat == BlobFormat then
            return ExecutionClientResponse.forError(times, SerializedException("reset destination offline"), command)
          database.delete(request)
          ExecutionClientResponse.forValue(times, SyncSuccess.emptyNow(), command)
        case other => throw IllegalArgumentException(s"Unexpected request: $other")
      }
    }

    def withHttp[A](run: => Future[A]): Future[A] = {
      val oldFetch = global.selectDynamic("fetch")
      val oldCompression = global.selectDynamic("CompressionStream")
      val fetch: js.Function2[String, js.Dynamic, js.Promise[js.Dynamic]] = (_, init) => {
        val command = read[ExecutionCommand](init.body.asInstanceOf[String])
        authorization.update(_ :+ (command -> init.headers.selectDynamic("Authorization").asInstanceOf[String]))
        val text = write(response(command))
        beforeResponse.now()(command).map { _ =>
          val body: js.Function0[js.Promise[String]] = () => js.Promise.resolve(text)
          js.Dynamic.literal(status = 200, statusText = "OK", ok = true, text = body)
        }.toJSPromise
      }
      global.updateDynamic("CompressionStream")(js.undefined)
      global.updateDynamic("fetch")(fetch)
      scala.util.Try(run).fold(Future.failed, identity).andThen { case _ =>
        global.updateDynamic("fetch")(oldFetch)
        global.updateDynamic("CompressionStream")(oldCompression)
      }
    }
  }

  /** Restored histories schedule their own stores after the current task; this lets them run and flushes them,
    * so later request windows hold only the requests a test causes.
    */
  private def settled(fixture: Fixture): Future[Unit] = {
    val due = Promise[Unit]()
    js.timers.setTimeout(0)(due.success(()))
    due.future.flatMap(_ => fixture.info.syncControl.storeAll(true))
  }

  /** Writes are confirmed only by reading them back: the periodic synchronization's next read. */
  private def synchronizedAndRead(fixture: Fixture): Future[Unit] =
    fixture.info.syncControl.ensureSynced().flatMap(_ => fixture.info.syncControl.fetchAll(true))

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
    fixture.elements.head.interactionVariable.setStateFromUserInteraction("offline edit", UpdateImportance.MAJOR,
      context = ExecutionContextInformation.default)
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().failed.map { _ =>
        val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name).map(command => read[BulkStoreToDbRequest](command.json))
        assert(writes.exists(request => request.databaseFormat == defaultDatabase.format &&
          request.histories.exists(_.typedAs(TextInteractionFormat).allStates.exists(_.newState == "offline edit"))))
        assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("offline edit", "saved-1"))
      }
    }
  }

  test("reopening a reused workbook for another user clears its answers and feedback ownership") {
    val fixture = new Fixture
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap(_ => settled(fixture)).flatMap { _ =>
        val previousContext = fixture.info.workbookContextInformation
        val workbook = fixture.info.homepageInfoNow().workbookInfo
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
        val other = signedIn(AllUserInfo.createNewUser("Other student", "other@example.invalid", fixture.user.config))
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

  /** Each event as a closing page fires it: `visibilitychange` at the document, bubbling to the window, then `pagehide`. */
  for (target, kind) <- List((() => dom.document, "visibilitychange"), (() => dom.window, "pagehide")) do
    test(s"$kind sends edits at once whose stores are only scheduled and buffered") {
      val fixture = new Fixture
      fixture.withHttp {
        fixture.info.syncControl.ensureSynced().flatMap(_ => settled(fixture)).flatMap { _ =>
          val written = Promise[Unit]()
          fixture.afterStore.set(() => written.trySuccess(()))
          fixture.elements.foreach(element => element.interactionVariable.setStateFromUserInteraction(
            s"typed before $kind", UpdateImportance.MAJOR, context = fixture.info.workbookContextInformation))
          // The buffered destinations would hold these writes for 30 seconds.
          target().dispatchEvent(js.Dynamic.newInstance(EditorDomSuite.window.Event)(kind,
            js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event])
          written.future.map { _ =>
            val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name)
              .map(command => read[BulkStoreToDbRequest](command.json))
            assert(writes.exists(_.histories.exists(_.typedAs(TextInteractionFormat).allStates.exists(_.newState == s"typed before $kind"))))
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
        val other = signedIn(AllUserInfo.createNewUser("Next user", "next@example.invalid", fixture.user.config))
        fixture.info.homepageInfoState.update(_.copy(userInfo = Some(other)))
        assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Idle)
      }
    }
  }

  test("partial reset invalidates pre-reset synchronization confirmations") {
    val fixture = new Fixture
    fixture.withHttp {
      synchronizedAndRead(fixture).flatMap { _ =>
        val report = fixture.info.syncControl.createReportFor(fixture.elements.head).observe(using unsafeWindowOwner)
        val sharedReporter = fixture.info.signals.reportersFor(fixture.elements.head).sync
        val summary = sharedReporter.report.observe(using unsafeWindowOwner)
        assertEquals(report.now().get.destinationsWithSufficientWrites.size, 2)
        assertEquals(summary.now().confirmed, 2)
        fixture.failBlobReset.set(true)
        fixture.info.syncControl.resetAllData().failed.map { _ =>
          assertEquals(report.now().get.destinationsWithSufficientWrites.size, 0)
          assertEquals(summary.now().confirmed, 0)
        }
      }
    }
  }

  test("under SYNC_MAJOR a temporary answer stays unsynced until an explicit sync commits, writes and reads it") {
    val fixture = new Fixture
    val owner = new ManualOwner
    val majorOnly = fixture.user.config.copy(syncDestinations = Seq(
      SyncInformation(defaultDatabase, EventSyncStrategy.SYNC_MAJOR), SyncInformation(defaultBlobDatabase, EventSyncStrategy.SYNC_MAJOR)))
    fixture.info.homepageInfoState.update(_.copy(userInfo = Some(fixture.user.copy(config = majorOnly))))
    val variable = fixture.elements.head.interactionVariable
    def written(answer: String) = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name)
      .flatMap(command => read[BulkStoreToDbRequest](command.json).histories)
      .exists(_.typedAs(TextInteractionFormat).allStates.exists(_.newState == answer))
    fixture.withHttp {
      synchronizedAndRead(fixture).flatMap(_ => settled(fixture)).flatMap { _ =>
        val summary = fixture.info.signals.reportersFor(fixture.elements.head).sync.report.observe(using owner)
        variable.setStateFromUserInteraction("half-typed", UpdateImportance.TEMPORARY, context = fixture.info.workbookContextInformation)
        settled(fixture).flatMap(_ => fixture.info.syncControl.fetchAll(true)).flatMap { _ =>
          assert(!written("half-typed"), "a temporary answer is not sent to SYNC_MAJOR destinations")
          assertEquals(summary.now().confirmed, 0, "the latest answer is not synchronized")
          variable.commitCurrentState(fixture.info.workbookContextInformation)
          fixture.info.syncControl.synchronize(variable)
        }.map { _ =>
          assert(written("half-typed"))
          assertEquals(variable.currentHistory.historyStates.lastStateOption.map(_.updateImportance), Some(UpdateImportance.MAJOR))
          assertEquals(summary.now().confirmed, 2)
        }
      }.andThen { case _ => owner.killSubscriptions() }
    }
  }

  test("an edit inside an Airstream transaction stores its new history at both destinations") {
    val fixture = new Fixture
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap(_ => settled(fixture)).flatMap { _ =>
        val owner = new ManualOwner
        val editing = Var(0)
        val finished = Promise[Unit]()
        editing.signal.changes.foreach { _ =>
          fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction("transaction edit", UpdateImportance.MAJOR,
            context = fixture.info.workbookContextInformation))
          fixture.info.syncControl.storeAll(true).onComplete(finished.tryComplete)
        }(using owner)
        fixture.requests.set(Vector.empty)
        editing.set(1)
        finished.future.map { _ =>
          val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name)
            .map(command => read[BulkStoreToDbRequest](command.json))
          assertEquals(writes.size, 2)
          assert(writes.forall(_.histories.forall(_.typedAs(TextInteractionFormat).allStates.exists(_.newState == "transaction edit"))))
        }.andThen { case _ => owner.killSubscriptions() }
      }
    }
  }

  /** Event ids each destination format received for each interaction key, from the recorded store commands. */
  private def sentEventIds(fixture: Fixture): Map[(DatabaseHistoryFormat, String), Set[String]] =
    fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name).map(command => read[BulkStoreToDbRequest](command.json))
      .flatMap(request => request.syncContexts.zip(request.histories).map((key, history) =>
        (request.databaseFormat, key.keyForSerialisation) -> history.typedAs(TextInteractionFormat).events.values.map(_.eventId)))
      .groupMapReduce(_._1)(_._2)(_ ++ _)

  /** Event ids each destination format holds for each interaction key. */
  private def heldEventIds(fixture: Fixture): Map[(DatabaseHistoryFormat, String), Set[String]] =
    fixture.database.rows.now().map { case ((format, key), history) =>
      (format, key.keyForSerialisation) -> history.typedAs(TextInteractionFormat).events.values.map(_.eventId)
    }

  /** Every event of every local history, as each destination's strategy selects it, was either sent to that
    * destination in some store command or already held there (it was fetched from it) before the commands.
    */
  private def assertEveryLocalEventSent(fixture: Fixture, heldBefore: Map[(DatabaseHistoryFormat, String), Set[String]]): Unit = {
    val sent = sentEventIds(fixture)
    for
      information <- fixture.user.config.syncDestinations
      element <- fixture.elements
    do {
      val format = information.syncDestination.asInstanceOf[SyncDestinationBasedOnHistoryDatabase].format
      val key = element.interactionVariable.keyForSerialization
      val local = element.interactionVariable.currentHistory.cleanWithStrategy(information.syncStrategy).events.values
      val known = sent.getOrElse(format -> key, Set.empty) ++ heldBefore.getOrElse(format -> key, Set.empty)
      val missing = local.filterNot(event => known.contains(event.eventId))
      assertEquals(missing.map(event => (event.associatedState, event.updateImportance)), Set.empty, s"$format $key")
    }
  }

  test("every event of every local history reaches at least one store command of each destination") {
    val fixture = new Fixture
    fixture.withHttp {
      val heldBefore = heldEventIds(fixture)
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        val context = fixture.info.workbookContextInformation
        for
          (element, index) <- fixture.elements.zipWithIndex
          (value, importance) <- List("draft" -> UpdateImportance.MINOR, "typing" -> UpdateImportance.TEMPORARY,
            "submitted" -> UpdateImportance.MAJOR, "revised" -> UpdateImportance.MINOR)
        do element.interactionVariable.setStateFromUserInteraction(s"$value-$index", importance, context = context)
        fixture.info.syncControl.storeAll(true)
      }.map { _ =>
        assertEveryLocalEventSent(fixture, heldBefore)
        assert(fixture.elements.forall(_.interactionVariable.currentHistory.allStates.exists(_.newState.startsWith("revised"))))
      }
    }
  }

  test("an edit made while a store is in flight is sent by a later store command") {
    val fixture = new Fixture
    val gate = Promise[Unit]()
    fixture.withHttp {
      val heldBefore = heldEventIds(fixture)
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        val context = fixture.info.workbookContextInformation
        fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction("before", UpdateImportance.MAJOR, context = context))
        fixture.beforeResponse.set(command => if command.name == bulkStoreToDbCommand.name then gate.future else Future.unit)
        val first = fixture.info.syncControl.storeAll(true)
        fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction("during", UpdateImportance.MAJOR, context = context))
        gate.success(())
        first.flatMap { _ =>
          fixture.beforeResponse.set(_ => Future.unit)
          fixture.info.syncControl.storeAll(true)
        }
      }.map(_ => assertEveryLocalEventSent(fixture, heldBefore))
    }
  }

  test("answers restored from a session file are sent to every destination") {
    val fixture = new Fixture
    fixture.withHttp {
      val heldBefore = heldEventIds(fixture)
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        // The same workbook in the session's own page, answered there.
        val (studied, answered) = fixture.anotherWorkbook(fixture.workbook.elementId)
        answered.zipWithIndex.foreach((interaction, index) => interaction.interactionVariable.setStateFromUserInteraction(
          s"from the session $index", UpdateImportance.MAJOR, context = ExecutionContextInformation.default))
        val session = it.evadid.homepage.control.info.SessionData.createSessionData(fixture.info.homepageInfoNow()
          .copy(workbookInfo = Some(studied))).get
        val sessionEvents = answered.map(_.interactionVariable.currentHistory.events.values.map(_.eventId)).reduce(_ ++ _)
        fixture.info.userSessionControl.continueWithSession(session).flatMap(_ => fixture.info.syncControl.storeAll(true)).map { _ =>
          assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("from the session 0", "from the session 1"))
          assertEveryLocalEventSent(fixture, heldBefore)
          val sent = sentEventIds(fixture)
          List(defaultDatabase.format, defaultBlobDatabase.format).foreach { format =>
            val received = fixture.elements.flatMap(element => sent.getOrElse(format -> element.interactionVariable.keyForSerialization, Set.empty)).toSet
            assert(sessionEvents.subsetOf(received), s"$format received the session's answers")
          }
        }
      }
    }
  }

  test("partial reset remains paused across navigation and credential replacement, then retries") {
    val fixture = new Fixture
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        fixture.failBlobReset.set(true)
        fixture.info.syncControl.resetAllData().failed.flatMap { _ =>
          val workbook = fixture.info.homepageInfoNow().workbookInfo
          fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
          assert(fixture.info.syncControl.resetState.now().isInstanceOf[SyncResetState.Failed])
          fixture.info.homepageInfoState.update(_.copy(workbookInfo = workbook,
            userInfo = Some(fixture.user.copy(credentials = fixture.user.credentials.copy(singleAccessToken = Some(SingleAccessToken("replacement")))))))
          fixture.requests.set(Vector.empty)
          fixture.info.syncControl.ensureSynced().flatMap { _ =>
            assert(fixture.info.syncControl.resetState.now().isInstanceOf[SyncResetState.Failed])
            assertEquals(fixture.requests.now(), Vector.empty)
            fixture.failBlobReset.set(false)
            fixture.info.syncControl.resetAllData().map { _ =>
              assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Completed)
              assertEquals(fixture.requests.now().count(_.name == resetUserHistoriesDbCommand.name), 2)
            }
          }
        }
      }
    }
  }

  test("running reset follows its user across navigation without allowing synchronization") {
    val fixture = new Fixture
    val release = Promise[Unit]()
    val deleting = Promise[Unit]()
    fixture.beforeResponse.set(command =>
      if command.name == resetUserHistoriesDbCommand.name then {
        deleting.trySuccess(())
        release.future
      } else Future.unit)
    fixture.withHttp {
      val reset = fixture.info.syncControl.resetAllData()
      deleting.future.flatMap { _ =>
        val workbook = fixture.info.homepageInfoNow().workbookInfo
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = workbook))
        assert(fixture.info.syncControl.resetState.now().isInstanceOf[SyncResetState.Running])
        assert(fixture.info.syncControl.resetAllData() eq reset)
        fixture.requests.set(Vector.empty)
        fixture.info.syncControl.storeAll(true).flatMap { _ =>
          assertEquals(fixture.requests.now(), Vector.empty)
          release.success(())
          reset.map(_ => assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Completed))
        }
      }.andThen { case _ => release.trySuccess(()) }
    }
  }

  test("a replacement binding syncs before the old read completes, retaining each user's credentials") {
    val fixture = new Fixture
    val oldToken = signed(fixture.user, "old-session")
    fixture.info.homepageInfoState.update(_.copy(userInfo = Some(fixture.user.copy(credentials = UserCredentials(signedBackendToken = Some(oldToken))))))
    val oldRead = Promise[Unit]()
    val release = Promise[Unit]()
    fixture.beforeResponse.set(command => {
      if command.name == fetchEventsFromDbCommand.name && read[FetchEventsFromDbRequest](command.json).usageContext.userId == fixture.user.user.personId then {
        oldRead.trySuccess(())
        release.future
      } else Future.unit
    })
    fixture.withHttp {
      val departing = fixture.info.syncControl.ensureSynced().transform(result => scala.util.Success(result))
      oldRead.future.flatMap { _ =>
        val other = AllUserInfo.createNewUser("Next user", "replacement@example.invalid", fixture.user.config)
        val newToken = signed(other, "new-session")
        fixture.info.userSessionControl.changeUser(Some(other.copy(credentials = UserCredentials(signedBackendToken = Some(newToken))))).flatMap { _ =>
          fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction("new user's answer", UpdateImportance.MAJOR,
            context = fixture.info.workbookContextInformation))
          fixture.info.syncControl.ensureSynced()
        }.flatMap { _ =>
          assert(departing.value.forall(_.get.isFailure))
          assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("new user's answer", "new user's answer"))
          val replacementHistory = fixture.elements.map(_.interactionVariable.currentHistory)
          release.success(())
          departing.map { _ =>
            assertEquals(fixture.elements.map(_.interactionVariable.currentHistory), replacementHistory)
            assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq("new user's answer", "new user's answer"))
            val writes = fixture.authorization.now().collect { case (command, token) if command.name == bulkStoreToDbCommand.name =>
              read[BulkStoreToDbRequest](command.json) -> token
            }
            val oldWrites = writes.filter(_._1.syncContexts.forall(_.userId == fixture.user.user.personId))
            val newWrites = writes.filter(_._1.syncContexts.forall(_.userId == other.user.personId))
            assert(oldWrites.nonEmpty && newWrites.nonEmpty)
            assert(oldWrites.forall(_._2 == oldToken.toJson))
            assert(newWrites.forall(_._2 == newToken.toJson))
            assert(oldWrites.flatMap(_._1.histories).forall(_.typedAs(TextInteractionFormat).allStates.forall(_.newState != "new user's answer")))
          }
        }
      }.andThen { case _ => release.trySuccess(()) }
    }
  }

  test("a late reset completion cannot change a replacement user's reset state") {
    val fixture = new Fixture
    fixture.info.homepageInfoState.update(_.copy(workbookInfo = None))
    fixture.failBlobReset.set(true)
    val release = Promise[Unit]()
    val deleting = Promise[Unit]()
    fixture.beforeResponse.set(command =>
      if command.name == resetUserHistoriesDbCommand.name then {
        deleting.trySuccess(())
        release.future
      } else Future.unit)
    fixture.withHttp {
      val reset = fixture.info.syncControl.resetAllData()
      deleting.future.flatMap { _ =>
        val other = signedIn(AllUserInfo.createNewUser("Next user", "next@example.invalid", fixture.user.config))
        fixture.info.invalidateFeedbackSession()
        fixture.info.homepageInfoState.update(_.copy(userInfo = Some(other)))
        assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Idle)
        release.success(())
        reset.failed.map(_ => assertEquals(fixture.info.syncControl.resetState.now(), SyncResetState.Idle))
      }.andThen { case _ => release.trySuccess(()) }
    }
  }

  test("workbook switching isolates colliding interaction IDs and ignores a detached read") {
    val fixture = new Fixture
    val gate = new ResponseGate
    val original = fixture.info.homepageInfoNow().toContext
    fixture.seed(original, "late original workbook", LocalDateTime.now().plusDays(1))
    val initial = fixture.elements.map(_.interactionVariable.currentHistory)
    fixture.beforeResponse.set(command =>
      if command.name == fetchEventsFromDbCommand.name && read[FetchEventsFromDbRequest](command.json).usageContext == original then gate.hold()
      else Future.unit)
    fixture.withHttp {
      val detached = fixture.info.syncControl.fetchAll(true).transform(result => scala.util.Success(result))
      gate.arrived.flatMap { _ =>
        val (other, interactions) = fixture.anotherWorkbook("other-workbook")
        assertEquals(interactions.map(_.elementId), fixture.elements.map(_.elementId))
        val context = original.copy(scenarioId = other.loadedWorkbook.workbookId)
        fixture.seed(context, "current workbook", LocalDateTime.now().plusDays(2))
        fixture.info.homepageInfoState.update(_.copy(workbookInfo = Some(other)))
        // An older edit in the new workbook: it must be written there, while the newer saved answer stays current.
        interactions.head.interactionVariable.setStateFromUserInteraction("older edit", UpdateImportance.MAJOR,
          LocalDateTime.now(), fixture.info.workbookContextInformation)
        fixture.info.syncControl.ensureSynced().flatMap { _ =>
          val current = interactions.map(_.interactionVariable.currentHistory)
          assertEquals(interactions.map(_.interactionVariable.currentValue), Seq.fill(2)("current workbook"))
          gate.release()
          detached.map { _ =>
            assertEquals(fixture.elements.map(_.interactionVariable.currentHistory), initial)
            assertEquals(interactions.map(_.interactionVariable.currentHistory), current)
            val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name)
              .flatMap(command => read[BulkStoreToDbRequest](command.json).entries).map(entry => entry.syncContext -> entry.history)
            assert(writes.exists(_._1.toUsageContext == context))
            // The detached binding may finish its own pending stores, but never with the detached read's answers.
            assert(writes.forall((key, history) => key.toUsageContext == context ||
              key.toUsageContext == original && !history.typedAs(TextInteractionFormat).allStates.exists(_.newState == "late original workbook")))
          }
        }
      }.andThen { case _ => gate.release() }
    }
  }

  Seq(false, true).foreach { retiredReadFails =>
    test(s"A to B to A isolates the first binding when its response fails=$retiredReadFails") {
      val fixture = new Fixture
      val gate = new ResponseGate
      fixture.failBlobReads.set(retiredReadFails)
      val holding = Var(true)
      val context = fixture.info.homepageInfoNow().toContext
      fixture.seed(context, "first binding only", LocalDateTime.now().plusDays(1))
      fixture.beforeResponse.set(command =>
        if holding.now() && command.name == fetchEventsFromDbCommand.name then gate.hold() else Future.unit)
      fixture.withHttp {
        val firstRead = fixture.info.syncControl.fetchAll(true).transform(result => scala.util.Success(result))
        gate.arrived.flatMap { _ =>
          holding.set(false)
          fixture.failBlobReads.set(false)
          val original = fixture.info.homepageInfoNow().workbookInfo
          val (other, _) = fixture.anotherWorkbook("intermediate-workbook")
          fixture.info.homepageInfoState.update(_.copy(workbookInfo = Some(other)))
          // Another client has reset/replaced the remote history while the first response is in flight.
          fixture.seed(context, "replacement binding only", LocalDateTime.now().plusDays(2))
          fixture.info.homepageInfoState.update(_.copy(workbookInfo = original))
          fixture.info.syncControl.ensureSynced().flatMap { _ =>
            val current = fixture.elements.map(_.interactionVariable.currentHistory)
            assert(current.forall(_.allStates.forall(_.newState != "first binding only")))
            gate.release()
            firstRead.map { outcome =>
              if retiredReadFails then assert(outcome.isFailure)
              assertEquals(fixture.elements.map(_.interactionVariable.currentHistory), current)
            }
          }
        }.andThen { case _ => gate.release() }
      }
    }
  }

  Seq(false, true).foreach { retiredFails =>
    test(s"same-user credential replacement isolates write confirmations when the retired write fails=$retiredFails") {
      val fixture = new Fixture
      val oldGate = new ResponseGate
      val newGate = new ResponseGate
      val oldToken = signed(fixture.user, "old-credentials")
      val newToken = signed(fixture.user, "new-credentials")
      val oldDatabase = DatabaseConfigWithCredentials("mysql", "localhost", "3306", "old_database", "test", "old-password")
      val newDatabase = oldDatabase.copy(database = "new_database", password = "new-password")
      fixture.info.homepageInfoState.update(_.copy(userInfo = Some(fixture.user.copy(credentials =
        UserCredentials(signedBackendToken = Some(oldToken), databaseConfig = Some(oldDatabase))))))
      val owner = new ManualOwner
      fixture.withHttp {
        fixture.info.syncControl.ensureSynced().flatMap { _ =>
          fixture.authorization.set(Vector.empty)
          fixture.beforeResponse.set(command =>
            if command.name == bulkStoreToDbCommand.name then {
              val request = read[BulkStoreToDbRequest](command.json)
              if request.databaseConfig == oldDatabase then oldGate.hold() else newGate.hold()
            } else Future.unit)
          fixture.failStores.set(retiredFails)
          fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction("retiring edit", UpdateImportance.MAJOR,
            context = ExecutionContextInformation.default))
          val retired = fixture.info.syncControl.storeAll(true).transform(result => scala.util.Success(result))
          oldGate.arrived.flatMap { _ =>
            fixture.failStores.set(false)
            fixture.info.homepageInfoState.update(_.copy(userInfo = Some(fixture.user.copy(credentials =
              UserCredentials(signedBackendToken = Some(newToken), databaseConfig = Some(newDatabase))))))
            // A successful retired acknowledgement carries the exact same latest event:
            // it must not confirm that event in the replacement database.
            val replacementAnswer = if retiredFails then "replacement edit" else "retiring edit"
            fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction(replacementAnswer, UpdateImportance.MAJOR,
              context = ExecutionContextInformation.default))
            val report = fixture.info.syncControl.createReportFor(fixture.elements.head).observe(using owner)
            val sharedReporter = fixture.info.signals.reportersFor(fixture.elements.head).sync
            val summary = sharedReporter.report.observe(using owner)
            val replacement = fixture.info.syncControl.storeAll(true)
            newGate.arrived.flatMap { _ =>
              assert(!replacement.isCompleted)
              assertEquals(report.now().get.destinationsWithSufficientWrites.size, 0)
              assertEquals(summary.now().confirmed, 0)
              oldGate.release()
              retired.flatMap { result =>
                if retiredFails then assert(result.isFailure)
                assert(!replacement.isCompleted)
                assertEquals(report.now().get.destinationsWithSufficientWrites.size, 0)
                assertEquals(summary.now().confirmed, 0)
                newGate.release()
                replacement.flatMap { _ =>
                  assertEquals(report.now().get.destinationsWithSufficientWrites.size, 0, "written, but not read back yet")
                  fixture.info.syncControl.fetchAll(true)
                }.map { _ =>
                  assertEquals(report.now().get.destinationsWithSufficientWrites.size, 2)
                  assertEquals(summary.now().confirmed, 2)
                  assertEquals(fixture.elements.map(_.interactionVariable.currentValue), Seq.fill(2)(replacementAnswer))
                  val writes = fixture.authorization.now().collect { case (command, token) if command.name == bulkStoreToDbCommand.name =>
                    read[BulkStoreToDbRequest](command.json) -> token
                  }
                  assert(writes.filter(_._1.databaseConfig == oldDatabase).forall(_._2 == oldToken.toJson))
                  assert(writes.filter(_._1.databaseConfig == newDatabase).forall(_._2 == newToken.toJson))
                  if !retiredFails then {
                    def latest(database: DatabaseConfigWithCredentials) = writes.filter(_._1.databaseConfig == database)
                      .flatMap(_._1.histories).flatMap(_.typedAs(TextInteractionFormat).latestAnswerEvents).toSet
                    assertEquals(latest(oldDatabase), latest(newDatabase))
                  }
                }
              }
            }
          }
        }.andThen { case _ => oldGate.release(); newGate.release(); owner.killSubscriptions() }
      }
    }
  }

  test("workbook selection from an Airstream observer starts reads for the replacement binding") {
    val fixture = new Fixture
    val gate = new ResponseGate
    val owner = new ManualOwner
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap { _ =>
        val (other, interactions) = fixture.anotherWorkbook("selected-workbook")
        val context = fixture.info.homepageInfoNow().toContext.copy(scenarioId = other.loadedWorkbook.workbookId)
        fixture.seed(context, "selected workbook", LocalDateTime.now().plusDays(1))
        fixture.beforeResponse.set(command =>
          if command.name == fetchEventsFromDbCommand.name && read[FetchEventsFromDbRequest](command.json).usageContext == context then gate.hold()
          else Future.unit)
        val select = Var(false)
        select.signal.changes.foreach(_ => fixture.info.workbookNavigationControl.changeWorkbook(Some(other)))(using owner)
        fixture.requests.set(Vector.empty)
        select.set(true)
        // Await the navigation's own reads before making any explicit request for the new binding.
        gate.arrived.flatMap { _ =>
          val reads = fixture.requests.now().filter(_.name == fetchEventsFromDbCommand.name)
            .map(command => read[FetchEventsFromDbRequest](command.json))
          assertEquals(reads.size, 2)
          assert(reads.forall(_.usageContext == context))
          gate.release()
          fixture.info.syncControl.ensureSynced().map { _ =>
            assertEquals(interactions.map(_.interactionVariable.currentValue), Seq.fill(2)("selected workbook"))
          }
        }
      }.andThen { case _ => gate.release(); owner.killSubscriptions() }
    }
  }

  test("changing configured destinations replaces the cache without inheriting old cursors or removed locations") {
    val fixture = new Fixture
    val owner = new ManualOwner
    fixture.withHttp {
      fixture.info.syncControl.ensureSynced().flatMap(_ => settled(fixture)).flatMap { _ =>
        val onlyRelational = fixture.user.config.copy(syncDestinations = Seq(
          SyncInformation(defaultDatabase, EventSyncStrategy.SYNC_EVERYTHING)))
        fixture.info.homepageInfoState.update(_.copy(userInfo = Some(fixture.user.copy(config = onlyRelational))))
        fixture.elements.foreach(_.interactionVariable.setStateFromUserInteraction("after destination change", UpdateImportance.MAJOR,
          context = ExecutionContextInformation.default))
        val report = fixture.info.syncControl.createReportFor(fixture.elements.head).observe(using owner)
        val sharedReporter = fixture.info.signals.reportersFor(fixture.elements.head).sync
        val summary = sharedReporter.report.observe(using owner)
        fixture.requests.set(Vector.empty)
        fixture.info.syncControl.ensureSynced().flatMap { _ =>
          val reads = fixture.requests.now().filter(_.name == fetchEventsFromDbCommand.name)
            .map(command => read[FetchEventsFromDbRequest](command.json))
          val writes = fixture.requests.now().filter(_.name == bulkStoreToDbCommand.name)
            .map(command => read[BulkStoreToDbRequest](command.json))
          assertEquals(reads.size, 1)
          assertEquals(reads.head.databaseFormat, defaultDatabase.format)
          assertEquals(reads.head.starting, LocalDateTime.MIN)
          assertEquals(writes.size, 1)
          assertEquals(writes.head.databaseFormat, defaultDatabase.format)
          assertEquals(report.now().get.allSyncDestinations.size, 1)
          assertEquals(summary.now().total, 1)
          fixture.info.syncControl.fetchAll(true)
        }.map { _ =>
          assertEquals(report.now().get.destinationsWithSufficientWrites.size, 1)
          assertEquals(summary.now().confirmed, 1)
        }
      }.andThen { case _ => owner.killSubscriptions() }
    }
  }

  test("language and display changes preserve the binding's cursor and confirmations") {
    val fixture = new Fixture
    val owner = new ManualOwner
    fixture.withHttp {
      synchronizedAndRead(fixture).flatMap { _ =>
        val report = fixture.info.syncControl.createReportFor(fixture.elements.head).observe(using owner)
        val sharedReporter = fixture.info.signals.reportersFor(fixture.elements.head).sync
        val summary = sharedReporter.report.observe(using owner)
        assertEquals(report.now().get.destinationsWithSufficientWrites.size, 2)
        assertEquals(summary.now().confirmed, 2)
        fixture.requests.set(Vector.empty)
        fixture.info.displayControl.changeLanguage(AppLanguage.German)
        fixture.info.homepageInfoState.update(info => info.copy(displayInfo = info.displayInfo.copy(collapsedNavigation = true)))
        assertEquals(report.now().get.destinationsWithSufficientWrites.size, 2)
        assertEquals(summary.now().confirmed, 2)
        fixture.info.syncControl.ensureSynced().map { _ =>
          val reads = fixture.requests.now().filter(_.name == fetchEventsFromDbCommand.name)
            .map(command => read[FetchEventsFromDbRequest](command.json))
          assertEquals(reads.size, 2)
          assert(reads.forall(_.starting == at))
          assert(!fixture.requests.now().exists(_.name == bulkStoreToDbCommand.name))
        }
      }.andThen { case _ => owner.killSubscriptions() }
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
