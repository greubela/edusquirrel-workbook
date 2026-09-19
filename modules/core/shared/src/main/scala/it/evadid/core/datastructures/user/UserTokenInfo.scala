package it.evadid.core.datastructures.user

import it.evadid.core.util.io.serializer.DefaultSerializer

import java.net.InetAddress
import java.time.LocalDateTime

object UserTokenInfo {

  case class SignedToken(info: UserTokenInfo, signatureHex: String) {
    lazy val toJson: String = DefaultSerializer.serializerSignedUserTokenInfo.serialize(this)
  }

}

case class UserTokenInfo(user: User, createdAt: LocalDateTime, expiresAt: LocalDateTime, createdForAddress: InetAddress) {

  lazy val toJson: String = DefaultSerializer.serializerUserTokenInfo.serialize(this)

}
