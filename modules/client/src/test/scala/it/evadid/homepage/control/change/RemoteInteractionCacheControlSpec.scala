package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.{English, German}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.storage.remote.{RemoteDataSyncControllable, WorkbookSyncEnvironment}
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserCredentials}
import it.evadid.homepage.control.model.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.model.FetchResponse
import it.evadid.workbook.interaction.variable.{InteractionVariableHistory, InteractionVariableState}
import munit.FunSuite
import java.time.LocalDateTime
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class RemoteInteractionCacheControlSpec extends FunSuite {
  private def user(id: String) = AllUserInfo(User("Test", id, s"$id@example.test"), UserCredentials(), UserConfig(Nil, false))
  private def fixture() = {
    val answer = TextInteraction("answer")
    val second = TextInteraction("second")
    val section = WorkbookSection("section", WorkbookSection.WorkbookSectionMetadata(LanguageMapContentId("test/title")), List(answer, second))
    val workbook = Workbook("test", Workbook.WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("test/title"), List(English)), List(section))
    val info = FullInfo(HomepageInfo(English, Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)),
      Some(user("first")), AllDisplayInfo(false, None), LanguageMapStorage.empty))
    (info, answer, second)
  }
  private def install(info: FullInfo, response: Future[FetchResponse[SyncContext, String]]) = {
    val context = info.homepageInfoNow().toContext
    val destination = new SyncDestinationHistoryHandler {
      val syncLogger = info.loggerSystemInfo.syncControlLogger
      val definition = SyncDestinationDefinition.defaultBrowserHistory
      def readHistoriesJson(context: UsageContext)(implicit ec: scala.concurrent.ExecutionContext) = response
      def storeHistoryJson(context: SyncContext, json: String)(implicit ec: scala.concurrent.ExecutionContext) = Future.successful(SyncSuccess.emptyNow())
      def clearValue(context: SyncContext)(implicit ec: scala.concurrent.ExecutionContext) = Future.successful(SyncSuccess.emptyNow())
      def clearValues(context: UsageContext)(implicit ec: scala.concurrent.ExecutionContext) = Future.successful(SyncSuccess.emptyNow())
    }
    val location = SyncInformation(destination.definition, SyncStrategy.SYNC_EVERYTHING).forContext(context, _ => destination)
    val environment = WorkbookSyncEnvironment(context, info.homepageInfoNow().workbookInfo.get.loadedWorkbook.allContainedInteractionsById.values.toSet, Set(location))
    val state = RemoteDataSyncControllable(destination.syncLogger, Some(environment))
    info.syncControl.innerState.set(state)
    state
  }

  test("empty environments initialize and complete without remote work") {
    val info = FullInfo(HomepageInfo(English, None, None, AllDisplayInfo(false, None), LanguageMapStorage.empty))
    info.syncControl.ensureSynced().map(_ => assertEquals(info.syncControl.innerState.now().syncEnv, None))
  }
  test("display and language changes preserve the controller and destination identities") {
    val (info, _, _) = fixture()
    info.homepageInfoState.update(current => current.copy(userInfo = current.userInfo.map(_.copy(config =
      UserConfig(List(SyncInformation(SyncDestinationDefinition.defaultBrowserHistory, SyncStrategy.SYNC_LAST)), false)))))
    val before = info.syncControl.innerState.now()
    assertEquals(before.caches.readWriters.size, 1)
    info.homepageInfoState.update(_.copy(currentLanguage = German))
    info.homepageInfoState.update(current => current.copy(displayInfo = current.displayInfo.copy(collapsedNavigation = true)))
    assert(info.syncControl.innerState.now() eq before)
  }
  test("a cold fetch restores valid records and discards only malformed histories") {
    val (info, answer, second) = fixture()
    val context = info.homepageInfoNow().toContext
    val history = InteractionVariableHistory[String, Unit, Unit](Set(InteractionVariableState("saved", UpdateImportance.MAJOR, LocalDateTime.now())))
    val state = install(info, Future.successful(FetchResponse.fromMap(LocalDateTime.now(), Map(
      context.toSyncContext(answer.interactionVariable.keyForSerialization) -> answer.interactionVariable.serializerHistory.serialize(history),
      context.toSyncContext(second.interactionVariable.keyForSerialization) -> "invalid"))))
    state.fetchAll(true).map { _ =>
      assertEquals(answer.interactionVariable.currentValue, "saved")
      assertEquals(second.interactionVariable.currentValue, "")
    }
  }
  test("a delayed response is ignored after switching away and back to the same user") {
    val (info, answer, _) = fixture()
    val context = info.homepageInfoNow().toContext
    val gate = Promise[FetchResponse[SyncContext, String]]()
    val old = install(info, gate.future)
    val loading = old.fetchAll(true)
    info.homepageInfoState.update(_.copy(userInfo = Some(user("other"))))
    info.homepageInfoState.update(_.copy(userInfo = Some(user("first"))))
    val history = InteractionVariableHistory[String, Unit, Unit](Set(InteractionVariableState("old", UpdateImportance.MAJOR, LocalDateTime.now())))
    gate.success(FetchResponse.fromMap(LocalDateTime.now(), Map(
      context.toSyncContext(answer.interactionVariable.keyForSerialization) -> answer.interactionVariable.serializerHistory.serialize(history))))
    loading.map(_ => assertEquals(answer.interactionVariable.currentValue, ""))
  }
  test("cache signals detach on unmount and seed the latest snapshot when observed again") {
    import com.raquo.airstream.ownership.ManualOwner
    import com.raquo.airstream.state.Var
    import it.evadid.core.datastructures.state.State
    import it.evadid.core.datastructures.state.StateHelper.*
    val source = State("first")
    val signal = source.observable.toSignal("fallback")
    val seen = Var(List.empty[String])
    val first = new ManualOwner
    signal.foreach(value => seen.update(_ :+ value))(using first)
    assertEquals(seen.now().last, "first")
    val beforeUnmount = seen.now()
    first.killSubscriptions()
    source.set("changed while unmounted")
    assertEquals(seen.now(), beforeUnmount)
    val second = new ManualOwner
    signal.foreach(value => seen.update(_ :+ value))(using second)
    assertEquals(seen.now().last, "changed while unmounted")
    second.killSubscriptions()
  }

}
