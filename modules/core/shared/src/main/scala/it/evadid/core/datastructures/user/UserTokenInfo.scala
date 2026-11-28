package it.evadid.core.datastructures.user


import java.net.InetAddress
import java.time.Instant

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

case class UserTokenInfo(user: User, createdAt: Instant, expiresAt: Instant) derives ReadWriter {
  lazy val toJson: String = write(this)
}
