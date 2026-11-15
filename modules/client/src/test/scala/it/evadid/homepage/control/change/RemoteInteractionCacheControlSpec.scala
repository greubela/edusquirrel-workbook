package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo, HomepageInfo}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.variable.InteractionVariable
import it.evadid.workbook.meta.InteractionVariableSyncReport
import munit.FunSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class RemoteInteractionCacheControlSpec extends FunSuite {
  private def emptyInfo: FullInfo = FullInfo(HomepageInfo(English, None, None,
    AllDisplayInfo(false, None), LanguageMapStorage.empty))

  test("batch loading propagates a failure from an asynchronous per-variable load") {
    val failure = IllegalStateException("history report failed")
    val control = new RemoteInteractionCacheControl(emptyInfo) {
      override def createCurrentReport[T, GA, SA](variable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] =
        throw failure
    }
    val result: Future[Unit] = control.load(List(InteractionVariable(TextInteraction("answer"))))
    result.failed.map(error => assert(error eq failure))
  }

  test("an empty batch completes without accessing remote caches") {
    val control = RemoteInteractionCacheControl(emptyInfo)
    val result: Future[Unit] = control.load(Nil)
    result.map(value => assertEquals(value, ()))
  }

  test("display and language updates preserve sync destination identities") {
    import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserCredentials}
    import it.evadid.core.datastructures.language.AppLanguage.German
    import it.evadid.workbook.interaction.sync.{SyncInformation, SyncStrategy}
    import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition
    val info = emptyInfo
    val user = AllUserInfo(User("Test", "test-user", "test@example.test"), UserCredentials(),
      UserConfig(List(SyncInformation(SyncDestinationDefinition.defaultBrowserHistory, SyncStrategy.SYNC_LAST)), false))
    info.homepageInfoState.update(_.copy(userInfo = Some(user)))
    val before = info.signals.currentSyncDestinations.now()
    info.homepageInfoState.update(_.copy(currentLanguage = German))
    assertEquals(info.signals.currentSyncDestinations.now(), before)
    info.homepageInfoState.update(value => value.copy(displayInfo = value.displayInfo.copy(collapsedNavigation = !value.displayInfo.collapsedNavigation)))
    assertEquals(info.signals.currentSyncDestinations.now(), before)
  }


  test("a load queued for the previous user cannot apply after the identity changes") {
    val info = emptyInfo
    val control = new RemoteInteractionCacheControl(info) {
      override def createCurrentReport[T, GA, SA](variable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] =
        throw AssertionError("A stale load must not read the new user's report")
    }
    val variable = TextInteraction("answer").interactionVariable
    val loading = control.load(List(variable))
    info.homepageInfoState.update(_.copy(userInfo = Some(it.evadid.core.datastructures.user.AllUserInfo(
      it.evadid.core.datastructures.user.User("Other", "other", "other@example.test"),
      it.evadid.core.datastructures.user.UserCredentials(), it.evadid.core.datastructures.user.UserConfig(Nil, false)))))
    loading.map(_ => assertEquals(variable.currentValue, ""))
  }

}
