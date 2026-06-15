package it.evadid.server

import it.evadid.distribution.commandTypes.SQLCommands.{SyncToDbRequest, SyncToDbResponse}
import it.evadid.util.Logger

import java.sql.{Connection, DriverManager, SQLException, Timestamp}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object HandleSQLCommand {

  private[server] final case class DatabaseConfig(
                                                    host: String,
                                                    port: String,
                                                    database: String,
                                                    user: String,
                                                    password: String
                                                  ) {
    val jdbcUrl: String = s"jdbc:postgresql://$host:$port/$database"
  }

  private[server] trait SyncDbExecutor {
    def upsert(config: DatabaseConfig, request: SyncToDbRequest): Int
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

    logger.logInfo(s"syncing key '${request.keyId}' to database for program '${request.programId}' and user '${request.userId}'")
    SyncToDbResponse(executor.upsert(config, request))
  }

  def handleSyncToDbRequest(request: SyncToDbRequest, logger: Logger): Future[SyncToDbResponse] = Future {
    syncToDb(request, logger, name => Option(System.getenv(name)), JdbcSyncDbExecutor)
  }(using ExecutionContext.global)

  private object JdbcSyncDbExecutor extends SyncDbExecutor {
    private val upsertSql =
      """
        |INSERT INTO "InteractionEvents" ("programId", "userId", "keyId", "eventtime", "eventdata")
        |VALUES (?, ?, ?, ?, ?)
        |ON CONFLICT ("programId", "userId", "keyId")
        |DO UPDATE SET
        |  "eventtime" = EXCLUDED."eventtime",
        |  "eventdata" = EXCLUDED."eventdata"
        |""".stripMargin

    override def upsert(config: DatabaseConfig, request: SyncToDbRequest): Int =
      UsingConnection(config.jdbcUrl, config.user, config.password) { conn =>
        val stmt = conn.prepareStatement(upsertSql)
        try {
          stmt.setString(1, request.programId)
          stmt.setString(2, request.userId)
          stmt.setString(3, request.keyId)
          stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(request.eventTime)))
          stmt.setString(5, request.eventData)
          stmt.executeUpdate()
        } finally {
          stmt.close()
        }
      }
  }

  private object UsingConnection {
    def apply[T](jdbcUrl: String, user: String, pw: String)(f: Connection => T): T = {
      ensurePostgresDriverAvailable(jdbcUrl)
      val conn =
        try {
          DriverManager.getConnection(jdbcUrl, user, pw)
        } catch {
          case ex: SQLException =>
            throw new SQLException(
              s"Could not connect to PostgreSQL database at $jdbcUrl as user '$user': ${ex.getMessage}",
              ex.getSQLState,
              ex.getErrorCode,
              ex
            )
        }
      try f(conn)
      finally conn.close()
    }

    private def ensurePostgresDriverAvailable(jdbcUrl: String): Unit =
      try {
        Class.forName("org.postgresql.Driver")
      } catch {
        case ex: ClassNotFoundException =>
          throw new IllegalStateException(
            s"PostgreSQL JDBC driver is not available on the server classpath for $jdbcUrl. " +
              "Add org.postgresql:postgresql to the server runtime dependencies.",
            ex
          )
      }
  }
}
