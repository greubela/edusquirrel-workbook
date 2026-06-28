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


  def clearUsageStatement(connection: Connection, config: DatabaseConfig, request: ClearUsageInDbRequest, logger: Logger): PreparedStatement = {
    val sql: String =
      """
        |DELETE FROM `events`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
        |""".stripMargin

    val stmt: PreparedStatement = connection.prepareStatement(sql)

    stmt.setString(1, request.usageContext.programId)
    stmt.setString(2, request.usageContext.scenarioId)
    stmt.setString(3, request.usageContext.userId)
    stmt
  }

  def clearUsage(connection: Connection, config: DatabaseConfig, request: ClearUsageInDbRequest, logger: Logger): SyncSuccess = {
    val config = readConfig(request)
    logger.logInfo(s"clearing database sync data for program '${request.usageContext.programId}', scenario '${request.usageContext.scenarioId}', user '${request.usageContext.userId}'")
    val statement = clearUsageStatement(config.newConnection(), config, request, logger)
    try {
      SyncSuccess(0, 0, statement.executeUpdate(), LocalDateTime.now())
    } catch case e: Exception => {
      statement.close()
      logger.logError(s"Error fetching from database: ${e.getMessage}")
      throw e
    }
  }

  def clearUsage(request: ClearUsageInDbRequest, logger: Logger): SyncSuccess = {
    val config = readConfig(request)
    if (request.limitToKey.isEmpty) {
      clearUsage(config.newConnection(), config, request, logger)
    } else {
      val list = fetchAllEventsAsList(config, request.usageContext, request.formatter, logger)
      deleteEventsById(config.newConnection(), list.map(_.eventId.toLong), logger)
    }
  }



  // Store

  def upsertStatement(connection: Connection, config: DatabaseConfig, request: StoreToDbRequest): PreparedStatement = {
    // todo: prevent doubling entries...
    val sql =
      """
        |INSERT INTO `events` (`programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata`)
        |VALUES (?, ?, ?, ?, ?)
        |ON DUPLICATE KEY UPDATE
        |  `timestamp` = VALUES(`timestamp`),
        |  `serializeddata` = VALUES(`serializeddata`)
        |""".stripMargin

    val stmt = connection.prepareStatement(sql)

    val maxTimestamp: LocalDateTime = request.request.history.states.toList.maxBy(_.timestamp).timestamp

    stmt.setString(1, request.request.syncContext.programId)
    stmt.setString(2, request.request.syncContext.scenarioId)
    stmt.setString(3, request.request.syncContext.userId)
    stmt.setTimestamp(4, Timestamp.valueOf(maxTimestamp))
    stmt.setString(5, request.serializedValueString)
    stmt
  }

  def upsert(connection: Connection, config: DatabaseConfig, request: StoreToDbRequest, logger: Logger): SyncSuccess = {
    try if (request.request.history.states.nonEmpty) {
      val stmt = upsertStatement(connection, config, request)
      val changed = stmt.executeUpdate()
      SyncSuccess(changed, 0, 0, LocalDateTime.now())
    } else {
      SyncSuccess(0, 0, 0, LocalDateTime.now())
    }
    catch case e: Exception => {
      connection.close()
      logger.logError(s"Error syncing to database: ${e.getMessage}")
      throw e
    }
  }

  def handleStoreToDbRequest(request: StoreToDbRequest, logger: Logger): SyncSuccess = {
    logger.logInfo(s"syncing key '${request.request.syncContext.keyForSerialisation}' to database for program '${request.request.syncContext.programId}' and user '${request.request.syncContext.userId}'")
    val config = readConfig(request)
    upsert(config.newConnection(), config, request, logger)
  }


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
