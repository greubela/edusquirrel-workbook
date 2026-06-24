package it.evadid.server

import it.evadid.distribution.commandTypes.SQLCommands.{ClearDbRequest, ClearDbResponse, FetchFromDbRequest, FetchFromDbResponse, SyncToDbRequest, SyncToDbResponse}
import it.evadid.util.Logger

import java.sql.{Connection, DriverManager, SQLException, Timestamp}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object HandleSQLCommand {

  private[server] final case class DatabaseConfig(
                                                    host: String,
                                                    port: String,
                                                    database: String,
                                                    user: String,
                                                    password: String
                                                  ) {
    val jdbcUrl: String = s"jdbc:mysql://$host:$port/$database"
  }

  private[server] trait SyncDbExecutor {
    def upsert(config: DatabaseConfig, request: SyncToDbRequest, logger: Logger): Int

    def fetch(config: DatabaseConfig, request: FetchFromDbRequest, logger: Logger): Map[String, String]

    def clear(config: DatabaseConfig, request: ClearDbRequest, logger: Logger): Int
  }

  private def env(name: String, envProvider: String => Option[String]): Option[String] =
    envProvider(name).map(_.trim).filter(_.nonEmpty)

  private def requiredEnv(name: String, envProvider: String => Option[String]): String =
    env(name, envProvider).getOrElse(throw new IllegalStateException(s"$name is not configured"))

  private[server] def readDatabaseConfig(envProvider: String => Option[String]): DatabaseConfig =
    DatabaseConfig(
      host = requiredEnv("SQL_HOST", envProvider),
      port = requiredEnv("SQL_PORT", envProvider),
      database = requiredEnv("SQL_DATABASE", envProvider),
      user = requiredEnv("SQL_USER", envProvider),
      password = requiredEnv("SQL_PW", envProvider)
    )

  private[server] def syncToDb(
                                request: SyncToDbRequest,
                                logger: Logger,
                                envProvider: String => Option[String],
                                executor: SyncDbExecutor
                              ): SyncToDbResponse = {
    val config = readDatabaseConfig(envProvider)

    //logger.logInfo(s"database config: $config")

    logger.logInfo(s"syncing key '${request.keyId}' to database for program '${request.programId}' and user '${request.userId}'")
    SyncToDbResponse(executor.upsert(config, request, logger))
  }

  def handleSyncToDbRequest(request: SyncToDbRequest, logger: Logger): Future[SyncToDbResponse] = Future {
    syncToDb(request, logger, name => Option(System.getenv(name)), JdbcSyncDbExecutor)
  }(using ExecutionContext.global)

  private[server] def fetchFromDb(
                                   request: FetchFromDbRequest,
                                   logger: Logger,
                                   envProvider: String => Option[String],
                                   executor: SyncDbExecutor
                                 ): FetchFromDbResponse = {
    val config = readDatabaseConfig(envProvider)
    logger.logInfo(s"fetching database sync data for program '${request.programId}', scenario '${request.scenarioId}', user '${request.userId}', key '${request.keyId.getOrElse("*")}'")
    FetchFromDbResponse(executor.fetch(config, request, logger))
  }

  def handleFetchFromDbRequest(request: FetchFromDbRequest, logger: Logger): Future[FetchFromDbResponse] = Future {
    fetchFromDb(request, logger, name => Option(System.getenv(name)), JdbcSyncDbExecutor)
  }(using ExecutionContext.global)

  private[server] def clearDb(
                               request: ClearDbRequest,
                               logger: Logger,
                               envProvider: String => Option[String],
                               executor: SyncDbExecutor
                             ): ClearDbResponse = {
    val config = readDatabaseConfig(envProvider)
    logger.logInfo(s"clearing database sync data for program '${request.programId}', scenario '${request.scenarioId}', user '${request.userId}', key '${request.keyId.getOrElse("*")}'")
    ClearDbResponse(executor.clear(config, request, logger))
  }

  def handleClearDbRequest(request: ClearDbRequest, logger: Logger): Future[ClearDbResponse] = Future {
    clearDb(request, logger, name => Option(System.getenv(name)), JdbcSyncDbExecutor)
  }(using ExecutionContext.global)

  private object JdbcSyncDbExecutor extends SyncDbExecutor {
    private val upsertSql =
      """
        |INSERT INTO `events` (`programid`, `scenarioid`, `userid`, `timestamp`, `serializeddata`)
        |VALUES (?, ?, ?, ?, ?)
        |ON DUPLICATE KEY UPDATE
        |  `timestamp` = VALUES(`timestamp`),
        |  `serializeddata` = VALUES(`serializeddata`)
        |""".stripMargin

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override def upsert(config: DatabaseConfig, request: SyncToDbRequest, logger: Logger): Int =
      UsingConnection(config.jdbcUrl, config.user, config.password) { conn =>
        val stmt = conn.prepareStatement(upsertSql)
        try {
          stmt.setString(1, request.programId)
          stmt.setString(2, request.scenarioId)
          stmt.setString(3, request.userId)
          stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(request.eventTime, formatter)))
          stmt.setString(5, request.eventData)
          stmt.executeUpdate()
        } catch {
          case e: Exception => {
            logger.logError(s"Error syncing to database: ${e.getMessage}")
            throw e
          }
        } finally {
          stmt.close()
        }
      }


    override def fetch(config: DatabaseConfig, request: FetchFromDbRequest, logger: Logger): Map[String, String] =
      UsingConnection(config.jdbcUrl, config.user, config.password) { conn =>
        val stmt = conn.prepareStatement(selectEventsForUserSql)
        try {
          bindUserScope(stmt, request.programId, request.scenarioId, request.userId)
          val rs = stmt.executeQuery()
          try {
            val values = scala.collection.mutable.LinkedHashMap.empty[String, String]
            while (rs.next()) {
              parseSyncEvent(rs.getString("serializeddata"))
                .filter(event => request.keyId.forall(_ == event.key))
                .foreach { event =>
                  if (!values.contains(event.key)) values.put(event.key, event.data)
                }
            }
            values.toMap
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

    override def clear(config: DatabaseConfig, request: ClearDbRequest, logger: Logger): Int =
      UsingConnection(config.jdbcUrl, config.user, config.password) { conn =>
        request.keyId match {
          case None => clearUserScope(conn, request, logger)
          case Some(key) =>
            val idsToDelete = eventIdsForKey(conn, request, key, logger)
            deleteEventsById(conn, idsToDelete, logger)
        }
      }

    private val selectEventsForUserSql =
      """
        |SELECT `id`, `serializeddata`
        |FROM `events`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
        |ORDER BY `timestamp` DESC
        |""".stripMargin

    private val clearUserScopeSql =
      """
        |DELETE FROM `events`
        |WHERE `programid` = ? AND `scenarioid` = ? AND `userid` = ?
        |""".stripMargin

    private def bindUserScope(stmt: java.sql.PreparedStatement, programId: String, scenarioId: String, userId: String): Unit = {
      stmt.setString(1, programId)
      stmt.setString(2, scenarioId)
      stmt.setString(3, userId)
    }

    private def clearUserScope(conn: Connection, request: ClearDbRequest, logger: Logger): Int = {
      val stmt = conn.prepareStatement(clearUserScopeSql)
      try {
        bindUserScope(stmt, request.programId, request.scenarioId, request.userId)
        stmt.executeUpdate()
      } catch {
        case e: Exception =>
          logger.logError(s"Error clearing database sync data: ${e.getMessage}")
          throw e
      } finally {
        stmt.close()
      }
    }

    private def eventIdsForKey(conn: Connection, request: ClearDbRequest, key: String, logger: Logger): List[Long] = {
      val stmt = conn.prepareStatement(selectEventsForUserSql)
      try {
        bindUserScope(stmt, request.programId, request.scenarioId, request.userId)
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
      val conn =
        try {
          DriverManager.getConnection(jdbcUrl, user, pw)
        } catch {
          case ex: SQLException =>
            throw new SQLException(
              s"Could not connect to MySQL database at $jdbcUrl as user '$user': ${ex.getMessage}",
              ex.getSQLState,
              ex.getErrorCode,
              ex
            )
        }
      try f(conn)
      finally conn.close()
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
}
