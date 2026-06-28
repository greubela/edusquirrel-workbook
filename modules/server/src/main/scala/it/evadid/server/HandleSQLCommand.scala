package it.evadid.server

import it.evadid.distribution.commandTypes.SQLCommands.*
import it.evadid.util.Logger
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.sync.SyncFormatter.{RichInteractionVariableFormatter, RichInteractionVariableHistorySerialized}
import it.evadid.workbook.model.interaction.sync.SyncInformation.SyncSuccess
import it.evadid.workbook.model.interaction.variable.InteractionVariableHistorySerialized

import java.sql.*
import java.time.LocalDateTime
import scala.collection.mutable

object HandleSQLCommand {

  // todo: make context sensitive and log writing to DB (may read from cache if no new logging received). Use AsyncDataCache (?)

  // surrounding

  // Fetch


  // Clear



  // Store



  /*
    // Helper


    private[server] def fetchFromDb(
                                     request: FetchFromDbRequest,
                                     logger: Logger,
                                     envProvider: String => Option[String],
                                     executor: SyncDbExecutor
                                   ): DbFetchResponse = {
      val config = readDatabaseConfig(envProvider, request)
      logger.logInfo(s"fetching database sync data for program '${request.context.programId}', scenario '${request.context.scenarioId}', user '${request.context.userId}', key '${request.context.keyForSerialisation}'")
      //val res: DbResponse = DbResponse()
      val map: Map[String, String] = executor.fetchAllKeys(config, request, logger) // keyForSynchronisation -> serializedData


      val historySerialized: Option[InteractionVariableHistorySerialized] = map
        .get(request.context.keyForSerialisation)
        .map(DefaultSerializer.serializerInteractionVariableHistoryIgnoreErrors.deserialize)
      if (historySerialized.isEmpty) {
        logger.logWarn("No history found for key: " + request.context.keyForSerialisation + " in database!")
        DbFetchResponse(InteractionVariableHistorySerialized(Set()))
      } else {
        DbFetchResponse(historySerialized.get)
      }
    }

    def handleFetchFromDbRequest(request: FetchFromDbRequest, logger: Logger): Future[DbFetchResponse] = Future {
      fetchFromDb(request, logger, name => Option(System.getenv(name)), JdbcSyncDbExecutor)
    }(using ExecutionContext.global)

    private[server] def clearDb(
                                 request: ClearUsageInDbRequest,
                                 logger: Logger,
                                 envProvider: String => Option[String],
                                 executor: SyncDbExecutor
                               ): DbChangeResponse = {
      val config = readDatabaseConfig(envProvider, request)
      DbChangeResponse(executor.clear(config, request, logger))
    }

    def handleClearDbRequest(request: ClearUsageInDbRequest, logger: Logger): Future[DbChangeResponse] = Future {
      clearDb(request, logger, name => Option(System.getenv(name)), JdbcSyncDbExecutor)
    }(using ExecutionContext.global)

    private object JdbcSyncDbExecutor extends SyncDbExecutor {


      override def fetchAllKeys(config: DatabaseConfig, request: FetchFromDbRequest, logger: Logger): Map[String, String] =
        UsingConnection(config.jdbcUrl, config.user, config.password) { conn =>
          val stmt = conn.prepareStatement(selectEventsForUserSql)
          try {
            bindUserScope(stmt, request.context)
            val rs = stmt.executeQuery()
            try {
              val values = scala.collection.mutable.LinkedHashMap.empty[String, String]
              while (rs.next()) {
                parseSyncEvent(rs.getString("serializeddata"))
                  .filter(_.key == request.context.keyForSerialisation)
                  .foreach { event =>
                    if (!values.contains(event.key)) values.put(event.key, event.data)
                  }
              }
              values.toMap
              // todo
            } finally {
              rs.close()
            }
          } catch {
            case e: Exception =>
              logger.logError(s"Error fetching from database: ${e.getMessage}")
              throw e
          } finally {
            stmt.close()
          }
        }

      override def clear(config: DatabaseConfig, request: ClearUsageInDbRequest, logger: Logger): Int =
        UsingConnection(config.jdbcUrl, config.user, config.password) { conn =>
          val idsToDelete = eventIdsForKey(conn, request, request.context.keyForSerialisation, logger)
          deleteEventsById(conn, idsToDelete, logger)
        }


      private def clearUserScope(conn: Connection, request: ClearUsageInDbRequest, logger: Logger): Int = {
        val stmt = conn.prepareStatement(clearUserScopeSql)
        try {
          bindUserScope(stmt, request.context)
          stmt.executeUpdate()
        } catch {
          case e: Exception =>
            logger.logError(s"Error clearing database sync data: ${e.getMessage}")
            throw e
        } finally {
          stmt.close()
        }
      }

      private def eventIdsForKey(conn: Connection, request: ClearUsageInDbRequest, key: String, logger: Logger): List[Long] = {
        val stmt = conn.prepareStatement(selectEventsForUserSql)
        try {
          bindUserScope(stmt, request.context)
          val rs = stmt.executeQuery()
          try {
            val ids = scala.collection.mutable.ListBuffer.empty[Long]
            while (rs.next()) {
              parseSyncEvent(rs.getString("serializeddata"))
                .filter(_.key == key)
                .foreach(_ => ids += rs.getLong("id"))
            }
            ids.toList
          } finally {
            rs.close()
          }
        } catch {
          case e: Exception =>
            logger.logError(s"Error finding database sync events to clear: ${e.getMessage}")
            throw e
        } finally {
          stmt.close()
        }
      }

      private def deleteEventsById(conn: Connection, ids: List[Long], logger: Logger): Int = {
        if (ids.isEmpty) 0
        else {
          val placeholders = ids.map(_ => "?").mkString(", ")
          val stmt = conn.prepareStatement(s"DELETE FROM `events` WHERE `id` IN ($placeholders)")
          try {
            ids.zipWithIndex.foreach { case (id, index) => stmt.setLong(index + 1, id) }
            stmt.executeUpdate()
          } catch {
            case e: Exception =>
              logger.logError(s"Error clearing database sync events by id: ${e.getMessage}")
              throw e
          } finally {
            stmt.close()
          }
        }
      }

      private[server] def parseSyncEvent(serializedData: String): Option[StoredSyncEvent] =
        Try {
          val obj = ujson.read(serializedData).obj
          for {
            key <- obj.get("key").map(_.str)
            data <- obj.get("data").map(_.str)
          } yield StoredSyncEvent(key, data)
        }.toOption.flatten

      private[server] case class StoredSyncEvent(key: String, data: String)
    }

    private object UsingConnection {
      def apply[T](jdbcUrl: String, user: String, pw: String)(f: Connection => T): T = {
        ensureMysqlDriverAvailable(jdbcUrl)

      }

      private def ensureMysqlDriverAvailable(jdbcUrl: String): Unit =
        try {
          Class.forName("com.mysql.cj.jdbc.Driver")
        } catch {
          case ex: ClassNotFoundException =>
            throw new IllegalStateException(
              s"MySQL JDBC driver is not available on the server classpath for $jdbcUrl. " +
                "Add com.mysql:mysql-connector-j to the server runtime dependencies.",
              ex
            )
        }
    }

   */
}
