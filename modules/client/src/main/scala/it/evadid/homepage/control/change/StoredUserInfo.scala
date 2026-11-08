package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.sql.DatabaseHistoryFormat.{ClassicJsonWithKey, ClassicKeylessJson}
import it.evadid.workbook.interaction.sync.{SyncInformation, SyncStrategy}
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.*
import upickle.default.*

import scala.util.Try
import scala.util.control.NonFatal

/** Migration belongs at the persisted browser-data boundary, not in transport codecs. */
private[change] object StoredUserInfo {
  def decode(raw: String): Try[AllUserInfo] = Try(read[AllUserInfo](raw)).recoverWith { case NonFatal(_) => Try {
    val saved = ujson.read(raw)
    val config = saved("config") match {
      case ujson.Str(encoded) => ujson.read(encoded)
      case value => value
    }
    val restoredConfig = Try(read[UserConfig](config)).getOrElse {
      val destinations = config("syncDestinations").arr.toList.map(value => legacyDestination(value.str))
      UserConfig(destinations, config("isOnlineAccount").bool)
    }
    AllUserInfo(read[User](saved("user")), read[Option[SignedToken]](saved("token")),
      restoredConfig)
  }}

  private def legacyDestination(name: String): SyncInformation = name match {
    case "localStorage" | "unknown" =>
      SyncInformation(LocalStorageSyncDefinition(50000), SyncStrategy.SYNC_LAST)
    case "localIndexedDb" => HomepageDefaults.defaultSyncLocations.find(
      _.syncDestination.isInstanceOf[LocalIndexedDbSyncDefinition]).get
    case "db1" => historyDatabase(ClassicJsonWithKey)
    case "db2" => historyDatabase(ClassicKeylessJson)
    case _ => throw IllegalArgumentException("Unrecognized saved synchronization destination")
  }

  private def historyDatabase(format: it.evadid.sql.DatabaseHistoryFormat): SyncInformation =
    HomepageDefaults.defaultSyncLocations.find(_.syncDestination match {
      case SyncDestinationBasedOnHistoryDatabase(_, existing) => existing == format
      case _ => false
    }).get
}
