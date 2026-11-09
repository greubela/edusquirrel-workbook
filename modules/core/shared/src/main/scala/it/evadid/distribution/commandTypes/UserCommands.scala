package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.distribution.commandTypes.MailCommands.SendMailResponse
import upickle.default.*

object UserCommands {

  private def identityForLog(infoField: Option[String])(payload: ujson.Value): ujson.Value = {
    val fields = payload.objOpt
    val user = infoField.flatMap(field => fields.flatMap(_.get(field)).flatMap(_.objOpt)
      .flatMap(_.get("user")).flatMap(_.objOpt))
    def text(field: String, userField: String): ujson.Value =
      fields.flatMap(_.get(field)).flatMap(_.strOpt)
        .orElse(user.flatMap(_.get(userField)).flatMap(_.strOpt)).map(ujson.Str(_)).getOrElse(ujson.Null)
    ujson.Obj("redacted" -> true, "userId" -> text("userId", "id"), "userMail" -> text("userMail", "mail"))
  }

  case class LoginRequest(userMail: String, accessToken: Either[SingleAccessToken, SignedToken]) derives ReadWriter

  case class LoginResponse(userKnown: Boolean, tokenValid: Boolean, allUserInfo: Option[AllUserInfo]) derives ReadWriter {
    def loginSucceeded: Boolean = userKnown && tokenValid && allUserInfo.nonEmpty
  }

  lazy val loginCommand: ExecutionCommandFactory[LoginRequest, LoginResponse] = ExecutionCommandFactory(
    "login-command", privacyFilter = Some(identityForLog(None))
  )

  case class UpdateAccountRequest(allUserInfo: AllUserInfo) derives ReadWriter

  case class UpdateAccountResponse(accountChanged: Boolean, token: SignedToken) derives ReadWriter

  lazy val updateAccountCommand: ExecutionCommandFactory[UpdateAccountRequest, UpdateAccountResponse] = ExecutionCommandFactory(
    "update-account", privacyFilter = Some(identityForLog(Some("allUserInfo")))
  )

  case class CreateAccountRequest(userInfo: AllUserInfo) derives ReadWriter

  case class CreateAccountResponse(accountCreated: Boolean, token: Option[SignedToken]) derives ReadWriter

  lazy val createAccountCommand: ExecutionCommandFactory[CreateAccountRequest, CreateAccountResponse] = ExecutionCommandFactory(
    "create-account", privacyFilter = Some(identityForLog(Some("userInfo")))
  )

  case class AuthMailRequest(
                              userMail: String
                            ) derives ReadWriter

  lazy val authMailCommand: ExecutionCommandFactory[AuthMailRequest, SendMailResponse] = ExecutionCommandFactory(
    "auth-mail-request"
  )


}
