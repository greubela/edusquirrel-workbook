package it.evadid.homepage.workbook.syncDestination

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.LocalIndexedDbSyncDefinition
import org.scalajs.dom
import org.scalajs.dom.{IDBDatabase, IDBObjectStore, IDBTransaction, IDBTransactionMode}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

object LocalIndexedDbStorageSyncHandler {
  val instanceForCaching = LocalIndexedDbSyncDefinition("EvaDidCacheDb", "cache", 1)
}

case class LocalIndexedDbStorageSyncHandler(definition: LocalIndexedDbSyncDefinition) extends SyncDestinationHandler {

  override def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess] =
    // todo: count is transaction.. nervig
    inTransaction[SyncSuccess](IDBTransactionMode.readwrite, "IndexedDB transaction aborted",
      committed = Some(() => SyncSuccess(0, 0, 0, LocalDateTime.now()))) { (_, store, _) =>
      store.clear()
    }

  override def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess] =
    inTransaction[SyncSuccess](IDBTransactionMode.readwrite, "IndexedDB delete transaction aborted",
      committed = Some(() => SyncSuccess(0, 0, 1, LocalDateTime.now()))) { (_, store, _) =>
      store.delete(key)
    }

  override def storeElementRaw(logger: SyncLogger, key: String, value: String)(using ec: ExecutionContext): Future[SyncSuccess] =
    inTransaction[SyncSuccess](IDBTransactionMode.readwrite, "IndexedDB write transaction aborted",
      committed = Some(() => SyncSuccess(1, 0, 0, LocalDateTime.now()))) { (_, store, _) =>
      store.put(record(key, value))
    }

  override def updateElementRaw(logger: SyncLogger, key: String, update: Option[String] => String)(using ec: ExecutionContext): Future[SyncSuccess] =
    inTransaction[SyncSuccess](IDBTransactionMode.readwrite, s"IndexedDB update aborted for $key",
      committed = Some(() => SyncSuccess(0, 1, 0, LocalDateTime.now()))) { (transaction, store, promise) =>
      val request = store.get(key)
      request.onsuccess = (_: dom.Event) => {
        try {
          val previous = if js.isUndefined(request.result) then None else Some(storedValue(request.result))
          store.put(record(key, update(previous)))
        } catch case scala.util.control.NonFatal(error) =>
          promise.tryFailure(error)
          transaction.abort()
      }
    }

  override def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String] =
    inTransaction[String](IDBTransactionMode.readonly, s"IndexedDB read aborted: $key") { (_, store, promise) =>
      val request = store.get(key)
      request.onsuccess = (_: dom.Event) => {
        if (js.isUndefined(request.result)) promise.tryFailure(new NoSuchElementException(s"No IndexedDB value for $key"))
        else promise.trySuccess(storedValue(request.result))
      }
      request.onerror = (_: dom.Event) => promise.tryFailure(new RuntimeException(s"IndexedDB read failed: ${request.error.name}"))
    }

  override def underlyingDefinition: SyncDestinationDefinition = definition

  override def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]] =
    inTransaction[Map[String, String]](IDBTransactionMode.readonly, "IndexedDB cursor transaction aborted") { (_, store, promise) =>
      // Use a cursor to step through the records asynchronously; each step installs the handler for the next one.
      val request = store.openCursor()
      def collect(entries: Map[String, String]): Unit =
        request.onsuccess = (_: dom.Event) => {
          val cursor = request.result.asInstanceOf[dom.IDBCursorWithValue[js.Any]]
          if (cursor != null) {
            val stored = cursor.value.asInstanceOf[js.Dynamic]
            collect(entries.updated(stored.id.asInstanceOf[String], stored.value.asInstanceOf[String]))
            cursor.continue()
          }
          else {
            promise.trySuccess(entries)
          }
        }
      collect(Map.empty)

      request.onerror = (_: dom.Event) => {
        promise.tryFailure(new RuntimeException(s"Cursor parsing failed: ${request.error.name}"))
      }
    }

  /** Our store layout: 'id' (string key) -> 'value' (stringified data). */
  private def record(key: String, value: String): js.Dynamic = js.Dynamic.literal(id = key, value = value)

  private def storedValue(result: Any): String = result.asInstanceOf[js.Dynamic].value.asInstanceOf[String]

  /** Starts one transaction on the store. Its commit closes the database and, if `committed` is given, completes
    * with that result; an abort closes the database and fails with `abortMessage`. `start` may complete earlier.
    */
  private def inTransaction[A](mode: IDBTransactionMode, abortMessage: => String, committed: Option[() => A] = None)(
      start: (IDBTransaction, IDBObjectStore, Promise[A]) => Unit)(using ExecutionContext): Future[A] =
    openDatabase().flatMap { db =>
      val promise = Promise[A]()
      val transaction = db.transaction(js.Array(definition.storeName), mode)
      start(transaction, transaction.objectStore(definition.storeName), promise)
      transaction.oncomplete = (_: dom.Event) => {
        db.close()
        committed.foreach(result => promise.trySuccess(result()))
      }
      transaction.onabort = (_: dom.Event) => {
        db.close()
        promise.tryFailure(new RuntimeException(abortMessage))
      }
      promise.future
    }

  // --- Helper: Open DB Connection wrapped in a Scala Future ---
  private def openDatabase(): Future[IDBDatabase] =
    Try(dom.window.indexedDB.get.open(definition.dbName, definition.dbVersion)) match {
      case Failure(error) => Future.failed(error)
      case Success(request) =>
        val promise = Promise[IDBDatabase]()

        def ensureStore(db: IDBDatabase): Unit = {
          if (!db.objectStoreNames.contains(definition.storeName)) {
            // FIX: Cast js.Dynamic.literal to structural option trait to bypass read-only fields
            db.createObjectStore(definition.storeName, js.Dynamic.literal(keyPath = "id").asInstanceOf[dom.IDBCreateObjectStoreOptions])
          }
        }

        request.onupgradeneeded = (_: dom.IDBVersionChangeEvent) => ensureStore(request.result.asInstanceOf[IDBDatabase])

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
}
