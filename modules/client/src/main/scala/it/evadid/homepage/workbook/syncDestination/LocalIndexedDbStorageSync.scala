package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.LoggingLevel.WARN
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import org.scalajs.dom
import org.scalajs.dom.{IDBDatabase, IDBTransactionMode}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

object LocalIndexedDbStorageSync {
  val instance = LocalIndexedDbStorageSync()
}

case class LocalIndexedDbStorageSync() extends SyncDestination {

  private val dbName = "EvaDidInteractionDB"
  private val storeName = "variableHistoryStore"
  private val dbVersion = 1

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val contextToKeySerializer: Serializer[SyncContext] = SyncContext.serializer

  override def isLocal: Boolean = true

  override def shouldBePersistant(): Boolean = true // Now safely persistent!

  // --- Helper: Open DB Connection wrapped in a Scala Future ---
  private def openDatabase(): Future[IDBDatabase] = {
    val promise = Promise[IDBDatabase]()
    val request = dom.window.indexedDB.get.open(dbName, dbVersion)

    request.onupgradeneeded = (event: dom.IDBVersionChangeEvent) => {
      val db = request.result.asInstanceOf[IDBDatabase]
      if (!db.objectStoreNames.contains(storeName)) {
        // We use a simple layout: 'key' (string) -> 'value' (stringified data)
        // FIX: Cast js.Dynamic.literal to structural option trait to bypass read-only fields
        db.createObjectStore(storeName, js.Dynamic.literal(keyPath = "id").asInstanceOf[dom.IDBCreateObjectStoreOptions])
      }
    }

    request.onsuccess = (_: dom.Event) => {
      promise.success(request.result.asInstanceOf[IDBDatabase])
    }

    request.onerror = (_: dom.Event) => {
      promise.failure(new RuntimeException(s"Failed to open IndexedDB: ${request.error.name}"))
    }

    promise.future
  }

  // --- Core API Implementations ---

  override def storeTo(
                        logger: SyncLogger,
                        context: SyncContext,
                        history: InteractionVariableHistorySerialized,
                        formatter: SyncFormatter
                      ): Future[SyncSuccess] = {

    val serializedKey = contextToKeySerializer.serialize(context)
    val serializedValue = formatter.serialize(context, history)

    openDatabase().flatMap { db =>
      val promise = Promise[SyncSuccess]()
      val transaction = db.transaction(js.Array(storeName), IDBTransactionMode.readwrite)
      val store = transaction.objectStore(storeName)

      // Package data matching our store structure
      val dataEntry = js.Dynamic.literal(id = serializedKey, value = serializedValue)
      val request = store.put(dataEntry)

      transaction.oncomplete = (_: dom.Event) => {
        promise.success(SyncSuccess(1, 0, 0, LocalDateTime.now()))
      }

      transaction.onerror = (_: dom.Event) => {
        logger.logExceptionWarn("LocalIndexedDbStorageSync, error inside storeTo transaction", new RuntimeException(transaction.error.name))
        promise.failure(new RuntimeException(transaction.error.name))
      }

      promise.future
    }
  }

  override def fetchAll(
                         logger: SyncLogger,
                         context: UsageContext,
                         formatter: SyncFormatter
                       ): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = {

    openDatabase().flatMap { db =>
      val promise = Promise[FetchResponse[SyncContext, InteractionVariableHistorySerialized]]()
      val transaction = db.transaction(js.Array(storeName), IDBTransactionMode.readonly)
      val store = transaction.objectStore(storeName)

      // Use a cursor to step through the records asynchronously
      val request = store.openCursor()
      var mutableMap = Map[SyncContext, InteractionVariableHistorySerialized]()

      request.onsuccess = (event: dom.Event) => {
        val cursor = request.result.asInstanceOf[dom.IDBCursorWithValue[js.Any]]
        if (cursor != null) {
          val record = cursor.value.asInstanceOf[js.Dynamic]
          val browserKey = record.id.asInstanceOf[String]
          val browserValue = record.value.asInstanceOf[String]

          try {
            val syncCtx = contextToKeySerializer.deserialize(browserKey)
            val history = formatter.deserialize(browserValue)
            mutableMap += (syncCtx -> history)
          } catch {
            case e: Exception =>
              logger.log(s"LocalIndexedDbStorageSync: Ignore tuple ($browserKey) because it was unparsable: ${e.getMessage}", WARN, Option(false))
          }
          cursor.continue()
        } else {
          // Cursor finished iterating
          val fetchRes = FetchResponse.fromMap[SyncContext, InteractionVariableHistorySerialized](
            LocalDateTime.now(),
            mutableMap,
            _.lastStateOption.map(_.timestamp)
          )
          promise.success(fetchRes)
        }
      }

      request.onerror = (_: dom.Event) => {
        promise.failure(new RuntimeException(s"Cursor parsing failed: ${request.error.name}"))
      }

      promise.future
    }
  }

  override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] = {
    val serializedKey = contextToKeySerializer.serialize(context)
    openDatabase().flatMap { db =>
      val promise = Promise[SyncSuccess]()
      val transaction = db.transaction(js.Array(storeName), IDBTransactionMode.readwrite)
      val store = transaction.objectStore(storeName)

      store.delete(serializedKey)

      transaction.oncomplete = (_: dom.Event) => promise.success(SyncSuccess(0, 0, 1, LocalDateTime.now()))
      transaction.onerror = (_: dom.Event) => promise.failure(new RuntimeException(transaction.error.name))
      promise.future
    }
  }

  override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] = {
    openDatabase().flatMap { db =>
      val promise = Promise[SyncSuccess]()
      val transaction = db.transaction(js.Array(storeName), IDBTransactionMode.readwrite)
      val store = transaction.objectStore(storeName)

      store.clear()

      transaction.oncomplete = (_: dom.Event) => promise.success(SyncSuccess(0, 0, 0, LocalDateTime.now()))
      transaction.onerror = (_: dom.Event) => promise.failure(new RuntimeException(transaction.error.name))
      promise.future
    }
  }

  override def toString: String = "LocalIndexedDbStorageSync()"

}
