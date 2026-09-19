package it.evadid.homepage.control.startup

import it.evadid.core.datastructures.user.User.*
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.{AuthMailRequest, LoginRequest, LoginResponse}
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.util.logging.Logger
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}

object HomepageUserLogic {


  private[startup] val allUserInfoStorageKey: String = "edusquirrel-alluserinfo"
  private[startup] val serializer: Serializer[AllUserInfo] = DefaultSerializer.serializerAllUserInfo(fullInfo.defaults.defaultSerializerUserConfig)

  private given ExecutionContext = ExecutionContext.global


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
    AllUserInfo(user, None, config)
  }

  def createNewUser(logger: Logger): AllUserInfo = {
    val name = User.cleanUserName(forceInput(logger, "First and Last Name: ", ""))
    val defaultMail = name.split(" ").mkString(".") + "@student.hu-berlin.de"
    val mail = forceInput(logger, "E-Mail: ", defaultMail)
    createNewUser(logger, name, mail)
  }





  def onLoginFailed(logger: Logger, fullInfo: FullInfo, userInfo: Option[User], reason: Exception): Boolean = {
    logger.logExceptionWarn(s"Login attempt failed, forwarding to login page", reason)
    false
  }

}