package it.evadid.homepage.control.change

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.{AuthMailRequest, LoginRequest, LoginResponse}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.workbook.abstractions.WorkbookInteractionElement

import scala.concurrent.*
import scala.util.Success


case class HomepageUsageControl(fullInfo: FullInfo) {

  private given ExecutionContext = ExecutionContext.global

  private def interactions: List[WorkbookInteractionElement[?]] = fullInfo.current.allAvailableInteractions

  private val logger = fullInfo.loggerSystemInfo.contentControlLogger

  def changeDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit = fullInfo.synchronized {
    updateInfoWithoutContextChange((curInfo: HomepageInfo) => curInfo.copy(displayInfo = func(curInfo.displayInfo)))
  }

  def updateInfoWithContextChange(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {
    fullInfo.syncControl
      .storeAndReset(interactions.map(_.interactionVariable))
      .flatMap(_ => {
        fullInfo.homepageInfoState.update(func)
        fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable))
      })

  }

  private[change] def updateInfoWithoutContextChange(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {
    fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable)).map(_ => {
      fullInfo.homepageInfoState.update(func)
    })
  }

  def changeWorkbook(factory: WorkbookFactory): Unit = fullInfo.synchronized {
    changeWorkbook(Some(factory.createEverything))
  }

  def changeWorkbook(newWorkbook: Option[AllWorkbookInfo]): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    if (fullInfo.homepageInfoNow().workbookInfo != newWorkbook) {
      updateInfoWithContextChange(_.copy(workbookInfo = newWorkbook))
    }
  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoState.now().workbookInfo.nonEmpty) {
      val newInfo: Option[AllWorkbookInfo] = fullInfo.homepageInfoState.now().workbookInfo.map(info => info.copy(config = func(info.config)))
      updateInfoWithoutContextChange(curInfo => curInfo.copy(workbookInfo = newInfo))
      //cacheControl.fetchAndStore(interactions.map(_.interactionVariable))
    } else {
      logger.logWarn("[WARN] ignore updated workbook config because there is no workbook loaded!")
    }

  }

  /* USER */
  private val allUserInfoStorageKey: String = "edusquirrel-alluserinfo"
  private val serializer: Serializer[AllUserInfo] = DefaultSerializer.serializerAllUserInfo(fullInfo.defaults.defaultSerializerUserConfig)

  private def tryParsingExistingUser(): Option[AllUserInfo] = {
    val value: Option[String] = LocalStorageSync.fetchAllRaw(logger).get(allUserInfoStorageKey)
    value.map(serializer.deserialize)
  }

  def tryServerLoginWith(userMail: String, token: Either[SingleAccessToken, SignedToken]): Future[AllUserInfo] = {
    val loginReq = LoginRequest(userMail, token)
    val loginRes: Future[ExecutionInfoTyped[LoginResponse]] = UserCommands.loginCommand.sendCommandTo(fullInfo.defaults.defaultBackend.executor, loginReq)
    loginRes.map {
      case (exInfo: ExecutionInfoTyped[LoginResponse]) =>
        val loginResponse = exInfo.resultTyped.result
        val userInfo = loginResponse.toInfo(fullInfo.defaults.defaultSerializerUserConfig).get
        if (!loginResponse.userKnown) {
          removeUserFromLocalStorage()
          throw SerializedException(s"Local Login failed: user ${userMail} is not known on the server!")
        } else if (!loginResponse.tokenValid) {
          UserCommands.authMailCommand.sendCommandTo(fullInfo.defaults.defaultBackend.executor, AuthMailRequest(userMail))
          throw SerializedException(s"Local Login failed: invalid token for user ${userMail}, requested SingleAccessToken!")
        } else {
          removeUserFromLocalStorage()
          throw SerializedException("Login failed for unknown reasons!")
        }
    }
  }

  def removeUserFromLocalStorage(): Unit = {
    LocalStorageSync.removeKey(allUserInfoStorageKey)
  }

  def tryAutoLogin(): Future[Unit] = {
    val localUser = tryParsingExistingUser()
    if (localUser.isEmpty || localUser.get.token.isEmpty) {
      Future.failed[Unit](SerializedException("No local user with token stored to try auto login with!"))
    } else {
      val futRes = tryServerLoginWith(localUser.get.user.mail, Right(localUser.get.token.get))
      futRes.onComplete { case Success(allUserInfo: AllUserInfo) => changeUser(Some(allUserInfo)) }
      futRes.map( _ => ())
    }
  }

  def changeUser(userInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    if (userInfo != fullInfo.homepageInfoNow().userInfo) {
      val allUserInfo = userInfo.get
      logger.logInfo(s"Loading user ${allUserInfo.user.id} (${allUserInfo.user.name}: ${allUserInfo.user.mail}")
      LocalStorageSync.removeKey(allUserInfoStorageKey)
      val serialized = serializer.serialize(allUserInfo)
      LocalStorageSync.storeRaw(logger, allUserInfoStorageKey, serialized)
      updateInfoWithContextChange(_.copy(userInfo = userInfo))
    }
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}

object HomepageUsageControl {


}