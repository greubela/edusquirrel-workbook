package it.evadid.server.account

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{User, UserTokenInfo}
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.Logger
import pdi.jwt.*
import play.api.mvc.{Cookies, Headers}

import java.time.{Clock, Instant, ZoneOffset}
import scala.util.{Failure, Success}

/** Issues and verifies the HS256-signed login tokens; `secret` comes from the validated [[it.evadid.server.ServerConfig]]. */
final class SignedTokens(secret: String) {
  require(secret.trim.nonEmpty, "Login tokens require a non-blank signing secret")
  private given Clock = Clock.systemUTC
  private val algorithm = JwtAlgorithm.HS256

  def isTokenValid(logger: Logger, givenToken: SignedToken): Boolean =
    JwtJson.decodeJson(givenToken.tokenStringWithSignature, secret, Seq(algorithm)) match {
      case Success(jsonClaims) => try {
        val parsedUserInfo = upickle.default.read[UserTokenInfo](jsonClaims.toString)
        if parsedUserInfo.expiresAt.isBefore(Instant.now()) then {
          logger.logWarn(s"Dismissed token because it expired at ${parsedUserInfo.expiresAt}!")
          false
        } else if parsedUserInfo != givenToken.info then {
          logger.logWarn("Dismissed token because claimed info does not match actual info")
          false
        } else true
      } catch {
        case e: Exception =>
          logger.logExceptionWarn("Dismissed token because of deserialization or logic error", e)
          false
      }
      case Failure(exception) =>
        logger.logExceptionWarn("Dismissed token because cryptographic signature verification failed", exception)
        false
    }

  def createToken(user: User): SignedToken = {
    val now = Instant.now()
    val tokenInfo = UserTokenInfo(user, now, now.atOffset(ZoneOffset.UTC).plusYears(1).toInstant)
    SignedToken(tokenInfo, JwtJson.encode(tokenInfo.toJson, secret, algorithm))
  }

  /** The first claimed token from the request's cookies or header that verifies. */
  def verifiedToken(cookies: Cookies, headers: Headers, logger: Logger): Option[SignedToken] =
    SignedTokens.claimedTokens(cookies, headers).find(isTokenValid(logger, _))
}

object SignedTokens {
  /** Tokens claimed in the auth cookie or header; unreadable claims are skipped. */
  def claimedTokens(cookies: Cookies, headers: Headers): Seq[SignedToken] = {
    val claimed = cookies.filter(_.name == SignedToken.cookieKey).map(_.value).toList ++ headers.toSimpleMap.get(SignedToken.cookieKey).toList
    Serializer.fromUpickleJson[SignedToken].tryDeserializeAll(claimed).inputAfterOperation.toList
  }
}
