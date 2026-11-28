package it.evadid.server.database

import it.evadid.core.util.UtcTime

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserCredentials}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.ParsingContext
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.logging.Logger
import java.sql.{Connection, ResultSet, Timestamp}
import java.time.LocalDateTime
import scala.util.Try
import scala.util.control.NonFatal
import upickle.default.*

/** Reads and writes user accounts on one caller-owned connection. */
final class UserAccountRows(connection: Connection, logger: Logger) {

  def mayCreateAccount(requestedUser: User): Boolean = {
    val findMail = findUserWithMail(requestedUser.mail)
    val findId = readUserInfoFromDb(requestedUser.id)
    if (findMail.nonEmpty) {
      logger.logWarn(s"Mail ${requestedUser.mail} already associated with users ${findMail.map(_.user.id).mkString(", ")}!")
      false
    } else if (findId.nonEmpty) {
      logger.logWarn(s"Id ${requestedUser.id} already in use (associated with mail ${findId.head.user.mail})!")
      false
    } else {
      true
    }
  }

  private val statements = SqlStatements(connection, logger)

  private val userColumns = List("id", "name", "mail", "config")

  /** The named columns of the current row; an unreadable row is reported and skipped. */
  private def columns(names: Seq[String])(row: ResultSet): Option[Seq[String]] =
    Try(names.map(row.getString)).fold(error => {
      logger.logWarn(s"Could not parse element from result set because of the following error: ${ParsingContext.reason(error)}")
      None
    }, Some(_))

  /** Columns in [[userColumns]] order; malformed settings fall back to the current defaults. */
  private def parseUser(columns: Seq[String]): UserInDatabase = {
    val user = User(columns(1), columns(0), columns(2))
    val userConfig = Try(read[UserConfig](columns(3))).recover {
      case NonFatal(error) =>
        logger.logWarn(s"Discarded invalid UserConfig; using current defaults: ${ParsingContext.reason(error)}")
        UserConfig.defaults(isLocalAccount = false)
    }.get
    UserInDatabase(user, userConfig)
  }

  /** Users whose fixed key column equals `value`, bound as a statement parameter. */
  private def selectUsers(keyColumn: "id" | "mail", value: String): Seq[UserInDatabase] =
    statements.query(s"SELECT `id`, `name`, `mail`, `config` FROM `user` WHERE `$keyColumn` = ?", value)(columns(userColumns))
      .flatten.map(parseUser)

  def findAccessTokens(userId: String): Option[SingleAccessToken] =
    statements.query("SELECT `userId`, `token`, `tokenExpires` FROM `loginTokens` WHERE `userId` = ?", userId)(
      columns(List("userId", "token", "tokenExpires"))).flatten.headOption.flatMap { columns =>
      Try {
        require(Option(columns(1)).exists(_.trim.nonEmpty), "Empty login code")
        SingleAccessToken(columns(1), UserAccountRows.parseDatabaseTimestamp(columns(2)))
      }.fold(_ => {
        logger.logWarn("Discarded an invalid stored login code; a new code can be requested.")
        None
      }, Some(_))
    }

  def findUserWithMail(mail: String): Seq[UserInDatabase] = selectUsers("mail", mail.toLowerCase)

  def readUserInfoFromDb(userId: String): Option[UserInDatabase] = selectUsers("id", userId).headOption

  def ensureAndGetUserTokenFromDb(userId: String, logger: Logger): Option[SingleAccessToken] = {

    def createNewToken(reason: String): Option[SingleAccessToken] = {
      val token = SingleAccessToken.generateSecureToken()
      upsertToken(userId, token)
      logger.logInfo(s"Created new token (${reason}) for user " + userId + " (expires at " + token.expires + ")")
      Some(token)
    }

    val userInDb = readUserInfoFromDb(userId)
    val token = findAccessTokens(userId)
    (userInDb, token) match {
      case (None, _) =>
        logger.logWarn("Cannot ensure token since user is unknown!")
        None
      case (_, None) => createNewToken("missing or invalid")
      case (_, Some(stored)) if stored.expires.isBefore(UtcTime.now()) => createNewToken(s"old expired at ${stored.expires}")
      case _ => token
    }
  }

  def upsertToken(userId: String, token: SingleAccessToken): Int = statements.update(
    """INSERT INTO `loginTokens` (`userId`, `token`, `tokenExpires`)
      |VALUES (?, ?, ?)
      |ON DUPLICATE KEY UPDATE
      |  `token` = VALUES(`token`),
      |  `tokenExpires` = VALUES(`tokenExpires`)""".stripMargin,
    userId, token.token, Timestamp.valueOf(token.expires))

  def upsertUser(user: User, userConfig: UserConfig): Int = statements.update(
    """INSERT INTO `user` (`id`, `name`, `mail`, `config`)
      |VALUES (?, ?, ?, ?)
      |ON DUPLICATE KEY UPDATE
      |  `name` = VALUES(`name`),
      |  `mail` = VALUES(`mail`),
      |  `config` = VALUES(`config`)""".stripMargin,
    user.id, user.name, user.mail.toLowerCase, userConfig.withoutCredentials.toJson)

}

object UserAccountRows {
  /** Reads a JDBC timestamp text such as `2026-10-13 01:40:00.123456`; other formats are rejected. */
  private[server] def parseDatabaseTimestamp(databaseTimestampString: String): LocalDateTime =
    Timestamp.valueOf(databaseTimestampString).toLocalDateTime
}
