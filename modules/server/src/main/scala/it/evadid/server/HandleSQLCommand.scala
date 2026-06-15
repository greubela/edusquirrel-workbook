package it.evadid.server

import it.evadid.distribution.commandTypes.SQLCommands.{SyncToDbRequest, SyncToDbResponse}
import it.evadid.util.Logger

import java.sql.{Connection, DriverManager, SQLException, Timestamp}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

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
