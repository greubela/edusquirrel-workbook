package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.{AllUserInfo, SavedUserPreferences, UserConfig, UserCredentials}
import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo, HomepageInfo}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.workbook.interaction.sync.{EventSyncStrategy, SyncInformation}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.defaultDatabase
import org.scalajs.dom
import upickle.default.write

/** The saved login in browser storage holds the user and public settings only, and is discarded when unusable. */
class SavedLoginSpec extends EditorDomSuite {
  private val key = "edusquirrel-alluserinfo"
  private def control = FullInfo(HomepageInfo(AppLanguage.English, None, None, AllDisplayInfo(false, None), LanguageMapStorage.empty)).usageControl
  override def beforeEach(context: BeforeEach): Unit = dom.window.localStorage.clear()

  private val credentials = DatabaseConfigWithCredentials("mysql", "db.example", "3306", "school", "private-user", "private-password")
  private val user = AllUserInfo.createNewUser("Student", "saved@example.invalid", UserConfig(List(
    SyncInformation(defaultDatabase.copy(databaseConfig = credentials), EventSyncStrategy.SYNC_EVERYTHING)), isOnlineAccount = false))

  test("a saved login restores the user and settings, never credentials") {
    dom.window.localStorage.setItem(key, write(SavedUserPreferences(user.user, user.config.withoutCredentials)))
    val restored = control.tryParsingExistingUser().getOrElse(fail("the saved login was not restored"))
    assertEquals(restored.user, user.user)
    assertEquals(restored.config, user.config.withoutCredentials)
    assertEquals(restored.credentials, UserCredentials())
    assert(!dom.window.localStorage.getItem(key).contains("private-"), "credentials never reach browser storage")
  }

  test("no saved login reads as none and leaves storage alone") {
    dom.window.localStorage.setItem("unrelated", "kept")
    assertEquals(control.tryParsingExistingUser(), None)
    assertEquals(dom.window.localStorage.getItem("unrelated"), "kept")
  }

  test("malformed or unsupported saved logins are discarded without touching other entries") {
    val withCredentials = ujson.Obj("user" -> ujson.read(write(user.user)), "config" -> ujson.read(write(user.config)),
      "credentials" -> ujson.Obj("signedBackendToken" -> "private-token"))
    List("{not json", "[]", """{"user": {}, "config": {}}""", ujson.write(withCredentials)).foreach { saved =>
      dom.window.localStorage.setItem(key, saved)
      dom.window.localStorage.setItem("unrelated", "kept")
      assertEquals(control.tryParsingExistingUser(), None, saved)
      assertEquals(dom.window.localStorage.getItem(key), null, s"the unusable entry is removed: $saved")
      assertEquals(dom.window.localStorage.getItem("unrelated"), "kept")
    }
  }
}
