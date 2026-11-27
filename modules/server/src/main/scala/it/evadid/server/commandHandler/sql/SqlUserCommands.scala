package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserCredentials}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.ParsingContext
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.AuthHandling
import it.evadid.server.database.{SqlStatements, UserAccountDatabase}
import it.evadid.server.SendMailCommand.sendMail
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger

import java.sql.{Connection, ResultSet, Timestamp}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal
import upickle.default.*

object SqlUserCommands {

  given ExecutionContext = ExecutionContext.global

  private val database = new UserAccountDatabase(() => DatabaseConfigWithCredentials.readFromEnv().newConnection())

  def requestAuthMail(authMailRequest: AuthMailRequest, logger: Logger): Future[SendMailResponse] = {

    def accountNotFound(): SendMailResponse = {
      logger.logWarn(s"No Account for '${authMailRequest.userMail}' found in the database!")
      SendMailResponse(None, false)
    }

    def informationFound(account: User, token: SingleAccessToken): SendMailResponse = {
      val mailRequest = SendMailRequest(authMailRequest.userMail, s"Code '${token.token} for Login to EvaDid.it", s"Guten Tag,\nIhr Login Code ist:\n\n${token.token}\n\nDer Code ist gültig bis ${token.expires}. Bitte geben Sie diesen Code nicht weiter!", Nil)
      val mailRes = sendMail(mailRequest, logger, JvmUtils.env)
      SendMailResponse(Some(account), mailRes.sent)
    }

    database.execute(logger) { control =>
      val accounts = control.findUserWithMail(authMailRequest.userMail)
      logger.logInfo(s"Accounts found for mail ${authMailRequest.userMail}: " + accounts)
      accounts.headOption.flatMap { account =>
        control.ensureAndGetUserTokenFromDb(account.user.id, logger).map(token => account.user -> token)
      }
    }.map {
      case None => accountNotFound()
      case Some((account, token)) => informationFound(account, token)
    }
  }

  def handleLoginCommand(loginRequest: LoginRequest, logger: Logger): Future[LoginResponse] = database.execute(logger) { ctrl =>
    val dbUser = ctrl.findUserWithMail(loginRequest.userMail).headOption
    val rejected = LoginResponse(dbUser.isDefined, false, None)
    def accepted(account: UserInDatabase, token: SignedToken): LoginResponse =
      LoginResponse(dbUser.isDefined, true, Some(AllUserInfo(account.user, UserCredentials(signedBackendToken = Some(token)), account.config)))

    (loginRequest.accessToken, dbUser) match {
      case (Left(singleUseToken), Some(account)) =>
        if acceptsSingleUseToken(ctrl, account.user, singleUseToken, logger) then accepted(account, AuthHandling.createToken(account.user))
        else rejected
      case (Right(signedToken), Some(account))
        if account.user.id == signedToken.info.user.id && AuthHandling.isTokenValid(logger, signedToken) =>
        accepted(account, signedToken)
      case _ => rejected
    }
  }

  /** The submitted code must equal the stored, unexpired code; the submitted expiry is ignored. */
  private def acceptsSingleUseToken(ctrl: SqlUserCommands, user: User, submitted: SingleAccessToken, logger: Logger): Boolean =
    ctrl.ensureAndGetUserTokenFromDb(user.id, logger) match {
      case None =>
        logger.logWarn(s"Dismissing Token: Not Existing in Database!")
        false
      case Some(stored) if submitted.token != stored.token =>
        logger.logWarn("Dismissing Token: Token Invalid!")
        false
      case Some(stored) if stored.expires.isBefore(LocalDateTime.now()) =>
        logger.logWarn(s"Dismissing Token: Token was expired (expired at ${stored.expires})!")
        false
      case Some(_) => true
    }

  def handleCreateAccountCommand(request: CreateAccountRequest, logger: Logger): Future[CreateAccountResponse] = database.execute(logger) { control =>
    if (control.mayCreateAccount(request.userInfo.user)) {
      control.upsertUser(request.userInfo.user, request.userInfo.config)
      val token = AuthHandling.createToken(request.userInfo.user)
      CreateAccountResponse(true, Some(token))
    } else {
      CreateAccountResponse(false, None)
    }
  }

  def handleUpdateAccountCommand(request: UpdateAccountRequest, logger: Logger): Future[UpdateAccountResponse] = database.execute(logger) { control =>
    val changes = control.upsertUser(request.allUserInfo.user, request.allUserInfo.config)
    val token = AuthHandling.createToken(request.allUserInfo.user)
    UpdateAccountResponse(changes > 0, token)
  }


  /** Reads a JDBC timestamp text such as `2026-10-13 01:40:00.123456`; other formats are rejected. */
  private[sql] def parseDatabaseTimestamp(databaseTimestampString: String): LocalDateTime =
    Timestamp.valueOf(databaseTimestampString).toLocalDateTime
}

class SqlUserCommands(connection: Connection, logger: Logger) {

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
        SingleAccessToken(columns(1), SqlUserCommands.parseDatabaseTimestamp(columns(2)))
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
      case (_, Some(stored)) if stored.expires.isBefore(LocalDateTime.now()) => createNewToken(s"old expired at ${stored.expires}")
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
