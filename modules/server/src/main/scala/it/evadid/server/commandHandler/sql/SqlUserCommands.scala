package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.AuthHandling
import it.evadid.server.SendMailCommand.sendMail
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

object SqlUserCommands {

  given ExecutionContext = ExecutionContext.global

  def requestAuthMail(authMailRequest: AuthMailRequest, logger: Logger): Future[SendMailResponse] = Future {

    def accountNotFound(): SendMailResponse = {
      logger.logWarn(s"No Account for '${authMailRequest.userMail}' found in the database!")
      val infoMail = SendMailRequest(
        authMailRequest.userMail,
        "Versuchter Login für evadid.it",
        s"Guten Tag,\nJemand hat versucht, sich mit Ihrer Mailadresse bei evadid.it anzumelden. Dies hat nicht funktioniert, da Sie dort keinen Account besitzen. Die IP-Adresse des Versuches wurde auf unserem Server gespeichert. Weitere Schritte Ihrerseits sind nicht nötig!")
      sendMail(infoMail, logger, JvmUtils.env)
      SendMailResponse(None, false)
    }

    def informationFound(account: User, token: SingleAccessToken): SendMailResponse = {
      val mailRequest = SendMailRequest(authMailRequest.userMail, s"Code '${token.token} for Login to EvaDid.it", s"Guten Tag,\nIhr Login Code ist:\n\n${token.token}\n\n. Der Code ist gültig bis ${token.expires}. Bitte geben Sie diesen Code nicht weiter!")
      val mailRes = sendMail(mailRequest, logger, JvmUtils.env)
      SendMailResponse(Some(account), mailRes.sent)
    }

    val control = instance(logger)
    val accounts = control.findUserWithMail(authMailRequest.userMail)
    if (accounts.isEmpty) {
      accountNotFound()
    } else {
      val account = accounts.head
      val token = control.ensureAndGetUserTokenFromDb(account.user.id, logger)
      if (token.isEmpty) accountNotFound()
      else informationFound(account.user, token.get)
    }
  }

  def handleLoginCommand(loginRequest: LoginRequest, logger: Logger): Future[LoginResponse] = Future {
    val ctrl = instance(logger)
    val dbUser = ctrl.findUserWithMail(loginRequest.userMail).headOption
    val user = dbUser.map(_.user)

    loginRequest.accessToken.match{
      case Left(singleUseToken) => {
        if (user.isEmpty) {
          LoginResponse(dbUser.isDefined, false, None, None, None)
        } else {
          val dbToken = ctrl.ensureAndGetUserTokenFromDb(user.head.id, logger)
          if (dbToken.isEmpty || singleUseToken.token != dbToken.get.token || dbToken.get.expires.isBefore(LocalDateTime.now())) {
            LoginResponse(dbUser.isDefined, false, user, None, None)
          } else {
            val token = AuthHandling.createToken(user.get)
            LoginResponse(dbUser.isDefined, true, user, Some(token), dbUser.map(_.configJson))
          }
        }
      }
      case Right(signedToken) => {
        if(!AuthHandling.isTokenValid(logger, signedToken)){
          LoginResponse(dbUser.isDefined, false, user, None, None)
        }else{
          LoginResponse(dbUser.isDefined, true, user, Some(signedToken), dbUser.map(_.configJson))
        }
      }
    }

  }

  def handleCreateAccountCommand(request: CreateAccountRequest, logger: Logger): Future[CreateAccountResponse] = Future {
    if (AuthHandling.mayCreateAccount(request.user, logger)) {
      instance(logger).upsertUser(request.user, request.userConfigJson)
      val token = AuthHandling.createToken(request.user)
      CreateAccountResponse(true, Some(token))
    } else {
      CreateAccountResponse(false, None)
    }
  }

  def instance(logger: Logger): SqlUserCommands = {
    val connection = DatabaseConfig.readFromEnv().newConnection()
    val control = SqlUserCommands(connection, logger)
    control
  }

  def handleUpdateAccountCommand(request: UpdateAccountRequest, logger: Logger): Future[UpdateAccountResponse] = Future {
    val connection = DatabaseConfig.readFromEnv().newConnection()
    val control = SqlUserCommands(connection, logger)
    val changes = control.upsertUser(request.user, request.userConfigJson)
    val token = AuthHandling.createToken(request.user)
    UpdateAccountResponse(changes > 0, token)
  }

}


class SqlUserCommands(
                       connection: Connection,
                       logger: Logger,
                     ) {

  private lazy val generic: GenericSqlFunctionality = new GenericSqlFunctionality(connection, logger)


  private def parseUser(userColumns: List[String]): Option[UserInDatabase] = {
    if (userColumns.isEmpty || userColumns.size < 6) None
    else {
      val user = User(userColumns(1), userColumns(0), userColumns(2))
      val userConfigJson = userColumns(3)
      Some(UserInDatabase(user, userConfigJson))
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

    val result: List[List[String]] = generic.executeQuery(stmt, List("id", "name", "mail", "config"))
    result.map(userColumns => SingleAccessToken(userColumns(1), generic.parseDatabaseTimestamp(userColumns(2)))).headOption
  }


  def findUserWithMail(mail: String): List[UserInDatabase] = {
    val sql =
      s"""
         |SELECT `id`, `name`, `mail`, `config`
         |FROM `user`
         |WHERE `mail` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, mail)
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
      createNewToken("none existed")
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

  def upsertUser(user: User, userConfigJson: String): Int = {
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
    stmt.setString(3, user.mail)
    stmt.setString(4, userConfigJson)

    generic.executeUpdate(stmt)
  }


}


