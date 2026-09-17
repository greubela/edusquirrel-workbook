package it.evadid.homepage.control.startup

import it.evadid.core.datastructures.user.User.UserToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.{LoginRequest, LoginResponse}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.util.logging.Logger
import org.scalajs.dom
import upickle.ReadWriter
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object HomepageUserLogic {

  private val allUserInfoStorageKey: String = "edusquirrel-alluserinfo"

  private given ExecutionContext = ExecutionContext.global

  private val serializer: Serializer[AllUserInfo] = DefaultSerializer.serializerAllUserInfo(fullInfo.defaults.defaultSerializerUserConfig)

  def tryParsingExistingUser(logger: Logger): Option[AllUserInfo] = {
    val value: Option[String] = LocalStorageSync.fetchAllRaw(logger).get(allUserInfoStorageKey)
    value.map(serializer.deserialize)
  }

  def forceInput(logger: Logger, prompt: String, defaultText: String): String = {
    val userInput: String = dom.window.prompt(prompt, defaultText)
    if (userInput != null && userInput.nonEmpty) userInput
    else {
      logger.logWarn("Input is required for prompt '" + prompt + "'")
      forceInput(logger, prompt, defaultText)
    }
  }

  def createNewUser(logger: Logger, name: String, mail: String): AllUserInfo = {
    val user = User.createNewUser(Some(name), Some(mail))
    val config = UserConfig(HomepageDefaults.defaultSyncLocation)
    AllUserInfo(user, UserToken.generateSecureToken(), config)
  }

  def createNewUser(logger: Logger): AllUserInfo = {
    val name = User.cleanUserName(forceInput(logger, "First and Last Name: ", ""))
    val defaultMail = name.split(" ").mkString(".") + "@student.hu-berlin.de"
    val mail = forceInput(logger, "E-Mail: ", defaultMail)
    createNewUser(logger, name, mail)
  }

  def removeUserFromLocalStorage(): Unit = {
    LocalStorageSync.removeKey(allUserInfoStorageKey)
  }

  def tryLoginWith(logger: Logger, fullInfo: FullInfo, tryUser: Option[AllUserInfo]): Unit = {
    if (tryUser.isEmpty) {
      onLoginFailed(logger, fullInfo, tryUser, Exception("User Information provided was empty!"))
    } else {
      val loginReq = LoginRequest(tryUser.get.user.id, tryUser.get.token.token)
      val loginRes: Future[ExecutionInfoTyped[LoginResponse]] = UserCommands.loginCommand.sendCommandTo(fullInfo.defaults.defaultBackend.executor, loginReq)
      loginRes.onComplete {
        case Failure(err) => onLoginFailed(logger, fullInfo, tryUser, Exception("Login Command Failed, likely because of lack of network connection", err))
        case Success(exInfo: ExecutionInfoTyped[LoginResponse]) => {
          val loginResponse = exInfo.resultTyped.result
          val userInfo = loginResponse.toInfo(fullInfo.defaults.defaultSerializerUserConfig)
          if (userInfo.nonEmpty) onLoginSuceeded(logger, fullInfo, userInfo.get)
          else if (!loginResponse.isUserKnown) {
            removeUserFromLocalStorage()
            onLoginFailed(logger, fullInfo, tryUser, Exception(s"Local Login failed: user ${tryUser.get.user} is not known on the server!"))
          } else if (!loginResponse.isTokenValid) {
            removeUserFromLocalStorage()
            onLoginFailed(logger, fullInfo, tryUser, Exception(s"Local Login failed: invalid token for user ${tryUser.get.user}!"))
          } else {
            onLoginFailed(logger, fullInfo, tryUser, Exception("Local login failed: Server not reachable!"))
          }
        }
      }
    }
  }

  def onLoginSuceeded(logger: Logger, fullInfo: FullInfo, allUserInfo: AllUserInfo): Unit = {
    logger.logInfo(s"Successfully logged in as ${allUserInfo.user.id} (${allUserInfo.user.name}: ${allUserInfo.user.mail}")
    val serialized = serializer.serialize(allUserInfo)
    LocalStorageSync.storeRaw(logger, allUserInfoStorageKey, serialized)
    fullInfo.usageControl.changeUser(Some(allUserInfo))
  }

  def onLoginFailed(logger: Logger, fullInfo: FullInfo, attempt: Option[AllUserInfo], reason: Exception): Unit = {
    // forward to login page
    logger.logExceptionWarn(s"Login attempt for ${attempt} failed, forwarding to login page", reason)

    println("[UGLY HomepageUserLogic] Missing forward to login page..")
  }

  def userStartupLogic(logger: Logger, fullInfo: FullInfo): Unit = {
    val localUser = tryParsingExistingUser(logger)
    if (localUser.nonEmpty) tryLoginWith(logger, fullInfo, localUser)
    else onLoginFailed(logger, fullInfo, None, Exception("no local user available, requiring new login!"))
  }


}