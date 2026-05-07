package it.evadid.server

import it.evadid.distribution.{ExecutionCommand, ExecutionInfo, ExecutionResult}

import java.sql.{Connection, DriverManager, PreparedStatement, Timestamp}
import scala.util.{Success, Try}

class HandleSQLCommand {

  private def env(name: String): Option[String] =
    Option(System.getenv(name)).map(_.trim).filter(_.nonEmpty)

  private def requiredParam(command: ExecutionCommand, name: String): String =
    command.params.get(name).map(_.trim).filter(_.nonEmpty)
      .getOrElse(throw new IllegalArgumentException(s"Missing required parameter '$name'"))

  private def requiredEnv(name: String): String =
    env(name).getOrElse(throw new IllegalStateException(s"$name is not configured"))

  def handle(command: ExecutionCommand): ExecutionInfo = {
    val host = requiredParam(command, "sqlHost")
    val port = requiredParam(command, "sqlPort")
    val database = requiredParam(command, "sqlDatabase")

    val programId = requiredParam(command, "programId")
    val userId = requiredParam(command, "userId")
    val keyId = requiredParam(command, "keyId")
    val eventTime = requiredParam(command, "eventtime")
    val eventData = command.params.getOrElse("eventdata", "")

    val sqlUser = requiredEnv("SQL_USER")
    val sqlPw = requiredEnv("SQL_PW")

    val jdbcUrl = s"jdbc:postgresql://$host:$port/$database"

    val upsertSql =
      """
        |INSERT INTO "InteractionEvents" (programId, userId, keyId, eventtime, eventdata)
        |VALUES (?, ?, ?, ?, ?)
        |ON CONFLICT (programId, userId, keyId)
        |DO UPDATE SET
        |  eventtime = EXCLUDED.eventtime,
        |  eventdata = EXCLUDED.eventdata
        |""".stripMargin

    val rowsAffected = UsingConnection(jdbcUrl, sqlUser, sqlPw) { conn =>
      val stmt = conn.prepareStatement(upsertSql)
      try {
        stmt.setString(1, programId)
        stmt.setString(2, userId)
        stmt.setString(3, keyId)
        stmt.setTimestamp(4, Timestamp.valueOf(eventTime))
        stmt.setString(5, eventData)
        stmt.executeUpdate()
      } finally {
        stmt.close()
      }
    }

    val result = ExecutionResult(Map("rowsAffected" -> rowsAffected.toString), "", "")
    ExecutionInfo(command, Success(result), None)
  }

  private object UsingConnection {
    def apply[T](jdbcUrl: String, user: String, pw: String)(f: Connection => T): T = {
      val conn = DriverManager.getConnection(jdbcUrl, user, pw)
      try f(conn)
      finally conn.close()
    }
  }
}
