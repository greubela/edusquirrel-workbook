package it.evadid.homepage.control.change

import it.evadid.core.datastructures.user.{UserCredentials, SavedUserPreferences}

import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.core.datastructures.user.{AllUserInfo, User, UserConfig}
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.{CreateAccountRequest, LoginRequest, LoginResponse}
import it.evadid.homepage.control.info.{SessionData, WorkbookUserDataAnalyzer}
import it.evadid.homepage.control.model.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.homepage.workbook.syncDestination.{LocalStorageSyncHandler, LocalIndexedDbStorageSyncHandler}
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import upickle.default.*

import scala.concurrent.*
import scala.scalajs.js
import scala.util.{Failure, Success}

case class HomepageUsageControl(fullInfo: FullInfo) {

  private given ExecutionContext = ExecutionContext.global

  private def interactions: List[WorkbookInteractionUntyped] = fullInfo.current.allAvailableInteractions

  private val logger = fullInfo.loggerSystemInfo.contentControlLogger

  def changeDisplay(func: AllDisplayInfo => AllDisplayInfo): Unit = fullInfo.synchronized {
    updateInfoWithoutContextChange((curInfo: HomepageInfo) => curInfo.copy(displayInfo = func(curInfo.displayInfo)))
  }

  private[change] def updateInfoWithoutContextChange(func: HomepageInfo => HomepageInfo): Future[?] = fullInfo.synchronized {
    fullInfo.syncControl
      .ensureFetchAndLoad(interactions.map(_.interactionVariable))
      .recover { case err =>
        fullInfo.loggerSystemInfo.syncCacheLogger.logExceptionWarn(
          "continuing display update despite fetchAndLoad failure",
          err
        )
        ()
      }
      .map(_ => fullInfo.homepageInfoState.update(func))
  }

  def changeWorkbook(factory: WorkbookFactory): Unit = fullInfo.synchronized {
    changeWorkbook(Some(factory.createEverything))
  }

  def changeWorkbook(newWorkbook: Option[AllWorkbookInfo]): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    if (fullInfo.homepageInfoNow().workbookInfo != newWorkbook) {
      //updateInfoWithContextChange(_.copy(workbookInfo = newWorkbook))
      fullInfo.homepageInfoState.update(_.copy(workbookInfo = newWorkbook))
      if (newWorkbook.isEmpty) Future.successful(()) else {
        fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable))
      }
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

  private val sessionStorageKey = "edusquirrel-session"
  private val sessionCache = LocalIndexedDbStorageSyncHandler(LocalIndexedDbStorageSyncHandler.instanceForCaching)

  private def saveSession(info: Option[AllUserInfo]): Future[Unit] = {
    val stored = info match {
      case Some(user) => sessionCache.storeElementRaw(fullInfo.syncControl.syncLogger, sessionStorageKey, write(user))
      case None => sessionCache.removeKeyRaw(fullInfo.syncControl.syncLogger, sessionStorageKey)
    }
    stored.map(_ => ()).recover { case scala.util.control.NonFatal(error) =>
      logger.logExceptionWarn("Could not update cached login", error)
    }
  }

  private def restoreSession(): Future[Option[AllUserInfo]] =
    sessionCache.readElementRaw(fullInfo.syncControl.syncLogger, sessionStorageKey).transformWith {
      case Failure(_: NoSuchElementException) => Future.successful(None)
      case Failure(error) =>
        logger.logExceptionWarn("Could not read cached login", error)
        Future.successful(None)
      case Success(raw) => scala.util.Try(read[AllUserInfo](raw)) match {
        case Success(info) => Future.successful(Some(info))
        case Failure(_) =>
          logger.logWarn("Discarded an invalid cached login; please sign in again.")
          saveSession(None).map(_ => None)
      }
    }

  def tryParsingExistingUser(): Option[AllUserInfo] = {
    val restored: scala.util.Try[Option[AllUserInfo]] = scala.util.Try(Option(org.scalajs.dom.window.localStorage.getItem(allUserInfoStorageKey)))
      .flatMap {
        case None => Success(None)
        case Some(raw) => scala.util.Try {
          val json = ujson.read(raw)
          require(json.obj.keySet.toSet == Set("user", "config"), "Saved credentials are not supported")
          val saved = read[SavedUserPreferences](json)
          Some(AllUserInfo(saved.user, UserCredentials(), saved.config))
        }
      }
    restored.recover { case scala.util.control.NonFatal(_) =>
      scala.util.Try(org.scalajs.dom.window.localStorage.removeItem(allUserInfoStorageKey))
      logger.logWarn("Discarded an invalid saved login; please sign in again.")
      None
    }.get
  }

  private def tryServerLoginWith(userMail: String, token: Either[SingleAccessToken, SignedToken], cachedCredentials: UserCredentials = UserCredentials()): Future[?] = {
    val loginReq = LoginRequest(userMail.trim.toLowerCase, token)
    val backend = token.match {
      case Left(sat) => fullInfo.defaults.backendExecutor
      case Right(sig) => fullInfo.defaults.backendExecutorWithCredentials(Some(sig))
    }
    val loginRes: Future[ExecutionSuccess[LoginResponse]] = UserCommands.loginCommand.sendCommandTo(backend, loginReq)
    loginRes.flatMap {
      case (exInfo: ExecutionSuccess[LoginResponse]) =>
        val loginResponse = exInfo.result
        val userInfoOp = loginResponse.allUserInfo
        if (!loginResponse.userKnown) {
          clearSavedLogin()
          throw AuthenticationFailure("login/errorUnknownUser")
        } else if (!loginResponse.tokenValid) {
          clearSavedLogin()
          throw AuthenticationFailure("login/errorInvalidToken")
        } else if (userInfoOp.isEmpty) {
          clearSavedLogin()
          throw AuthenticationFailure("login/errorLogin")
        } else {
          logger.logInfo(s"Successfully logged in for ${userInfoOp.get.user}")
          changeUser(userInfoOp.map { info =>
            info.copy(credentials = cachedCredentials.copy(
              signedBackendToken = info.credentials.signedBackendToken,
              singleAccessToken = token.left.toOption.orElse(cachedCredentials.singleAccessToken)))
          })
        }
    }.recoverWith { case err =>
      logger.logExceptionWarn("login attempt failed", err)
      Future.failed(err)
    }
  }

  private def clearSavedLogin(): Unit = {
    saveSession(None)
    scala.util.Try(LocalStorageSyncHandler.removeKey(allUserInfoStorageKey))
  }

  def tryLoginWith(mail: String, singleAccessToken: SingleAccessToken): Future[?] = {
    tryServerLoginWith(mail, Left(singleAccessToken))
  }

  def tryContinueWithSessionFile(loadedFile: LoadedFile): Future[Unit] = Future {
    val logger = fullInfo.loggerSystemInfo.uiAndDomLogger
    logger.logInfo("WorkbookUserDataAnalyzer: now trying to load prio session data!")

    val sessionData = read[SessionData](loadedFile.fileDataAsUtf8String)

    val loginFuture: Future[?] = if (fullInfo.current.userInfo.isEmpty || fullInfo.current.userInfo.get.user.id != sessionData.userInfo.id) {
      tryLoginOrLocalRegistration(Some(sessionData.userInfo), sessionData.userConfig)
    } else Future.successful(())


    def actuallyLoadData(): Unit = {
      if (fullInfo.current.userInfo.isEmpty || fullInfo.current.userInfo.get.user.id == sessionData.userInfo.id) {
        fullInfo.current.workbookInfo.foreach(_.loadedWorkbook.loadDataNoUserCheck(
          logger, sessionData.knownUsageData, fullInfo.executionContextInformation
        ))
      }
    }

    loginFuture.onComplete {
      case Success(_) => actuallyLoadData()
      case Failure(err) => {
        logger.logExceptionWarn("Could not locally log in even though that should work. Loading without user account set", err)
        actuallyLoadData()
      }
    }

  }

  def tryLoginOrLocalRegistration(withUser: Option[User], withConfig: Option[UserConfig]): Future[Unit] = {
    if (withUser.nonEmpty && withConfig.nonEmpty && withConfig.get.isOnlineAccount) {
      logger.logInfo("TryLoginOrLocalRegistration -> requested with online account, trying to log in")
      tryOnlineRegistration(withUser.get.name, withUser.get.mail).recover {
        case (err: Throwable) => {
          logger.logExceptionWarn(s"TryLoginOrLocalRegistration did not succeed for user ${withUser.get}, likely because of missing credentials. Using local mode!", err)
          tryLocalRegistration(withUser, withConfig)
        }
      }
    } else {
      tryLocalRegistration(withUser, withConfig)
    }
  }

  def tryLocalRegistration(withUser: Option[User], withConfig: Option[UserConfig]): Future[Unit] = Future {
    val userConfig: UserConfig = withConfig.getOrElse(UserConfig(UserConfig.defaultSyncDestinations, false))
    val user: User = withUser.getOrElse(User.createNewUser("Anonymous", "no-reply@evadid.it"))
    val allUserInfo = AllUserInfo(user, UserCredentials(signedBackendToken = None), userConfig)
    changeUser(Some(allUserInfo))
  }

  def tryOnlineRegistration(name: String, email: String): Future[Unit] = {
    val normalizedEmail = email.trim.toLowerCase
    if (!User.isEmail(normalizedEmail)) Future.failed(AuthenticationFailure("login/errorEmail"))
    else if (name.trim.isEmpty) Future.failed(AuthenticationFailure("login/errorName"))
    else {
      val userConfig = UserConfig(UserConfig.defaultSyncDestinations, true)
      val user = AllUserInfo.createNewUser(name.trim, normalizedEmail, userConfig)
      UserCommands.createAccountCommand
        .sendCommandTo(fullInfo.defaults.backendExecutor, CreateAccountRequest(user))
        .flatMap { exInfo =>
          val result = exInfo.result
          if (!result.accountCreated) {
            logger.logWarn("registration refused: account already exists")
            Future.failed(AuthenticationFailure("login/errorAccountExists"))
          } else if (result.token.isEmpty) {
            Future.failed(AuthenticationFailure("login/errorRegistration"))
          } else {
            val aui = AllUserInfo(result.token.get.info.user, UserCredentials(signedBackendToken = result.token), user.config)
            changeUser(Some(aui)).map(_ => ())
          }
        }.recoverWith { case err =>
          logger.logExceptionWarn("registration attempt failed", err)
          Future.failed(err)
        }
    }
  }

  def tryAutoLogin(): Future[?] = restoreSession().flatMap { cached =>
    cached.orElse(tryParsingExistingUser()) match {
      case None => Future.failed(SerializedException("No cached login available"))
      case Some(info) if info.config.isOnlineAccount =>
        info.credentials.signedBackendToken match {
          case Some(token) => tryServerLoginWith(info.user.mail, Right(token), info.credentials)
          case None => Future.failed(AuthenticationFailure("login/errorLogin"))
        }
      case Some(info) => changeUser(Some(info))
    }
  }

  def changeUser(newUserInfo: Option[AllUserInfo]): Future[?] = fullInfo.synchronized {

    def actuallySetNewUser(): Unit = {
      try {
        LocalStorageSyncHandler.removeKey(allUserInfoStorageKey)
        newUserInfo.foreach { allUserInfo =>
          logger.logInfo(s"Loading user ${allUserInfo.toString}")
          val serialized = write(SavedUserPreferences(allUserInfo.user, allUserInfo.config.withoutCredentials))
          LocalStorageSyncHandler.instance.storeElementRaw(fullInfo.syncControl.syncLogger, allUserInfoStorageKey, serialized)
            .failed.foreach(error => logger.logExceptionWarn("Could not save user preferences", error))
        }
      } catch case scala.util.control.NonFatal(error) =>
        logger.logExceptionWarn("Could not update saved user preferences", error)
      // Browser storage is optional. It must never prevent clearing session credentials.
      fullInfo.homepageInfoState.update(_.copy(userInfo = newUserInfo))
    }

    val oldUser = fullInfo.homepageInfoNow().userInfo
    logger.logInfo(s"Change User: ${oldUser.map(_.user.mail).getOrElse("none")} -> ${newUserInfo.map(_.user.mail).getOrElse("none")}")

    if (newUserInfo == oldUser) Future.successful(()) else {
      val waitFor: Future[?] = if (oldUser.isEmpty) Future.successful(()) else {
        WorkbookUserDataAnalyzer().downloadAllData(fullInfo)
        fullInfo.syncControl.storeAndReset(interactions.map(_.interactionVariable)).recover {
          err => logger.logExceptionWarn("Ignore Store and Reset!", err)
        }
      }
      waitFor.flatMap(res => {
        actuallySetNewUser()
        saveSession(newUserInfo).flatMap { _ =>
          if (newUserInfo.isEmpty || fullInfo.current.workbookInfo.isEmpty) Future.successful(()) else {
            fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable))
          }
        }
      }).recover {
        err => logger.logExceptionWarn("Ignore Fetch and Load!", err)
      }
    }
  }

  def updateCredentials(credentials: UserCredentials): Future[?] = {
    require(fullInfo.current.userInfo.nonEmpty, "Create or log in to a profile first")
    fullInfo.homepageInfoState.update(info => info.copy(userInfo = info.userInfo.map(_.copy(credentials = credentials))))
    saveSession(fullInfo.current.userInfo).flatMap(_ => fullInfo.syncControl.ensureFetchAndLoad(interactions.map(_.interactionVariable)))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }

}

object HomepageUsageControl {


}
