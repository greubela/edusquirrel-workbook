package it.evadid.core.datastructures.user

import it.evadid.core.util.io.serializer.DefaultSerializer

import java.net.InetAddress
import java.time.LocalDateTime

object UserTokenInfo {

  case class SignedToken(info: UserTokenInfo, tokenStringWithSignature: String) {
    lazy val toJson: String = DefaultSerializer.serializerSignedUserTokenInfo.serialize(this)
  }

  object SignedToken {
    val cookieKey: String = "Authorization"
  }

}

case class UserTokenInfo(user: User, createdAt: LocalDateTime, expiresAt: LocalDateTime) {
  lazy val toJson: String = DefaultSerializer.serializerUserTokenInfo.serialize(this)
}
