package it.evadid.homepage.workbook.syncDestination

import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.sync.*
import it.evadid.workbook.sync.destination.SyncDestinationDefinition.LocalStorageSyncDefinition
import it.evadid.workbook.sync.destination.{SyncDestinationDefinition, SyncDestinationHandler}
import org.scalajs.dom
import org.scalajs.dom.Storage

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object LocalStorageSyncHandler {

  val instance: LocalStorageSyncHandler = LocalStorageSyncHandler(LocalStorageSyncDefinition(50000))

  def fetchAllRawSync(): Map[String, String] = {
    val storage: Storage = dom.window.localStorage
    (0 until storage.length).map(i =>
      val browserKey = storage.key(i)
      val browserValue = storage.getItem(browserKey)
      browserKey -> browserValue
    ).toMap
  }

  def resetCompleteStorage(): SyncSuccess[String] = {
    val removed = fetchAllRawSync().keySet
    dom.window.localStorage.clear()
    SyncSuccess.removed(removed)
  }

  def removeKey(key: String): SyncSuccess[String] = {
    val existed = Option(dom.window.localStorage.getItem(key)).isDefined
    dom.window.localStorage.removeItem(key)
    SyncSuccess.removed(Set(key).filter(_ => existed))
  }
}

case class LocalStorageSyncHandler(localStorageSyncDefinition: LocalStorageSyncDefinition) extends SyncDestinationHandler {

  private val storage: Storage = dom.window.localStorage

  override def updateElementRaw[R](logger: SyncLogger, key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R] = Future {
    val (value, result) = update(Option(storage.getItem(key)))
    storage.setItem(key, value)
    result
  }

  override def underlyingDefinition: SyncDestinationDefinition = localStorageSyncDefinition

  override def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess[String]] = Future {
    LocalStorageSyncHandler.resetCompleteStorage()
  }

  override def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess[String]] = Future {
    LocalStorageSyncHandler.removeKey(key)
  }

  override def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String] = readDestinationRaw(logger)(using ec).transformWith {
    case Success(map) =>
      if (map.contains(key)) Future.successful(map(key))
      else Future.failed(SerializedException(s"Read ${map.size} elements from local storage map, but key ${key.take(60)} is not present!"))
    case Failure(err) => Future.failed(SerializedException(s"Error while reading local storage: ${err.getMessage}", err))
  }

  override def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]] = Future {
    LocalStorageSyncHandler.fetchAllRawSync()
  }(using ec)
}
