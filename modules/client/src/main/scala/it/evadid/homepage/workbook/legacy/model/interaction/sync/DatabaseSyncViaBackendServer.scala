package it.evadid.homepage.workbook.legacy.model.interaction.sync

import it.evadid.core.util.InfoUtil.datetimeFormattedForLog
import it.evadid.distribution.commandTypes.SQLCommands
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.SyncDestination

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object DatabaseSyncViaBackendServer extends SyncDestination {

  private val cache: mutable.Map[String, String] = mutable.Map.empty

  override def syncTo(key: String, value: String): Unit = {
    cache.update(key, value)

    val context = currentContext
    val request = SQLCommands.SyncToDbRequest(
      programId = context.programId,
      scenarioId = context.scenarioId,
      userId = context.userId,
      eventTime = datetimeFormattedForLog(),
      keyId = key,
      eventData = serializedSyncEvent(key, value)
    )

    SQLCommands.syncToDbCommand.sendCommandTo(context.backend, Logger(), request).onComplete {
      case Success(response) =>
        response.typedResult.foreach(result => println(s"[INFO] synced '$key' to database backend (${result.result.rowsAffected} rows affected)"))
      case Failure(error) => println(s"[ERROR] could not sync '$key' to database backend: ${error.getMessage}")
    }
  }

  override def syncAllFrom(): Map[String, String] = {
    fetchFromBackend(None)
    cache.toMap
  }

  override def syncKeyFrom(key: String): Option[String] = {
    fetchFromBackend(Some(key))
    cache.get(key)
  }

  override def clear(): Unit = {
    cache.clear()

    val context = currentContext
    val request = SQLCommands.ClearDbRequest(
      programId = context.programId,
      scenarioId = context.scenarioId,
      userId = context.userId,
      keyId = None
    )

    SQLCommands.clearDbCommand.sendCommandTo(context.backend, Logger(), request).onComplete {
      case Success(response) =>
        response.typedResult.foreach(result => println(s"[INFO] cleared database backend sync data (${result.result.rowsAffected} rows affected)"))
      case Failure(error) => println(s"[ERROR] could not clear database backend sync data: ${error.getMessage}")
    }
  }

  private def fetchFromBackend(key: Option[String]): Unit = {
    val context = currentContext
    val request = SQLCommands.FetchFromDbRequest(
      programId = context.programId,
      scenarioId = context.scenarioId,
      userId = context.userId,
      keyId = key
    )

    SQLCommands.fetchFromDbCommand.sendCommandTo(context.backend, Logger(), request).onComplete {
      case Success(response) =>
        response.typedResult.foreach { result =>
          result.result.values.foreach { case (fetchedKey, fetchedValue) => cache.update(fetchedKey, fetchedValue) }
          println(s"[INFO] fetched ${result.result.values.size} values from database backend")
        }
      case Failure(error) => println(s"[ERROR] could not fetch from database backend: ${error.getMessage}")
    }
  }

  private def serializedSyncEvent(key: String, value: String): String =
    ujson.Obj(
      "type" -> "syncEvent",
      "name" -> "syncInfo",
      "source" -> "DatabaseSync",
      "key" -> key,
      "data" -> value
    ).render()

  private def currentContext: DatabaseSyncContext = {
    val info = HtmlFullWorkbookApp.fullInfo
    DatabaseSyncContext(
      programId = "edusquirrel",
      scenarioId = info.current.workbookInfo.map(_.loadedWorkbook.workbookId).getOrElse("unknownScenario"),
      userId = info.current.userInfo.map(_.user.id).getOrElse("unknownUser"),
      backend = info.technical.backendServerExecutor
    )
  }

  private case class DatabaseSyncContext(
                                          programId: String,
                                          scenarioId: String,
                                          userId: String,
                                          backend: it.evadid.distribution.clients.ExecutionClient
                                        )
}
