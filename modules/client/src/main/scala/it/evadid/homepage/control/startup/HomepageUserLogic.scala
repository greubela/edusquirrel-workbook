package it.evadid.homepage.control.startup

import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.model.{AllUserInfo, FullInfo, UserConfig}
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.{SyncDestination, SyncInformation}
import org.scalajs.dom
import org.scalajs.dom.html.Dialog
import upickle.ReadWriter
import upickle.default.*

object HomepageUserLogic {

  private val allUserInfoStorageKey: String = "edusquirrel-alluserinfo"

  private given userConfigRW: ReadWriter[UserConfig] = macroRW

  private given syncRW: ReadWriter[SyncInformation] = HomepageDefaults.defaultSyncLocationSerializer.uPickleReadWrite

  private given userRW: ReadWriter[User] = macroRW

  private given allUserInfoRW: ReadWriter[AllUserInfo] = macroRW

  private val serializer: Serializer[AllUserInfo] = Serializer.fromUpickleJson(allUserInfoRW)

  def tryLoadExistingUser(logger: Logger): Option[AllUserInfo] = {
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
    val config = UserConfig(HomepageDefaults.defaultSyncLocation, None)
    AllUserInfo(user, config)
  }

  def createNewUser(logger: Logger): AllUserInfo = {
    val name = User.cleanUserName(forceInput(logger, "First and Last Name: ", ""))
    val defaultMail = name.split(" ").mkString(".") + "@student.hu-berlin.de"
    val mail = forceInput(logger, "E-Mail: ", defaultMail)
    createNewUser(logger, name, mail)
  }

  def userStartupLogic(logger: Logger, fullInfo: FullInfo): AllUserInfo = {
    val user = tryLoadExistingUser(logger).getOrElse(createNewUser(logger))
    val serialized = serializer.serialize(user)
    LocalStorageSync.storeRaw(logger, allUserInfoStorageKey, serialized)
    fullInfo.usageControl.changeUser(Some(user))
    user
  }

}