package it.evadid.server

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{User, UserTokenInfo}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.util.JvmUtils
import play.api.mvc.Cookies

import pdi.jwt.JwtJson._

import pdi.jwt._
import play.api.libs.json.Json

import java.net.InetAddress
import java.time.{Clock, LocalDateTime}

object AuthHandling {

  val serverSecret: String = JvmUtils.env("SERVER_SECRET").get
  implicit val clock: Clock = Clock.systemUTC
  val algo = JwtAlgorithm.HS256

  def findAuthCookie(cookies: Cookies): Option[SignedToken] = {
    val claimedAuth = cookies.filter(_.name == "auth_token").map(_.value)
    val deserialized = DefaultSerializer.serializerSignedUserTokenInfo.tryDeserializeAll(claimedAuth)
    deserialized.inputAfterOperation.headOption
  }

  def isTokenValid(givenToken: SignedToken): Boolean = try {
    val claim = givenToken.info.toJson
    val res = JwtJson.decodeJson(givenToken.signatureHex, serverSecret, Seq(JwtAlgorithm.HS256))

    false
  } catch case e: Exception => false


  def createToken(user: User, requestAddr: InetAddress): SignedToken = {
    val now = LocalDateTime.now()
    val tokenInfo = UserTokenInfo(user, now, now.plusYears(1), requestAddr)
    val claim = tokenInfo.toJson
    val token = JwtJson.encode(claim, serverSecret, algo)
    SignedToken(tokenInfo, token)
  }


}
