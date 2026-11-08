package it.evadid.homepage.workbook.syncDestination

import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.LocalStorageSyncDefinition
import it.evadid.workbook.interaction.sync.destination.{SyncDestinationDefinition, SyncDestinationHandler}
import org.scalajs.dom
import org.scalajs.dom.Storage

import java.time.LocalDateTime
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

  def resetCompleteStorage(): SyncSuccess = {
    println("[UGLY WARN IN LOCALSTORAGESYNC] clearing all local storage!")
    dom.window.localStorage.clear()
    SyncSuccess(0, 0, dom.window.localStorage.length, LocalDateTime.now())
  }

  def removeKey(key: String): SyncSuccess = {
    val sizeBefore = dom.window.localStorage.length
    dom.window.localStorage.removeItem(key)
    val sizeAfter = dom.window.localStorage.length
    SyncSuccess(0, 0, sizeBefore - sizeAfter, LocalDateTime.now())
  }
}

case class LocalStorageSyncHandler(localStorageSyncDefinition: LocalStorageSyncDefinition) extends SyncDestinationHandler {

  private val storage: Storage = dom.window.localStorage

  override def storeElementRaw(logger: SyncLogger, key: String, value: String)(using ec: ExecutionContext): Future[SyncSuccess] = Future {
    storage.setItem(key.toString, value.toString)
    SyncSuccess(1, 0, 0, LocalDateTime.now())
  }

  override def underlyingDefinition: SyncDestinationDefinition = localStorageSyncDefinition

  override def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess] = Future {
    LocalStorageSyncHandler.resetCompleteStorage()
  }

  override def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess] = Future {
    LocalStorageSyncHandler.removeKey(key)
  }

  override def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String] = readDestinationRaw(logger)(ec).transformWith {
    case Success(map) =>
      if (map.contains(key)) Future.successful(map(key))
      else Future.failed(SerializedException(s"Read ${map.size} elements from local storage map, but key ${key.take(60)} is not present!"))
    case Failure(err) => Future.failed(SerializedException(s"Error while reading local storage: ${err.getMessage}", err))
  }

  override def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]] = Future {
    LocalStorageSyncHandler.fetchAllRawSync()
  }(using ec)
}
