package it.evadid.server

import it.evadid.distribution.commandTypes.SQLCommands.{SyncToDbRequest, SyncToDbResponse}
import it.evadid.util.Logger

import java.sql.{Connection, DriverManager, Timestamp}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object HandleSQLCommand {

  private def env(name: String): Option[String] =
    Option(System.getenv(name)).map(_.trim).filter(_.nonEmpty)

  private def requiredEnv(name: String): String =
    env(name).getOrElse(throw new IllegalStateException(s"$name is not configured"))

  def handleSyncToDbRequest(request: SyncToDbRequest, logger: Logger): Future[SyncToDbResponse] = Future {
    val host = requiredEnv("SQL_HOST")
    val port = requiredEnv("SQL_PORT")
    val database = requiredEnv("SQL_DATABASE")
    val sqlUser = requiredEnv("SQL_USER")
    val sqlPw = requiredEnv("SQL_PW")
    val jdbcUrl = s"jdbc:postgresql://$host:$port/$database"

    val upsertSql =
      """
        |INSERT INTO "InteractionEvents" ("programId", "userId", "keyId", "eventtime", "eventdata")
        |VALUES (?, ?, ?, ?, ?)
        |ON CONFLICT ("programId", "userId", "keyId")
        |DO UPDATE SET
        |  "eventtime" = EXCLUDED."eventtime",
        |  "eventdata" = EXCLUDED."eventdata"
        |""".stripMargin

    logger.logInfo(s"syncing key '${request.keyId}' to database for program '${request.programId}' and user '${request.userId}'")
    val rowsAffected = UsingConnection(jdbcUrl, sqlUser, sqlPw) { conn =>
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
    SyncToDbResponse(rowsAffected)
  }(using ExecutionContext.global)

  private object UsingConnection {
    def apply[T](jdbcUrl: String, user: String, pw: String)(f: Connection => T): T = {
      val conn = DriverManager.getConnection(jdbcUrl, user, pw)
      try f(conn)
      finally conn.close()
    }
  }
}
