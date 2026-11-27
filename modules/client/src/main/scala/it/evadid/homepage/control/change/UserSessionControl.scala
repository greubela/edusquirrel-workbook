package it.evadid.homepage.control.change

import it.evadid.workbook.session.SessionData
import it.evadid.core.datastructures.file.LoadedFile
import it.evadid.core.datastructures.user.*
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess
import it.evadid.distribution.command.SerializedException
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.{CreateAccountRequest, LoginRequest, LoginResponse}
import it.evadid.homepage.control.info.WorkbookUserDataAnalyzer
import it.evadid.homepage.control.model.*
import it.evadid.workbook.interaction.sync.SyncResetState
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import upickle.default.*

import com.raquo.airstream.state.Var
import scala.concurrent.*
import scala.util.{Failure, Success}

/** Authentication, session restoration and user transitions; navigation has its own control. */
final class UserSessionControl(val fullInfo: FullInfo) {
  private given ExecutionContext = ExecutionContext.global
  private def interactions: Seq[WorkbookInteractionElement[? <: InteractionFormat]] = fullInfo.current.allAvailableInteractions
  private val logger = fullInfo.loggerSystemInfo.contentControlLogger
  private val storage = new UserSessionStorage(logger, fullInfo.loggerSystemInfo.syncControlLogger)

  def loadSavedProfile(): Option[AllUserInfo] = storage.loadSavedProfile()

  /** Name and email the login page pre-fills: the last user (also an anonymous local one), kept after its cached
    * login is cleared.
    */
  val loginIdentity: Var[Option[User]] = Var(loadSavedProfile().map(_.user))

  private def rememberIdentity(user: User): Unit = loginIdentity.set(Some(user))

  private def tryServerLoginWith(userMail: String, token: Either[SingleAccessToken, SignedToken], cachedCredentials: UserCredentials = UserCredentials()): Future[?] = {
    val loginReq = LoginRequest(userMail.trim.toLowerCase, token)
    val backend = token match {
      case Left(_) => fullInfo.backendExecutor
      case Right(signed) => fullInfo.defaults.backendExecutorWithCredentials(Some(signed))
    }
    val loginRes: Future[ExecutionSuccess[LoginResponse]] = UserCommands.loginCommand.sendCommandTo(backend, loginReq)
    loginRes.flatMap { exInfo =>
      val loginResponse = exInfo.result
      loginFailureKey(loginResponse) match {
        case Some(messageKey) =>
          clearSavedLogin()
          throw AuthenticationFailure(messageKey)
        case None =>
          logger.logInfo(s"Successfully logged in for ${loginResponse.allUserInfo.get.user}")
          changeUser(loginResponse.allUserInfo.map { info =>
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

  /** The login page's message for a response that does not sign the user in. */
  private def loginFailureKey(response: LoginResponse): Option[String] =
    if !response.userKnown then Some("login/errorUnknownUser")
    else if !response.tokenValid then Some("login/errorInvalidToken")
    else if response.allUserInfo.isEmpty then Some("login/errorLogin")
    else None

  private def clearSavedLogin(): Unit = {
    storage.saveSession(None)
    storage.clearSavedProfile()
  }

  def tryLoginWith(mail: String, singleAccessToken: SingleAccessToken): Future[?] = {
    tryServerLoginWith(mail, Left(singleAccessToken))
  }

  def tryContinueWithSessionFile(loadedFile: LoadedFile): Future[Unit] =
    Future(read[SessionData](loadedFile.fileDataAsUtf8String)).transformWith {
      case Success(session) => continueWithSession(session)
      case Failure(error) =>
        logger.logExceptionWarn("The selected file is not a readable session file", error)
        Future.failed(error)
    }

  /** Signs in as the session's user if needed, opens the session's workbook and restores its answers into it.
    * The returned future completes once the answers are restored.
    */
  def continueWithSession(session: SessionData): Future[Unit] = {
    def sameUser = fullInfo.current.userInfo.exists(_.user.id == session.userInfo.id)
    val login: Future[Unit] = if sameUser then Future.unit else
      tryLoginOrLocalRegistration(Some(session.userInfo), session.userConfig).recover { case scala.util.control.NonFatal(error) =>
        logger.logExceptionWarn("Could not sign in as the session's user; restoring the answers without an account", error)
      }
    login.map { _ =>
      if fullInfo.current.userInfo.isEmpty || sameUser then {
        val workbook = session.workbookToContinue(fullInfo.current.workbookInfo)
        fullInfo.workbookNavigationControl.changeWorkbook(Some(workbook))
        workbook.loadedWorkbook.restoreHistories(logger, session.knownUsageData, fullInfo.workbookContextInformation)
      } else logger.logWarn("Ignored a session file of another user")
    }
  }

  /** An online account is never continued as a local profile: if it cannot be signed in, the login page
    * shows its name and email.
    */
  def tryLoginOrLocalRegistration(withUser: Option[User], withConfig: Option[UserConfig]): Future[Unit] =
    (withUser, withConfig) match {
      case (Some(user), Some(config)) if config.isOnlineAccount =>
        logger.logInfo("TryLoginOrLocalRegistration -> requested with online account, trying to log in")
        tryOnlineRegistration(user.name, user.mail).recoverWith { case err =>
          logger.logExceptionWarn(s"Could not sign in $user; the login page shows the account instead", err)
          rememberIdentity(user)
          Future.failed(err)
        }
      case _ => tryLocalRegistration(withUser, withConfig)
    }

  /** Completes once the local profile is the page's user. */
  def tryLocalRegistration(withUser: Option[User], withConfig: Option[UserConfig]): Future[Unit] = Future {
    val userConfig: UserConfig = withConfig.getOrElse(UserConfig.defaults(isLocalAccount = true))
    val user: User = withUser.getOrElse(User.createAnonymous())
    AllUserInfo(user, UserCredentials(signedBackendToken = None), userConfig)
  }.flatMap(allUserInfo => changeUser(Some(allUserInfo))).map(_ => ())

  def tryOnlineRegistration(name: String, email: String): Future[Unit] = {
    val normalizedEmail = email.trim.toLowerCase
    if (!User.isEmail(normalizedEmail)) Future.failed(AuthenticationFailure("login/errorEmail"))
    else if (name.trim.isEmpty) Future.failed(AuthenticationFailure("login/errorName"))
    else {
      val userConfig = UserConfig.defaults(isLocalAccount = false)
      val user = AllUserInfo.createNewUser(name.trim, normalizedEmail, userConfig)
      UserCommands.createAccountCommand
        .sendCommandTo(fullInfo.backendExecutor, CreateAccountRequest(user))
        .flatMap { exInfo =>
          val result = exInfo.result
          result.token match {
            case _ if !result.accountCreated =>
              logger.logWarn("registration refused: account already exists")
              Future.failed(AuthenticationFailure("login/errorAccountExists"))
            case None => Future.failed(AuthenticationFailure("login/errorRegistration"))
            case Some(token) =>
              val aui = AllUserInfo(token.info.user, UserCredentials(signedBackendToken = result.token), user.config)
              changeUser(Some(aui)).map(_ => ())
          }
        }.recoverWith { case err =>
          logger.logExceptionWarn("registration attempt failed", err)
          Future.failed(err)
        }
    }
  }

  /** Restores a local profile (also an anonymous one) or signs in an online account whose cached token the backend
    * accepts. Otherwise the login page stays, pre-filled with the cached name and email, and the cached login is cleared.
    */
  def tryAutoLogin(): Future[?] = storage.restoreSession().flatMap { cached =>
    cached.orElse(loadSavedProfile()) match {
      case None => Future.failed(SerializedException("No cached login available"))
      case Some(info) =>
        rememberIdentity(info.user)
        val restored: Future[?] =
          if info.config.isOnlineAccount then info.credentials.signedBackendToken.fold(
            Future.failed(AuthenticationFailure("login/errorLogin")))(token => tryServerLoginWith(info.user.mail, Right(token), info.credentials))
          else changeUser(Some(info))
        restored.recoverWith { case error =>
          clearSavedLogin()
          Future.failed(error)
        }
    }
  }

  def changeUser(newUserInfo: Option[AllUserInfo]): Future[?] = {
    val oldUser = fullInfo.homepageInfoNow().userInfo
    logger.logInfo(s"Change User: ${oldUser.map(_.user.mail).getOrElse("none")} -> ${newUserInfo.map(_.user.mail).getOrElse("none")}")

    if (newUserInfo == oldUser) Future.successful(()) else {
      val waitFor: Future[?] = if (oldUser.isEmpty) Future.successful(()) else {
        WorkbookUserDataAnalyzer().downloadAllData(fullInfo)
        fullInfo.syncControl.ensureSynced()
      }
      val finishing = waitFor.recover { case scala.util.control.NonFatal(error) =>
        logger.logExceptionWarn("Could not finish synchronizing the previous user", error)
      }
      // Normal session changes replace the binding immediately; its captured IO finishes independently.
      // A full reset also clears shared local answer stores, so finish that deletion before switching users.
      val ready = fullInfo.syncControl.resetState.now() match {
        case _: SyncResetState.Running => finishing
        case _ => Future.unit
      }
      ready.flatMap(_ => {
        installUser(newUserInfo)
        storage.saveSession(newUserInfo).flatMap { _ =>
          if (newUserInfo.isEmpty || fullInfo.current.workbookInfo.isEmpty) Future.successful(()) else {
            fullInfo.syncControl.ensureSynced()
          }
        }
      }).recover {
        err => logger.logExceptionWarn("Could not complete the user session change", err)
      }
    }
  }

  def updateCredentials(credentials: UserCredentials): Future[?] = {
    require(fullInfo.current.userInfo.nonEmpty, "Create or log in to a profile first")
    fullInfo.homepageInfoState.update(info => info.copy(userInfo = info.userInfo.map(_.copy(credentials = credentials))))
    storage.saveSession(fullInfo.current.userInfo).flatMap(_ => fullInfo.syncControl.ensureSynced())
  }

  private def installUser(newUserInfo: Option[AllUserInfo]): Unit = {
    newUserInfo.foreach(info => rememberIdentity(info.user))
    storage.saveProfile(newUserInfo)
    // Browser storage is optional. It must never prevent clearing session credentials.
    if (fullInfo.current.userInfo.map(_.user.personId) != newUserInfo.map(_.user.personId)) {
      fullInfo.invalidateFeedbackSession()
      interactions.foreach(_.interactionVariable.resetForNewSession())
    }
    fullInfo.homepageInfoState.update(_.copy(userInfo = newUserInfo))
  }
}
