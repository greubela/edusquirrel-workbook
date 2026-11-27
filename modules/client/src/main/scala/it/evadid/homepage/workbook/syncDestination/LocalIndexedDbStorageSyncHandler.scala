package it.evadid.homepage.workbook.syncDestination

import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.sync.destination.SyncDestinationDefinition.LocalIndexedDbSyncDefinition
import org.scalajs.dom
import org.scalajs.dom.{IDBDatabase, IDBObjectStore, IDBTransaction, IDBTransactionMode}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

object LocalIndexedDbStorageSyncHandler {
  val instanceForCaching = LocalIndexedDbSyncDefinition("EvaDidCacheDb", "cache", 1)
}

case class LocalIndexedDbStorageSyncHandler(definition: LocalIndexedDbSyncDefinition) extends SyncDestinationHandler {

  override def clearAll(logger: SyncLogger)(using ec: ExecutionContext): Future[SyncSuccess[String]] =
    inTransaction(IDBTransactionMode.readwrite, "IndexedDB clear transaction aborted") { (_, store) =>
      val keys = store.getAllKeys()
      onResult(keys) { all =>
        store.clear()
        SyncSuccess.removed(all.toSet.map(_.toString))
      }
    }

  override def removeKeyRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[SyncSuccess[String]] =
    inTransaction(IDBTransactionMode.readwrite, "IndexedDB delete transaction aborted") { (_, store) =>
      val request = store.get(key)
      onResult(request) { result =>
        store.delete(key)
        SyncSuccess.removed(Set(key).filterNot(_ => js.isUndefined(result)))
      }
    }

  override def updateElementRaw[R](logger: SyncLogger, key: String, update: Option[String] => (String, R))(using ec: ExecutionContext): Future[R] =
    inTransaction(IDBTransactionMode.readwrite, s"IndexedDB update aborted for $key") { (transaction, store) =>
      val request = store.get(key)
      onResult(request) { result =>
        val (value, outcome) = try update(if js.isUndefined(result) then None else Some(storedValue(result)))
        catch case scala.util.control.NonFatal(error) =>
          transaction.abort()
          throw error
        store.put(record(key, value))
        outcome
      }
    }

  override def readElementRaw(logger: SyncLogger, key: String)(using ec: ExecutionContext): Future[String] =
    inTransaction(IDBTransactionMode.readonly, s"IndexedDB read aborted: $key") { (_, store) =>
      val request = store.get(key)
      onResult(request) { result =>
        if js.isUndefined(result) then throw new NoSuchElementException(s"No IndexedDB value for $key")
        storedValue(result)
      }
    }

  override def underlyingDefinition: SyncDestinationDefinition = definition

  override def readDestinationRaw(logger: SyncLogger)(implicit ec: ExecutionContext): Future[Map[String, String]] =
    inTransaction(IDBTransactionMode.readonly, "IndexedDB cursor transaction aborted") { (_, store) =>
      val promise = Promise[Map[String, String]]()
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
      promise.future
    }

  /** Our store layout: 'id' (string key) -> 'value' (stringified data). */
  private def record(key: String, value: String): js.Dynamic = js.Dynamic.literal(id = key, value = value)

  private def storedValue(result: Any): String = result.asInstanceOf[js.Dynamic].value.asInstanceOf[String]

  /** Completes with `handle(result)` inside the request's success callback, while the transaction is still active. */
  private def onResult[T, A](request: dom.IDBRequest[?, T])(handle: T => A): Future[A] = {
    val promise = Promise[A]()
    request.onsuccess = (_: dom.Event) => promise.tryComplete(Try(handle(request.result)))
    request.onerror = (_: dom.Event) => promise.tryFailure(new RuntimeException(s"IndexedDB request failed: ${request.error.name}"))
    promise.future
  }

  /** Runs one transaction on the store. `start` issues its requests and yields their outcome, which is reported only
    * once the transaction has committed; an abort fails with the outcome's error, else with `abortMessage`.
    */
  private def inTransaction[A](mode: IDBTransactionMode, abortMessage: => String)(
      start: (IDBTransaction, IDBObjectStore) => Future[A])(using ExecutionContext): Future[A] =
    openDatabase().flatMap { db =>
      val committed = Promise[A]()
      val transaction = db.transaction(js.Array(definition.storeName), mode)
      val outcome = Try(start(transaction, transaction.objectStore(definition.storeName))).fold(Future.failed, identity)
      transaction.oncomplete = (_: dom.Event) => {
        db.close()
        committed.tryComplete(outcome.value.getOrElse(Failure(new IllegalStateException(s"$abortMessage: committed without a result"))))
      }
      transaction.onabort = (_: dom.Event) => {
        db.close()
        committed.tryComplete(outcome.value.filter(_.isFailure).getOrElse(Failure(new RuntimeException(abortMessage))))
      }
      committed.future
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
