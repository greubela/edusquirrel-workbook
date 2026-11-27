package it.evadid.server.commandHandler.sql

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig, UserCredentials}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.ParsingContext
import it.evadid.distribution.commandTypes.MailCommands.{SendMailRequest, SendMailResponse}
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.AuthHandling
import it.evadid.server.database.{UserAccountDatabase, UserAccountRows, newConnection}
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
  private def acceptsSingleUseToken(ctrl: UserAccountRows, user: User, submitted: SingleAccessToken, logger: Logger): Boolean =
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
