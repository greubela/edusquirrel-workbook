package it.evadid.server.account

import it.evadid.core.datastructures.user.{User, UserConfig}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.util.io.ParsingContext
import it.evadid.server.database.{Database, SqlStatements}
import it.evadid.util.logging.Logger
import java.sql.{Connection, ResultSet}
import java.time.Instant
import scala.util.Try
import upickle.default.*

/** Reads and writes user accounts and login codes on one caller-owned connection. Mail addresses are unique and
  * stored in lower case.
  */
final class AccountRows(connection: Connection, logger: Logger) {
  import AccountRows.*

  private val statements = SqlStatements(connection, logger)

  def transaction[A](run: => A): A = Database.transaction(connection)(run)

  def findUserWithMail(mail: String): Option[UserInDatabase] =
    statements.query(selectUserByMail, normalizedMail(mail))(readUser).flatten.headOption

  def readUserInfoFromDb(userId: String): Option[UserInDatabase] =
    statements.query(selectUserById, userId)(readUser).flatten.headOption

  /** Inserts a new account; false when its ID or mail address is already taken. */
  def insertUser(user: User, userConfig: UserConfig): Boolean =
    statements.updateUnlessDuplicate(insertUserSql, user.id, user.name, normalizedMail(user.mail), userConfig.withoutCredentials.toJson)

  /** Updates an existing account; false when it does not exist or the new mail address belongs to another account. */
  def updateUser(user: User, userConfig: UserConfig): Boolean =
    statements.updateUnlessDuplicate(updateUserSql, user.name, normalizedMail(user.mail), userConfig.withoutCredentials.toJson, user.id)

  /** The stored login code, if one exists and is readable; never creates one. */
  def getTokenFromDb(userId: String): Option[SingleAccessToken] =
    statements.query(selectToken, userId)(readToken).flatten.headOption

  /** A live login code for an existing account, replacing a missing, unreadable or expired one. */
  def ensureTokenInDb(userId: String): Option[SingleAccessToken] =
    readUserInfoFromDb(userId) match {
      case None =>
        logger.logWarn("Cannot ensure a login code since the user is unknown!")
        None
      case Some(_) => getTokenFromDb(userId).filterNot(_.expires.isBefore(Instant.now())).orElse {
        val token = SingleAccessToken.generateSecureToken()
        upsertToken(userId, token)
        logger.logInfo(s"Created a new login code for user $userId (expires at ${token.expires})")
        Some(token)
      }
    }

  def upsertToken(userId: String, token: SingleAccessToken): Int =
    statements.update(upsertTokenSql, userId, token.token, token.expires)

  /** The account of the current row; malformed settings fall back to the current defaults, an unreadable row is skipped. */
  private def readUser(row: ResultSet): Option[UserInDatabase] = readable("account") {
    val user = User(row.getString("name"), row.getString("id"), row.getString("mail"))
    val config = Try(read[UserConfig](row.getString("config"))).fold(error => {
      logger.logWarn(s"Discarded invalid UserConfig; using current defaults: ${ParsingContext.reason(error)}")
      UserConfig.defaults(isLocalAccount = false)
    }, identity)
    UserInDatabase(user, config)
  }

  /** The login code of the current row; a blank code or unsupported expiry is discarded so a new one can be requested. */
  private def readToken(row: ResultSet): Option[SingleAccessToken] = readable("login code") {
    val code = Option(row.getString("token")).filter(_.trim.nonEmpty).getOrElse(throw IllegalArgumentException("Empty login code"))
    SingleAccessToken(code, parseDatabaseTimestamp(row.getString("tokenExpires")))
  }

  private def readable[A](kind: String)(read: => A): Option[A] = Try(read).fold(error => {
    logger.logWarn(s"Discarded an unreadable stored $kind: ${ParsingContext.reason(error)}")
    None
  }, Some(_))
}

object AccountRows {
  def normalizedMail(mail: String): String = mail.trim.toLowerCase

  /** Reads a stored time such as `2026-10-13T01:40:00.123456789Z`; other forms are rejected. */
  private[server] def parseDatabaseTimestamp(databaseTimestampString: String): Instant =
    SqlStatements.instant(databaseTimestampString)

  private val selectUserById = "SELECT `id`, `name`, `mail`, `config` FROM `user` WHERE `id` = ?"
  private val selectUserByMail = "SELECT `id`, `name`, `mail`, `config` FROM `user` WHERE `mail` = ?"
  private val insertUserSql = "INSERT INTO `user` (`id`, `name`, `mail`, `config`) VALUES (?, ?, ?, ?)"
  private val updateUserSql = "UPDATE `user` SET `name` = ?, `mail` = ?, `config` = ? WHERE `id` = ?"
  private val selectToken = "SELECT `token`, `tokenExpires` FROM `loginTokens` WHERE `userId` = ?"
  private val upsertTokenSql =
    """INSERT INTO `loginTokens` (`userId`, `token`, `tokenExpires`)
      |VALUES (?, ?, ?)
      |ON DUPLICATE KEY UPDATE
      |  `token` = VALUES(`token`),
      |  `tokenExpires` = VALUES(`tokenExpires`)""".stripMargin
}
