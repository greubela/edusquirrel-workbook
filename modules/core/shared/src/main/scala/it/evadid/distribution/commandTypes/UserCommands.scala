package it.evadid.distribution.commandTypes

import it.evadid.core.datastructures.user.User.UserToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.distribution.commandTypes.MailCommands.SendMailResponse

import scala.util.Try

object UserCommands {

  object LoginRequest {
    def apply(loginCode: String): LoginRequest = if (loginCode.length > 52 && loginCode.charAt(50) == '#') {
      LoginRequest(loginCode.substring(0, 50), loginCode.substring(51, loginCode.length))
    } else LoginRequest("", "")
  }

  case class LoginRequest(userId: String, userToken: String) {
    val asCode: String = userToken + "#" + userId
  }

  case class LoginResponse(isUserKnown: Boolean, isTokenValid: Boolean, user: Option[User], serverToken: Option[UserToken], userConfigJson: Option[String]) {
    def loginSucceeded: Boolean = isUserKnown && isTokenValid

    def toInfo(userConfigSerializer: Serializer[UserConfig]): Option[AllUserInfo] = if(!loginSucceeded) None else Try {
      AllUserInfo(user.get, serverToken.get, userConfigSerializer.deserialize(userConfigJson.get))
    }.toOption
  }

  val loginCommand: ExecutionCommandFactory[LoginRequest, LoginResponse] = ExecutionCommandFactory(
    "login-command", DefaultSerializer.serializerVerifyAuthenticationRequest, DefaultSerializer.serializerVerifyAuthenticationResponse
  )

  case class UpsertAccountRequest(user: User, userToken: UserToken, userConfigJson: String)

  case class UpsertAccountResponse(accountCreated: Boolean, providedTokenValid: Boolean, userInfoUpdated: Boolean)

  val upsertAccountCommand: ExecutionCommandFactory[UpsertAccountRequest, UpsertAccountResponse] = ExecutionCommandFactory(
    "upsert-account", DefaultSerializer.serializerUpsertAccountRequest, DefaultSerializer.serializerUpsertAccountResponse
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