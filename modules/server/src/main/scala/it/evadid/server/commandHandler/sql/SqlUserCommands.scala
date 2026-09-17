package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.user.User.{UserInDatabase, UserToken}
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.SendMailCommand.sendMail
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.SyncInformation.SyncSuccess

import java.security.SecureRandom
import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime
import java.util.HexFormat
import scala.concurrent.{ExecutionContext, Future}

object SqlUserCommands {

  given ExecutionContext = ExecutionContext.global

  def requestAuthMail(authMailRequest: AuthMailRequest, logger: Logger): Future[SendMailResponse] = Future {
    val connection = DatabaseConfig.readFromEnv().newConnection()
    val control = SqlUserCommands(connection, logger)
    val accounts = control.findUserWithMail(authMailRequest.userMail)
    if (accounts.isEmpty) {
      logger.logWarn(s"No Account for '${authMailRequest.userMail}' found in the database!")
      val infoMail = SendMailRequest(
        authMailRequest.userMail,
        "Versuchter Login für evadid.it",
        s"Guten Tag,\nJemand hat versucht, sich mit Ihrer Mailadresse bei evadid.it anzumelden. Dies hat nicht funktioniert, da Sie dort keinen Account besitzen. Die IP-Adresse des Versuches wurde auf unserem Server gespeichert. Weitere Schritte Ihrerseits sind nicht nötig!")
      sendMail(infoMail, logger, JvmUtils.env)
      SendMailResponse(false)
    } else if (accounts.size > 1) {
      val mailRequest = SendMailRequest(
        authMailRequest.userMail,
        "Login Code für evadid.it",
        s"Guten Tag,\nSie besitzen mehrere Accounts auf evadid.it. Die Login-Codes für ihre Accounts (einer pro Zeile) sind:\n\n${accounts.map(_.toCode).mkString("\n")}\n\nBitte geben Sie diese Codes nicht weiter!")
      sendMail(mailRequest, logger, JvmUtils.env)
    } else {
      val mailRequest = SendMailRequest(authMailRequest.userMail, "Login Code für evadid.it", s"Guten Tag,\nIhr Login Code ist:\n\n ${accounts.head.toCode}\n\nBitte geben Sie diesen Code nicht weiter!")
      sendMail(mailRequest, logger, JvmUtils.env)
    }
  }

  def handleLoginCommand(loginRequest: LoginRequest, logger: Logger): Future[LoginResponse] = Future {
    val connection = DatabaseConfig.readFromEnv().newConnection()
    val control = SqlUserCommands(connection, logger)
    control.requestLogin(loginRequest)
  }

  def handleUpsertAccountCommand(upsertAccountRequest: UpsertAccountRequest, logger: Logger): Future[UpsertAccountResponse] = Future {
    val connection = DatabaseConfig.readFromEnv().newConnection()
    val control = SqlUserCommands(connection, logger)

    val loginResponse = control.requestLogin(LoginRequest(upsertAccountRequest.user.id, upsertAccountRequest.userToken.token))
    if (loginResponse.loginSucceeded) {
      val changes = control.upsertUser(upsertAccountRequest.user, upsertAccountRequest.userConfigJson)
      UpsertAccountResponse(false, true, changes > 0)
    } else if (!loginResponse.isUserKnown) {
      val changes = control.upsertUser(upsertAccountRequest.user, upsertAccountRequest.userConfigJson)
      UpsertAccountResponse(true, true, false)
    } else {
      UpsertAccountResponse(false, false, false)
    }
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
      val token = UserToken(userColumns(3), generic.parseDatabaseTimestamp(userColumns(4)))
      val user = User(userColumns(1), userColumns(0), userColumns(2))
      val userConfigJson = userColumns(5)
      Some(UserInDatabase(user, token, userConfigJson))
    }
  }


  def requestLogin(loginRequest: LoginRequest): LoginResponse = {
    val dbInfo = readUserInfoFromDb(loginRequest.userId)
    if (dbInfo.isEmpty) {
      logger.logWarn(s"No access granted: user ${loginRequest.userId} does not exist in database!")
      LoginResponse(false, false, None, None, None)
    }
    else if (dbInfo.get.token.token != loginRequest.userToken) {
      logger.logWarn("No access granted: token provided is not equal to token required!")
      LoginResponse(true, false, None, None, None)
    }
    else if (dbInfo.get.token.expires.isBefore(LocalDateTime.now())) {
      logger.logWarn(s"No access granted: user token expired at ${dbInfo.get.token.expires}")
      ensureAndGetUserTokenFromDb(loginRequest.userId, logger)
      LoginResponse(true, false, None, None, None)
    }
    else {
      logger.logInfo("Login Suceeded!")
      LoginResponse(true, true, Option(dbInfo.get.user), Option(dbInfo.get.token), Option(dbInfo.get.configJson))
    }
  }

  def findUserWithMail(mail: String): List[UserInDatabase] = {
    val sql =
      s"""
         |SELECT `id`, `name`, `mail`, `token`, `tokenExpires`, `config`
         |FROM `user`
         |WHERE `mail` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, mail)
    val result: List[List[String]] = generic.executeQuery(stmt, List("id", "name", "mail", "token", "tokenExpires", "config"))
    result.flatMap(parseUser)
  }

  def readUserInfoFromDb(userId: String): Option[UserInDatabase] = {
    val sql =
      s"""
         |SELECT `id`, `name`, `mail`, `token`, `tokenExpires`, `config`
         |FROM `user`
         |WHERE `id` = ?
         |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setString(1, userId)
    val result: List[List[String]] = generic.executeQuery(stmt, List("id", "name", "mail", "token", "tokenExpires", "config"))
    result.flatMap(parseUser).headOption
  }

  def ensureAndGetUserTokenFromDb(userId: String, logger: Logger): Option[UserToken] = {
    val userInDb = readUserInfoFromDb(userId)
    if (userInDb.nonEmpty) {
      if (userInDb.get.token.expires.isAfter(LocalDateTime.now())) {
        logger.logInfo("Current Token is still valid, not changing!")
        Some(userInDb.get.token)
      }
      else {
        val token = UserToken.generateSecureToken()
        upsertToken(userInDb.get.user.id, token)
        logger.logInfo("Created new token for user " + userId + " (expires at " + token.expires + ")")
        Some(token)
      }
    } else {
      logger.logWarn("Cannot ensure token since user is unknown!")
      None
    }
  }



  def upsertToken(userId: String, token: UserToken): SyncSuccess = {
    val sql =
      s"""
         |INSERT INTO `user` (`id`, `token`, `tokenExpires`)
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


