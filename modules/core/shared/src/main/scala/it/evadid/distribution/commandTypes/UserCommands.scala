package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.distribution.commandTypes.MailCommands.SendMailResponse

import scala.util.Try

import upickle.default.*
object UserCommands {

  case class LoginRequest(userMail: String, accessToken: Either[SingleAccessToken, SignedToken]) derives ReadWriter

  case class LoginResponse(userKnown: Boolean, tokenValid: Boolean, user: Option[User], signedToken: Option[SignedToken], userConfigJson: Option[String]) derives ReadWriter {
    def loginSucceeded: Boolean = userKnown && tokenValid && user.nonEmpty && signedToken.nonEmpty && userConfigJson.nonEmpty

    def toInfo(userConfigSerializer: Serializer[UserConfig]): Option[AllUserInfo] = if (!loginSucceeded) None else Try {
      AllUserInfo(signedToken.get.info.user, signedToken, userConfigSerializer.deserialize(userConfigJson.get))
    }.toOption
  }

  lazy val loginCommand: ExecutionCommandFactory[LoginRequest, LoginResponse] = ExecutionCommandFactory(
    "login-command"
  )

  case class UpdateAccountRequest(user: User, userConfigJson: String, token: SignedToken) derives ReadWriter

  case class UpdateAccountResponse(accountChanged: Boolean, token: SignedToken) derives ReadWriter

  lazy val updateAccountCommand: ExecutionCommandFactory[UpdateAccountRequest, UpdateAccountResponse] = ExecutionCommandFactory(
    "create-account"
  )

  case class CreateAccountRequest(user: User, userConfigJson: String) derives ReadWriter

  case class CreateAccountResponse(accountCreated: Boolean, token: Option[SignedToken]) derives ReadWriter

  lazy val createAccountCommand: ExecutionCommandFactory[CreateAccountRequest, CreateAccountResponse] = ExecutionCommandFactory(
    "create-account"
  )

  case class AuthMailRequest(
                              userMail: String
                            ) derives ReadWriter

  lazy val authMailCommand: ExecutionCommandFactory[AuthMailRequest, SendMailResponse] = ExecutionCommandFactory(
    "auth-mail-request"
  )


}