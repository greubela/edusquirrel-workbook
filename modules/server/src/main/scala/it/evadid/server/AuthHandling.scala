package it.evadid.server


import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{User, UserTokenInfo}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import pdi.jwt.*
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Cookies, Headers}

import java.net.InetAddress
import java.time.{Clock, Instant}
import scala.util.{Failure, Success}

object AuthHandling {

  val serverSecret: String = JvmUtils.env("SERVER_SECRET").getOrElse("")
  implicit val clock: Clock = Clock.systemUTC
  val algo = JwtAlgorithm.HS256


  def mayAccessIdBased(userIdRequester: String, userIdOwner: String): Boolean = {
    userIdRequester == userIdOwner
  }

  def mayAccessMailBased(mailRequester: String, mailOwner: String): Boolean = {
    mailRequester == mailOwner
  }


  def findAuthCookies(cookies: Cookies, headers: Headers): Seq[SignedToken] = {
    val claimedAuth: Seq[String] = cookies.filter(_.name == SignedToken.cookieKey).map(_.value).toList ++ headers.toSimpleMap.get(SignedToken.cookieKey).toList
    val deserialized = it.evadid.core.util.io.Serializer.fromUpickleJson[SignedToken].tryDeserializeAll(claimedAuth)
    deserialized.inputAfterOperation.toList
  }

  def isTokenValid(logger: Logger, givenToken: SignedToken): Boolean = {
    JwtJson.decodeJson(givenToken.tokenStringWithSignature, serverSecret, Seq(algo)) match {
      case Success(jsonClaims) => try {
        val parsedUserInfo = upickle.default.read[UserTokenInfo](jsonClaims.toString)

        if (parsedUserInfo.expiresAt.isBefore(Instant.now())) {
          logger.logWarn(s"Dismissed token because it expired at ${parsedUserInfo.expiresAt}!")
          false
        }
        else if (parsedUserInfo != givenToken.info) {
          logger.logWarn("Dismissed token because claimed info does not match actual info")
          false
        }
        else {
          /*if (givenToken.info.createdForAddress != connectionRequestedFrom) {
            logger.logInfo(s"Token was created at [${givenToken.info.createdForAddress}] but now used with [$connectionRequestedFrom] (this is acceptable)")
          }*/
          true
        }
      } catch {
        case e: Exception =>
          logger.logExceptionWarn("Dismissed token because of deserialization or logic error", e)
          false
      }
      case Failure(exception) =>
        logger.logExceptionWarn("Dismissed token because cryptographic signature verification failed", exception)
        false
    }
  }


  def createToken(user: User): SignedToken = {
    val now = Instant.now()
    val tokenInfo = UserTokenInfo(user, now, now.atOffset(java.time.ZoneOffset.UTC).plusYears(1).toInstant)
    val claim = tokenInfo.toJson
    val token = JwtJson.encode(claim, serverSecret, algo)
    SignedToken(tokenInfo, token)
  }


}
