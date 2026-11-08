package it.evadid.homepage.workbook.syncDestination

import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.LocalIndexedDbSyncDefinition
import org.scalajs.dom
import org.scalajs.dom.{IDBDatabase, IDBObjectStore, IDBTransactionMode}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

object LocalIndexedDbStorageSyncHandler {
  val instanceForHistory = LocalIndexedDbSyncDefinition("EvaDidInteractionDB", "variableHistoryStore", 1)
  val instanceForCaching = LocalIndexedDbSyncDefinition("EvaDidCacheDb", "cache", 1)
}

case class LocalIndexedDbStorageSyncHandler(definition: LocalIndexedDbSyncDefinition) extends SyncDestinationHandler {

  override def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess] = {
    openDatabase().flatMap { db =>

      val promise = Promise[SyncSuccess]()

      val transaction = db.transaction(js.Array(definition.storeName), IDBTransactionMode.readwrite)
      val store: IDBObjectStore = transaction.objectStore(definition.storeName)

      store.clear()

      transaction.oncomplete = (_: dom.Event) => {
        db.close();
        promise.trySuccess(SyncSuccess(0, 0, 0, LocalDateTime.now())) // todo: count is transaction.. nervig
      }
      transaction.onabort = (_: dom.Event) => {
        db.close();
        promise.tryFailure(new RuntimeException("IndexedDB transaction aborted"))
      }
      promise.future
    }
  }

  override def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess] = ???

  override def storeElementRaw(logger: SyncLogger, key: String, value: String)(using ec: ExecutionContext): Future[SyncSuccess] = {
    openDatabase().flatMap { db =>
      val promise = Promise[Boolean]()
      val transaction = db.transaction(js.Array(definition.storeName), IDBTransactionMode.readwrite)
      val store = transaction.objectStore(definition.storeName)

      // Package data matching our store structure
      val dataEntry = js.Dynamic.literal(id = key, value = value)
      val request = store.put(dataEntry)

      transaction.oncomplete = (_: dom.Event) => {
        db.close()
        promise.trySuccess(true)
      }

      transaction.onabort = (_: dom.Event) => {
        db.close()
        promise.tryFailure(new RuntimeException("IndexedDB write transaction aborted"))
      }

      promise.future.map(if (_) SyncSuccess(1, 0, 0, LocalDateTime.now()) else throw SerializedException("Storing failed for unknown reasons!"))
    }
  }

  override def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String] = {
    openDatabase().flatMap { db =>
      val promise = Promise[String]()
      val transaction = db.transaction(js.Array(definition.storeName), IDBTransactionMode.readonly)
      val request = transaction.objectStore(definition.storeName).get(key)
      request.onsuccess = (_: dom.Event) => {
        if (js.isUndefined(request.result)) promise.tryFailure(new NoSuchElementException(s"No IndexedDB value for $key"))
        else promise.trySuccess(request.result.asInstanceOf[js.Dynamic].value.asInstanceOf[String])
      }
      request.onerror = (_: dom.Event) => promise.tryFailure(new RuntimeException(s"IndexedDB read failed: ${request.error.name}"))
      transaction.oncomplete = (_: dom.Event) => db.close()
      transaction.onabort = (_: dom.Event) => {
        db.close()
        promise.tryFailure(new RuntimeException(s"IndexedDB read aborted: $key"))
      }
      promise.future
    }
  }

  override def underlyingDefinition: SyncDestinationDefinition = definition


  // --- Helper: Open DB Connection wrapped in a Scala Future ---
  private def openDatabase(): Future[IDBDatabase] = {
    val promise = Promise[IDBDatabase]()
    val request = try {
      dom.window.indexedDB.get.open(definition.dbName, definition.dbVersion)
    } catch {
      case scala.util.control.NonFatal(error) => return Future.failed(error)
    }

    def ensureStore(db: IDBDatabase): Unit = {
      if (!db.objectStoreNames.contains(definition.storeName)) {
        // We use a simple layout: 'key' (string) -> 'value' (stringified data)
        // FIX: Cast js.Dynamic.literal to structural option trait to bypass read-only fields
        db.createObjectStore(definition.storeName, js.Dynamic.literal(keyPath = "id").asInstanceOf[dom.IDBCreateObjectStoreOptions])
      }
    }

    request.onupgradeneeded = (event: dom.IDBVersionChangeEvent) => {
      val db = request.result.asInstanceOf[IDBDatabase]
      ensureStore(db)
    }

    request.onsuccess = (_: dom.Event) => {
      val db = request.result.asInstanceOf[IDBDatabase]
      db.onversionchange = (_: dom.IDBVersionChangeEvent) => db.close()
      if (!promise.trySuccess(db)) db.close()
    }

    request.onerror = (_: dom.Event) => {
      promise.tryFailure(new RuntimeException(s"Failed to open IndexedDB: ${request.error.name}"))
    }
    request.onblocked = (_: dom.Event) => {
      promise.tryFailure(new RuntimeException(s"IndexedDB opening blocked: ${definition.dbName}/${definition.storeName}"))
    }

    promise.future
  }

  override def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]] = {
    openDatabase().flatMap { db =>
      val promise = Promise[Map[String, String]]()
      val transaction = db.transaction(js.Array(definition.storeName), IDBTransactionMode.readonly)
      val store = transaction.objectStore(definition.storeName)

      transaction.oncomplete = (_: dom.Event) => db.close()
      transaction.onabort = (_: dom.Event) => {
        db.close()
        promise.tryFailure(new RuntimeException("IndexedDB cursor transaction aborted"))
      }

      // Use a cursor to step through the records asynchronously
      val request = store.openCursor()
      var mutableMap = Map[String, String]()

      request.onsuccess = (event: dom.Event) => {
        val cursor = request.result.asInstanceOf[dom.IDBCursorWithValue[js.Any]]
        if (cursor != null) {
          val record = cursor.value.asInstanceOf[js.Dynamic]
          val browserKey = record.id.asInstanceOf[String]
          val browserValue = record.value.asInstanceOf[String]

          mutableMap += browserKey -> browserValue
          cursor.continue()
        }
        else {
          promise.trySuccess(mutableMap)
        }
      }

      request.onerror = (_: dom.Event) => {
        promise.tryFailure(new RuntimeException(s"Cursor parsing failed: ${request.error.name}"))
      }

      promise.future
    }
  }
}
