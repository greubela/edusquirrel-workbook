package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserCredentials}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.ParsingContext
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.AuthHandling
import it.evadid.server.database.UserAccountDatabase
import it.evadid.server.SendMailCommand.sendMail
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger

import java.sql.{Connection, Timestamp}
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

}


class SqlUserCommands(
                       connection: Connection,
                       logger: Logger,
                     ) {

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

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(logger)

  private val userColumns = List("id", "name", "mail", "config")

  /** Columns in [[userColumns]] order; malformed settings fall back to the current defaults. */
  private def parseUser(columns: Seq[String]): Option[UserInDatabase] =
    Option.when(columns.size >= 4) {
      val user = User(columns(1), columns(0), columns(2))
      val userConfig = Try(read[UserConfig](columns(3))).recover {
        case NonFatal(error) =>
          logger.logWarn(s"Discarded invalid UserConfig; using current defaults: ${ParsingContext.reason(error)}")
          UserConfig.defaults(isLocalAccount = false)
      }.get
      UserInDatabase(user, userConfig)
    }

  /** Users whose fixed key column equals `value`, bound as a statement parameter. */
  private def selectUsers(keyColumn: "id" | "mail", value: String): Seq[UserInDatabase] = {
    val sql =
      s"""
         |SELECT `id`, `name`, `mail`, `config`
         |FROM `user`
         |WHERE `$keyColumn` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, value)
    generic.executeQuery(stmt, userColumns).flatMap(parseUser)
  }

  def findAccessTokens(userId: String): Option[SingleAccessToken] = {
    val sql =
      s"""
         |SELECT `userId`, `token`, `tokenExpires`
         |FROM `loginTokens`
         |WHERE `userId` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, userId)

    val result: Seq[Seq[String]] = generic.executeQuery(stmt, List("userId", "token", "tokenExpires"))
    result.headOption.flatMap { columns =>
      Try {
        require(Option(columns(1)).exists(_.trim.nonEmpty), "Empty login code")
        SingleAccessToken(columns(1), generic.parseDatabaseTimestamp(columns(2)))
      }.fold(_ => {
        logger.logWarn("Discarded an invalid stored login code; a new code can be requested.")
        None
      }, Some(_))
    }
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


  def upsertToken(userId: String, token: SingleAccessToken): Int = {
    val sql =
      s"""
         |INSERT INTO `loginTokens` (`userId`, `token`, `tokenExpires`)
         |VALUES (?, ?, ?)
         |ON DUPLICATE KEY UPDATE
         |  `token` = VALUES(`token`),
         |  `tokenExpires` = VALUES(`tokenExpires`)
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, userId)
    stmt.setString(2, token.token)
    stmt.setTimestamp(3, Timestamp.valueOf(token.expires))

    generic.executeUpdate(stmt)
  }

  def upsertUser(user: User, userConfig: UserConfig): Int = {
    val sql =
      s"""
         |INSERT INTO `user` (`id`, `name`, `mail`, `config`)
         |VALUES (?, ?, ?, ?)
         |ON DUPLICATE KEY UPDATE
         |  `name` = VALUES(`name`),
         |  `mail` = VALUES(`mail`),
         |  `config` = VALUES(`config`)
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, user.id)
    stmt.setString(2, user.name)
    stmt.setString(3, user.mail.toLowerCase)
    stmt.setString(4, userConfig.withoutCredentials.toJson)

    generic.executeUpdate(stmt)
  }

}
