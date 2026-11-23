package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.*
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.syncDestination.{LocalIndexedDbStorageSyncHandler, LocalStorageSyncHandler}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.SyncLogger
import upickle.default.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Browser persistence for the current session and credential-free profile preferences.
  * Anonymous placeholder users are never stored.
  */
private[change] final class UserSessionStorage(logger: Logger, syncLogger: SyncLogger)(using ExecutionContext) {
  private val allUserInfoStorageKey: String = "edusquirrel-alluserinfo"

  private val sessionStorageKey = "edusquirrel-session"
  private val sessionCache = LocalIndexedDbStorageSyncHandler(LocalIndexedDbStorageSyncHandler.instanceForCaching)

  def saveSession(info: Option[AllUserInfo]): Future[Unit] = {
    val stored = info.filterNot(_.user.isAnonymous) match {
      case Some(user) => sessionCache.storeElementRaw(syncLogger, sessionStorageKey, write(user))
      case None => sessionCache.removeKeyRaw(syncLogger, sessionStorageKey)
    }
    stored.map(_ => ()).recover { case scala.util.control.NonFatal(error) =>
      logger.logExceptionWarn("Could not update cached login", error)
    }
  }

  def restoreSession(): Future[Option[AllUserInfo]] =
    sessionCache.readElementRaw(syncLogger, sessionStorageKey).transformWith {
      case Failure(_: NoSuchElementException) => Future.successful(None)
      case Failure(error) =>
        logger.logExceptionWarn("Could not read cached login", error)
        Future.successful(None)
      case Success(raw) => scala.util.Try(read[AllUserInfo](raw)) match {
        case Success(info) => Future.successful(Some(info))
        case Failure(_) =>
          logger.logWarn("Discarded an invalid cached login; please sign in again.")
          saveSession(None).map(_ => None)
      }
    }

  def loadSavedProfile(): Option[AllUserInfo] = {
    val restored: scala.util.Try[Option[AllUserInfo]] = scala.util.Try(Option(org.scalajs.dom.window.localStorage.getItem(allUserInfoStorageKey)))
      .flatMap {
        case None => Success(None)
        case Some(raw) => scala.util.Try {
          val json = ujson.read(raw)
          require(json.obj.keySet.toSet == Set("user", "config"), "Saved credentials are not supported")
          val saved = read[SavedUserPreferences](json)
          Some(AllUserInfo(saved.user, UserCredentials(), saved.config))
        }
      }
    restored.recover { case scala.util.control.NonFatal(_) =>
      scala.util.Try(org.scalajs.dom.window.localStorage.removeItem(allUserInfoStorageKey))
      logger.logWarn("Discarded an invalid saved login; please sign in again.")
      None
    }.get
  }


  def clearSavedProfile(): Unit = {
    scala.util.Try(LocalStorageSyncHandler.removeKey(allUserInfoStorageKey))
    ()
  }

  def saveProfile(info: Option[AllUserInfo]): Unit = {
    try {
      LocalStorageSyncHandler.removeKey(allUserInfoStorageKey)
      info.filterNot(_.user.isAnonymous).foreach { user =>
        logger.logInfo(s"Loading user $user")
        val serialized = write(SavedUserPreferences(user.user, user.config.withoutCredentials))
        LocalStorageSyncHandler.instance.storeElementRaw(syncLogger, allUserInfoStorageKey, serialized)
          .failed.foreach(error => logger.logExceptionWarn("Could not save user preferences", error))
      }
    } catch case scala.util.control.NonFatal(error) =>
      logger.logExceptionWarn("Could not update saved user preferences", error)
  }
}
