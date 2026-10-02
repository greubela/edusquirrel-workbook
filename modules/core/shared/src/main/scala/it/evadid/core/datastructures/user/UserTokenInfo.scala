package it.evadid.core.datastructures.user


import java.net.InetAddress
import java.time.LocalDateTime

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given


object UserTokenInfo {

  case class SignedToken(info: UserTokenInfo, tokenStringWithSignature: String) derives ReadWriter {
    lazy val toJson: String = write(this)
  }

  object SignedToken {
    val cookieKey: String = "Authorization"
  }

}

case class UserTokenInfo(user: User, createdAt: LocalDateTime, expiresAt: LocalDateTime) derives ReadWriter {
  lazy val toJson: String = write(this)
}
