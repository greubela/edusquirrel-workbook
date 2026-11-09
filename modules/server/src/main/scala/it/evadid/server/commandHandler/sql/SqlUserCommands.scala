package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.UserCredentials

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.AuthHandling
import it.evadid.server.database.UserAccountDatabase
import it.evadid.server.SendMailCommand.sendMail
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncInformation.*
import it.evadid.workbook.interaction.sync.SyncSuccess
import play.api.libs.json.JsPath.write

import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
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
      val mailRequest = SendMailRequest(authMailRequest.userMail, s"Code '${token.token} for Login to EvaDid.it", s"Guten Tag,\nIhr Login Code ist:\n\n${token.token}\n\nDer Code ist gültig bis ${token.expires}. Bitte geben Sie diesen Code nicht weiter!")
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
    val user = dbUser.map(_.user)

    loginRequest.accessToken.match {
      case Left(singleUseToken) => {
        if (user.isEmpty) {
          LoginResponse(dbUser.isDefined, false, None)
        } else {
          val dbToken = ctrl.ensureAndGetUserTokenFromDb(user.head.id, logger)
          if (dbToken.isEmpty) {
            logger.logWarn(s"Dismissing Token: Not Existing in Database!")
            LoginResponse(dbUser.isDefined, false, None)
          } else if (singleUseToken.token != dbToken.get.token) {
            logger.logWarn("Dismissing Token: Token Invalid!")
            LoginResponse(dbUser.isDefined, false, None)
          } else if (dbToken.get.expires.isBefore(LocalDateTime.now())) {
            logger.logWarn(s"Dismissing Token: Token was expired (expired at ${dbToken.get.expires})!")
            LoginResponse(dbUser.isDefined, false, None)
          } else {
            val token = AuthHandling.createToken(user.get)
            val userInfo = AllUserInfo(user.get, UserCredentials(signedBackendToken = Some(token)), dbUser.get.config)
            LoginResponse(dbUser.isDefined, true, Some(userInfo))
          }
        }
      }
      case Right(signedToken) => {
        if (!dbUser.exists(_.user.id == signedToken.info.user.id) || !AuthHandling.isTokenValid(logger, signedToken)) {
          LoginResponse(dbUser.isDefined, false, None)
        } else {
          val userInfo = AllUserInfo(user.get, UserCredentials(signedBackendToken = Some(signedToken)), dbUser.get.config)
          LoginResponse(dbUser.isDefined, true, Some(userInfo))
        }
      }
    }
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

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, logger)


  private def parseUser(userColumns: List[String]): Option[UserInDatabase] = {
    if (userColumns.isEmpty || userColumns.size < 4) None
    else {
      val user = User(userColumns(1), userColumns(0), userColumns(2))
      val userConfig = scala.util.Try(read[UserConfig](userColumns(3))).recover {
        case scala.util.control.NonFatal(_) =>
          logger.logWarn("Discarded invalid account settings; using current defaults.")
          UserConfig.onlineDefaults
      }.get
      Some(UserInDatabase(user, userConfig))
    }
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

    val result: List[List[String]] = generic.executeQuery(stmt, List("userId", "token", "tokenExpires"))
    result.headOption.flatMap { columns =>
      scala.util.Try {
        require(Option(columns(1)).exists(_.trim.nonEmpty), "Empty login code")
        SingleAccessToken(columns(1), generic.parseDatabaseTimestamp(columns(2)))
      }.fold(_ => {
        logger.logWarn("Discarded an invalid stored login code; a new code can be requested.")
        None
      }, Some(_))
    }
  }


  def findUserWithMail(mail: String): List[UserInDatabase] = {
    val sql =
      s"""
         |SELECT `id`, `name`, `mail`, `config`
         |FROM `user`
         |WHERE `mail` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, mail.toLowerCase)
    val result: List[List[String]] = generic.executeQuery(stmt, List("id", "name", "mail", "config"))
    result.flatMap(parseUser)
  }

  def readUserInfoFromDb(userId: String): Option[UserInDatabase] = {
    val sql =
      s"""
         |SELECT `id`, `name`, `mail`, `config`
         |FROM `user`
         |WHERE `id` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, userId)
    val result: List[List[String]] = generic.executeQuery(stmt, List("id", "name", "mail", "config"))
    result.flatMap(parseUser).headOption
  }

  def ensureAndGetUserTokenFromDb(userId: String, logger: Logger): Option[SingleAccessToken] = {

    def createNewToken(reason: String): Option[SingleAccessToken] = {
      val token = SingleAccessToken.generateSecureToken()
      upsertToken(userId, token)
      logger.logInfo(s"Created new token (${reason}) for user " + userId + " (expires at " + token.expires + ")")
      Some(token)
    }

    val userInDb = readUserInfoFromDb(userId)
    val token = findAccessTokens(userId)
    if (userInDb.isEmpty) {
      logger.logWarn("Cannot ensure token since user is unknown!")
      None
    } else if (token.isEmpty) {
      createNewToken("missing or invalid")
    }
    else if (token.get.expires.isBefore(LocalDateTime.now())) {
      createNewToken(s"old expired at ${token.get.expires}")
    }
    else {
      token
    }
  }


  def upsertToken(userId: String, token: SingleAccessToken): SyncSuccess = {
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

    val res = generic.executeUpdate(stmt)
    SyncSuccess(0, res, 0, LocalDateTime.now())
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


