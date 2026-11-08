package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.datastructures.user.UserTokenInfo
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.sql.DatabaseHistoryFormat.{ClassicJsonWithKey, ClassicKeylessJson}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
import munit.FunSuite
import upickle.default.*

class StoredUserInfoSpec extends FunSuite {
  private val user = User("Saved student", "saved-id", "saved@example.org")
  private val token = Some(UserTokenInfo.SignedToken(
    UserTokenInfo(user, java.time.LocalDateTime.of(2026, 1, 1, 0, 0), java.time.LocalDateTime.of(2099, 1, 1, 0, 0)), "fixture"))

  test("current saved users retain identity, token and configuration") {
    val value = AllUserInfo(user, token, UserConfig(HomepageDefaults.defaultSyncLocations, true))
    assertEquals(StoredUserInfo.decode(write(value)).get, value)
  }

  test("legacy string-encoded configuration migrates named destinations without losing identity or token") {
    val config = ujson.Obj("syncDestinations" -> ujson.Arr("localStorage", "db1", "db2", "localIndexedDb"), "isOnlineAccount" -> true)
    for encoded <- List[ujson.Value](ujson.Str(config.render()), config) do {
      val raw = ujson.Obj("user" -> writeJs(user), "token" -> writeJs(token), "config" -> encoded)
      val restored = StoredUserInfo.decode(raw.render()).get
      assertEquals(restored.user, user)
      assertEquals(restored.token, token)
      assert(restored.config.isOnlineAccount)
      val destinations = restored.config.syncDestinations.map(_.syncDestination)
      assertEquals(destinations.head, LocalStorageSyncDefinition(50000))
      assertEquals(destinations.collect { case SyncDestinationBasedOnHistoryDatabase(_, format) => format },
        List(ClassicJsonWithKey, ClassicKeylessJson))
      assert(destinations.last.isInstanceOf[LocalIndexedDbSyncDefinition])
      assertEquals(StoredUserInfo.decode(write(restored)).get, restored)
    }
  }

  test("local users stay local and malformed saved records fail without inventing a new identity") {
    val config = ujson.Obj("syncDestinations" -> ujson.Arr("localIndexedDb"), "isOnlineAccount" -> false)
    val raw = ujson.Obj("user" -> writeJs(user), "token" -> ujson.Null, "config" -> config.render())
    val restored = StoredUserInfo.decode(raw.render()).get
    assert(!restored.config.isOnlineAccount)
    assert(restored.config.syncDestinations.forall(_.syncDestination.isInstanceOf[LocalIndexedDbSyncDefinition]))
    for invalid <- List("not JSON", "{}", "{\"config\":null}") do
      assert(StoredUserInfo.decode(invalid).isFailure)
  }
}
