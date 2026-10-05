package it.evadid.homepage.workbook.syncDestination

import it.evadid.core.datastructures.storage.RemoteSyncDataCache.FetchResponse
import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.{ConstructorLikeParserWithJsonElements, Serializer}
import it.evadid.util.logging.LoggingLevel.WARN
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.*
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.sync.destination.*
import it.evadid.workbook.interaction.variable
import it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized
import org.scalajs.dom
import org.scalajs.dom.{IDBDatabase, IDBTransactionMode}

import java.time
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

object LocalIndexedDbStorageSync {
  val instanceForHistory = LocalIndexedDbStorageSync("EvaDidInteractionDB", "variableHistoryStore")
  val instanceForCaching = LocalIndexedDbStorageSync("EvaDidCacheDb", "cache")
}

case class LocalIndexedDbStorageSync(dbName: String, storeName: String) extends SyncDestinationRaw with SyncDestinationHistory {

  private val dbVersion = 1

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val contextToKeySerializer: Serializer[SyncContext] = SyncContext.serializer

  override def isLocal: Boolean = true

  override def shouldBePersistant(): Boolean = true // Now safely persistent!

  // --- Helper: Open DB Connection wrapped in a Scala Future ---
  private def openDatabase(): Future[IDBDatabase] = {
    val promise = Promise[IDBDatabase]()
    val request = dom.window.indexedDB.get.open(dbName, dbVersion)

    def ensureStore(db: IDBDatabase): Unit = {
      if (!db.objectStoreNames.contains(storeName)) {
        // We use a simple layout: 'key' (string) -> 'value' (stringified data)
        // FIX: Cast js.Dynamic.literal to structural option trait to bypass read-only fields
        db.createObjectStore(storeName, js.Dynamic.literal(keyPath = "id").asInstanceOf[dom.IDBCreateObjectStoreOptions])
      }
    }

    request.onupgradeneeded = (event: dom.IDBVersionChangeEvent) => {
      val db = request.result.asInstanceOf[IDBDatabase]
      ensureStore(db)
    }

    request.onsuccess = (_: dom.Event) => {
      val db = request.result.asInstanceOf[IDBDatabase]
      promise.success(db)
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
    val boolFut = storeToRaw(serializedKey, serializedValue)

    boolFut.map(boolRes => {
      SyncSuccess(1, 0, 0, LocalDateTime.now())
    })
  }


  override def fetchAll(
                         logger: SyncLogger,
                         context: UsageContext,
                         formatter: SyncFormatter
                       ): Future[FetchResponse[SyncContext, InteractionVariableHistorySerialized]] = {

    def parseTuple(browserKey: String, browserValue: String): Option[(SyncContext, InteractionVariableHistorySerialized)] = try {
      val syncCtx = contextToKeySerializer.deserialize(browserKey)
      val history = formatter.deserialize(browserValue)
      Some(syncCtx -> history)
    } catch case e: Exception => {
      logger.log(s"LocalIndexedDbStorageSync: Ignore tuple ($browserKey) because it was unparsable: ${e.getMessage}", WARN, Option(false))
      None
    }

    def parseResMap(res: Map[String, String]): Map[SyncContext, InteractionVariableHistorySerialized] = {
      res.toList.flatMap(tup => parseTuple(tup._1, tup._2)).toMap
    }

    readAllRaw(logger).map(strMap => {
      val resMap: Map[SyncContext, InteractionVariableHistorySerialized] = parseResMap(strMap)
      val res = FetchResponse.fromMap[SyncContext, InteractionVariableHistorySerialized](LocalDateTime.now(), resMap, _.lastStateOption.map(_.timestamp))
      logger.logInfo(s"Parsed ${strMap.knownSize} string elements and transformed them to ${resMap.knownSize} fetch response map elements!")
      res
    })
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

  override protected def readAllRaw(): Future[Map[String, String]] = {
    openDatabase().flatMap { db =>
      val promise = Promise[Map[String, String]]()
      val transaction = db.transaction(js.Array(storeName), IDBTransactionMode.readonly)
      val store = transaction.objectStore(storeName)

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
          promise.success(mutableMap)
        }
      }

      request.onerror = (_: dom.Event) => {
        promise.failure(new RuntimeException(s"Cursor parsing failed: ${request.error.name}"))
      }

      promise.future
    }
  }


  override def storeToRaw(keyToWrite: String, valueToWrite: String): Future[Boolean] = {
    openDatabase().flatMap { db =>
      val promise = Promise[Boolean]()
      val transaction = db.transaction(js.Array(storeName), IDBTransactionMode.readwrite)
      val store = transaction.objectStore(storeName)

      // Package data matching our store structure
      val dataEntry = js.Dynamic.literal(id = keyToWrite, value = valueToWrite)
      val request = store.put(dataEntry)

      transaction.oncomplete = (_: dom.Event) => {
        promise.success(true)
      }

      transaction.onerror = (_: dom.Event) => {
        promise.failure(new RuntimeException(transaction.error.name))
      }

      promise.future
    }
  }

  override protected def deserializeFromConstructorLikeString(from: ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult): Option[SyncDestination] = {
    if (from.elementType == this.getClass.getSimpleName) Some(LocalIndexedDbStorageSync(from.jsonPayloads(0), from.jsonPayloads(1)))
    else None
  }

  override protected def serializeToConstructorLikeString(): ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult = {
    ConstructorLikeReadResult(this.getClass.getSimpleName, List(dbName, storeName))
  }

}
