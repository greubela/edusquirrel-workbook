package it.evadid.homepage.control.change

import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionInfo.ExecutionInfoTyped
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.{AuthMailRequest, CreateAccountRequest, LoginRequest, LoginResponse}
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer
import it.evadid.homepage.control.model.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.workbook.abstractions.WorkbookInteractionElement

import scala.concurrent.*
import scala.scalajs.js
import scala.util.{Failure, Success}


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
  private val serializer: Serializer[AllUserInfo] = DefaultSerializer.serializerAllUserInfo(HomepageDefaults.defaultSerializerUserConfig)

  private def tryParsingExistingUser(): Option[AllUserInfo] = {
    val value: Option[String] = LocalStorageSync.fetchAllRaw(logger).get(allUserInfoStorageKey)
    serializer.tryDeserializeAll(value).inputAfterOperation.headOption
  }

  private def tryServerLoginWith(userMail: String, token: Either[SingleAccessToken, SignedToken]): Future[Unit] = {
    val loginReq = LoginRequest(userMail, token)
    val backend = token.match {
      case Left(sat) => fullInfo.defaults.backendExecutor
      case Right(sig) => fullInfo.defaults.backendExecutorWithCredentials(Some(sig))
    }
    val loginRes: Future[ExecutionInfoTyped[LoginResponse]] = UserCommands.loginCommand.sendCommandTo(backend, loginReq)
    loginRes.map {
      case (exInfo: ExecutionInfoTyped[LoginResponse]) =>
        val loginResponse = exInfo.resultTyped.result
        val userInfoOp = loginResponse.toInfo(HomepageDefaults.defaultSerializerUserConfig)
        if (!loginResponse.userKnown) {
          removeUserFromLocalStorage()
          throw SerializedException(s"Local Login failed: user ${userMail} is not known on the server!")
        } else if (!loginResponse.tokenValid) {
          UserCommands.authMailCommand.sendCommandTo(fullInfo.defaults.backendExecutor, AuthMailRequest(userMail))
          throw SerializedException(s"Local Login failed: invalid token for user ${userMail}, requested SingleAccessToken!")
        } else if (userInfoOp.isEmpty) {
          removeUserFromLocalStorage()
          throw SerializedException("Login failed for unknown reasons!")
        } else {
          changeUser(userInfoOp)
        }
    }.recover { err => logger.logExceptionWarn("login attempt failed", err) }
  }

  def removeUserFromLocalStorage(): Unit = {
    LocalStorageSync.removeKey(allUserInfoStorageKey)
  }

  def tryLoginWith(mail: String, singleAccessToken: SingleAccessToken): Future[Unit] = {
    tryServerLoginWith(mail, Left(singleAccessToken))
  }

  def tryContinueWithSessionFile(loadedFile: LoadedFile): Future[Unit] = Future {
    val logger = fullInfo.loggerSystemInfo.uiAndDomLogger
    logger.logInfo("WorkbookUserDataAnalyzer: now trying to load prio session data!")

    val sessionData = WorkbookUserDataAnalyzer.serializerSessionData.deserialize(loadedFile.fileDataAsUtf8String)
    changeUser(Some(sessionData.currentUserInfo))

    sessionData.interactionHistory.foreachEntry((varId, serHist) => {
      fullInfo.homepageInfoNow().workbookInfo.foreach(curInfo => {
        curInfo.loadedWorkbook.allContainedInteractions.map(_.interactionVariable).foreach(curInteraction => {
          if (curInteraction.keyForSerialization == varId) {
            curInteraction.updateHistory(_.withAddedEvents(serHist, curInteraction.underlyingInteraction.serializer))
          }
        })
      })
    })
  }


  def tryRegistration(name: String, email: String): Future[Unit] = {
    if (!User.isEmail(email)) Future.failed(SerializedException(s"Invalid mail format: ${email}")) else {
      val user = AllUserInfo.createNewUser(name, email, HomepageDefaults.defaultSyncLocation)
      val configJson = HomepageDefaults.defaultSerializerUserConfig.serialize(user.config)
      val createAccFuture: Future[ExecutionInfoTyped[UserCommands.CreateAccountResponse]] = UserCommands.createAccountCommand.sendCommandTo(fullInfo.defaults.backendExecutor, CreateAccountRequest(user.user, configJson))
      createAccFuture.transform {
        case Success(exInfo) =>
          val result = exInfo.resultTyped.result
          if (!result.accountCreated || result.token.isEmpty) {
            logger.logWarn(s"registration attempt ignored (account created: ${result.accountCreated} / token: ${result.token}")
            Failure(SerializedException("could not create account, see logs!"))
          }
          else {
            val aui = AllUserInfo(result.token.get.info.user, result.token, user.config)
            changeUser(Some(aui))
            Success(())
          }
        case Failure(err) =>
          logger.logExceptionWarn(s"registration attempt ignored", err)
          Failure(err)
      }
    }
  }

  def tryAutoLogin(): Future[Unit] = {
    val localUser = tryParsingExistingUser()
    if (localUser.isEmpty || localUser.get.token.isEmpty) {
      Future.failed[Unit](SerializedException("No local user with token stored to try auto login with!"))
    } else {
      tryServerLoginWith(localUser.get.user.mail, Right(localUser.get.token.get))
    }
  }

  def storeUserLocally(allUserInfo: AllUserInfo): Unit = {
    val rawToken = allUserInfo.token.get.toJson
    val safeToken = js.URIUtils.encodeURIComponent(rawToken)
  }

  def changeUser(newUserInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    val oldUser = fullInfo.homepageInfoNow().userInfo
    logger.logInfo(s"Change User: ${oldUser.map(_.user.mail).getOrElse("none")} -> ${newUserInfo.map(_.user.mail).getOrElse("none")}")
    if (newUserInfo != fullInfo.homepageInfoNow().userInfo) {
      LocalStorageSync.removeKey(allUserInfoStorageKey)
      if (newUserInfo.isDefined) {
        val allUserInfo = newUserInfo.get
        logger.logInfo(s"Loading user ${allUserInfo.user.id} (${allUserInfo.user.name}: ${allUserInfo.user.mail}")
        val serialized = serializer.serialize(allUserInfo)
        LocalStorageSync.storeRaw(logger, allUserInfoStorageKey, serialized)
      } else if (oldUser.isDefined) {
        logger.logInfo(s"Logged out user ${oldUser.get.user.id} (${oldUser.get.user.name}: ${oldUser.get.user.mail}")
      }

      updateInfoWithContextChange(_.copy(userInfo = newUserInfo))
    }
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}

object HomepageUsageControl {


}