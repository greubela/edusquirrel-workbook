package it.evadid.server

import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{User, UserTokenInfo}
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.server.commandHandler.sql.SqlUserCommands
import it.evadid.util.JvmUtils
import it.evadid.util.logging.Logger
import pdi.jwt.*
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Cookies, Headers}

import java.net.InetAddress
import java.time.{Clock, LocalDateTime}
import scala.util.{Failure, Success}

object AuthHandling {

  val serverSecret: String = JvmUtils.env("SERVER_SECRET").getOrElse("")
  implicit val clock: Clock = Clock.systemUTC
  val algo = JwtAlgorithm.HS256


  def mayCreateAccount(requestedUser: User, logger: Logger): Boolean = {
    val instance = SqlUserCommands.instance(logger)
    val findMail = instance.findUserWithMail(requestedUser.mail)
    val findId = instance.readUserInfoFromDb(requestedUser.id)
    if (findMail.nonEmpty) {
      logger.logWarn(s"Mail ${requestedUser.mail} already associated with users ${findMail.map(_.user.id).mkString(", ")}!")
      false
    } else if (findId.nonEmpty) {
      logger.logWarn(s"Id ${requestedUser.id} already in use (associated with mail ${findId.head.user.mail})!")
      false
    } else {
      true
    }
  }

  def mayAccessIdBased(userIdRequester: String, userIdOwner: String): Boolean = {
    userIdOwner == userIdOwner
  }

  def mayAccessMailBased(mailRequester: String, mailOwner: String): Boolean = {
    mailRequester == mailOwner
  }


  def findAuthCookies(cookies: Cookies, headers: Headers): Seq[SignedToken] = {
    val claimedAuth: Seq[String] = cookies.filter(_.name == SignedToken.cookieKey).map(_.value).toList ++ headers.toSimpleMap.get(SignedToken.cookieKey).toList
    println("claimAuth: " + claimedAuth)
    val deserialized = DefaultSerializer.serializerSignedUserTokenInfo.tryDeserializeAll(claimedAuth)
    deserialized.inputAfterOperation.toList
  }

  def isTokenValid(logger: Logger, givenToken: SignedToken): Boolean = {
    JwtJson.decodeJson(givenToken.tokenStringWithSignature, serverSecret, Seq(JwtAlgorithm.HS256)) match {
      case Success(jsonClaims) => try {
        val parsedUserInfo = DefaultSerializer.serializerUserTokenInfo.deserialize(jsonClaims.toString)

        if (parsedUserInfo.expiresAt.isBefore(LocalDateTime.now())) {
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
    val now = LocalDateTime.now()
    val tokenInfo = UserTokenInfo(user, now, now.plusYears(1))
    val claim = tokenInfo.toJson
    val token = JwtJson.encode(claim, serverSecret, algo)
    SignedToken(tokenInfo, token)
  }


}
