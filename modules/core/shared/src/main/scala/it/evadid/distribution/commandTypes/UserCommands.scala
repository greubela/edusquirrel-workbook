package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.distribution.commandTypes.MailCommands.SendMailResponse

import scala.util.Try

object UserCommands {

  case class LoginRequest(userMail: String, accessToken: Either[SingleAccessToken, SignedToken])

  case class LoginResponse(userKnown: Boolean, tokenValid: Boolean, user: Option[User], signedToken: Option[SignedToken], userConfigJson: Option[String]) {
    def loginSucceeded: Boolean = userKnown && tokenValid && user.nonEmpty && signedToken.nonEmpty && userConfigJson.nonEmpty

    def toInfo(userConfigSerializer: Serializer[UserConfig]): Option[AllUserInfo] = if (!loginSucceeded) None else Try {
      AllUserInfo(signedToken.get.info.user, signedToken, userConfigSerializer.deserialize(userConfigJson.get))
    }.toOption
  }

  val loginCommand: ExecutionCommandFactory[LoginRequest, LoginResponse] = ExecutionCommandFactory(
    "login-command", DefaultSerializer.serializerVerifyAuthenticationRequest, DefaultSerializer.serializerVerifyAuthenticationResponse
  )

  case class UpdateAccountRequest(user: User, userConfigJson: String, token: SignedToken)

  case class UpdateAccountResponse(accountChanged: Boolean, token: SignedToken)

  val updateAccountCommand: ExecutionCommandFactory[UpdateAccountRequest, UpdateAccountResponse] = ExecutionCommandFactory(
    "create-account", DefaultSerializer.serializerUpdateAccountRequest, DefaultSerializer.serializerUpdateAccountResponse
  )

  case class CreateAccountRequest(user: User, userConfigJson: String)

  case class CreateAccountResponse(accountCreated: Boolean, token: Option[SignedToken])

  val createAccountCommand: ExecutionCommandFactory[CreateAccountRequest, CreateAccountResponse] = ExecutionCommandFactory(
    "create-account", DefaultSerializer.serializerCreateAccountRequest, DefaultSerializer.serializerCreateAccountResponse
  )

  case class AuthMailRequest(
                              userMail: String
                            )

  val authMailCommand: ExecutionCommandFactory[AuthMailRequest, SendMailResponse] = ExecutionCommandFactory(
    "auth-mail-request",
    DefaultSerializer.serializerAuthMailRequestJson,
    DefaultSerializer.serializerSendMailResponseJson
  )


}