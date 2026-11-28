package it.evadid.server.account

import it.evadid.core.datastructures.user.{AllUserInfo, User, UserCredentials}
import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.commandTypes.MailCommands.SendMailResponse
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.server.BlockingExecution
import it.evadid.server.database.Database
import it.evadid.server.events.mail.MailSender
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.logging.Logger

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.LocalDateTime
import scala.concurrent.Future

/** Registration, login and account updates on the backend database. */
final class AccountCommands(database: => DatabaseConfigWithCredentials, tokens: SignedTokens, mail: MailSender) {
  import AccountCommands.*

  private def withAccounts[A](logger: Logger)(operation: AccountRows => A): Future[A] = BlockingExecution {
    Database.withConnection(database, "executing user account request", logger)(connection => operation(AccountRows(connection, logger)))
  }

  def requestAuthMail(request: AuthMailRequest, logger: Logger): Future[SendMailResponse] = withAccounts(logger) { rows =>
    rows.findUserWithMail(request.userMail).flatMap(account => rows.ensureTokenInDb(account.user.id).map(account.user -> _))
  }.map {
    case None =>
      logger.logWarn(s"No account for '${request.userMail}' found in the database!")
      SendMailResponse(None, false)
    case Some((account, token)) =>
      SendMailResponse(Some(account), mail.send(LoginMail(request.userMail, token), logger).sent)
  }(using BlockingExecution.context)

  def handleLoginCommand(request: LoginRequest, logger: Logger): Future[LoginResponse] = withAccounts(logger) { rows =>
    val account = rows.findUserWithMail(request.userMail)
    def accepted(found: UserInDatabase, token: SignedToken): LoginResponse =
      LoginResponse(userKnown = true, tokenValid = true,
        Some(AllUserInfo(found.user, UserCredentials(signedBackendToken = Some(token)), found.config)))
    (request.accessToken, account) match {
      case (Left(code), Some(found)) if acceptsLoginCode(rows.getTokenFromDb(found.user.id), code, logger) =>
        accepted(found, tokens.createToken(found.user))
      case (Right(signed), Some(found)) if found.user.id == signed.info.user.id && tokens.isTokenValid(logger, signed) =>
        accepted(found, signed)
      case _ => LoginResponse(account.isDefined, tokenValid = false, None)
    }
  }

  /** Inserts the account in one transaction; an ID or mail address already in use rejects it without changes. */
  def handleCreateAccountCommand(request: CreateAccountRequest, logger: Logger): Future[CreateAccountResponse] = withAccounts(logger) { rows =>
    val user = request.userInfo.user
    val created = rows.transaction {
      rejection(rows, user) match {
        case Some(reason) =>
          logger.logWarn(reason)
          false
        case None => rows.insertUser(user, request.userInfo.config)
      }
    }
    CreateAccountResponse(created, Option.when(created)(tokens.createToken(user)))
  }

  def handleUpdateAccountCommand(request: UpdateAccountRequest, logger: Logger): Future[UpdateAccountResponse] = withAccounts(logger) { rows =>
    val changed = rows.updateUser(request.allUserInfo.user, request.allUserInfo.config)
    UpdateAccountResponse(changed, tokens.createToken(request.allUserInfo.user))
  }
}

object AccountCommands {
  /** Why `user` cannot be registered, for the log; the unique keys decide concurrent registrations. */
  private def rejection(rows: AccountRows, user: User): Option[String] =
    rows.findUserWithMail(user.mail).map(existing => s"Mail ${user.mail} is already associated with user ${existing.user.id}!")
      .orElse(rows.readUserInfoFromDb(user.id).map(existing => s"Id ${user.id} is already in use (associated with mail ${existing.user.mail})!"))

  /** The submitted code must equal the stored, unexpired code; the submitted expiry is ignored. */
  private[account] def acceptsLoginCode(stored: Option[SingleAccessToken], submitted: SingleAccessToken, logger: Logger): Boolean =
    stored match {
      case None =>
        logger.logWarn("Dismissing login code: none is stored!")
        false
      case Some(code) if !MessageDigest.isEqual(code.token.getBytes(UTF_8), submitted.token.getBytes(UTF_8)) =>
        logger.logWarn("Dismissing login code: code invalid!")
        false
      case Some(code) if code.expires.isBefore(LocalDateTime.now()) =>
        logger.logWarn(s"Dismissing login code: it expired at ${code.expires}!")
        false
      case Some(_) => true
    }
}
