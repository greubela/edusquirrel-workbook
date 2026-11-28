package it.evadid.homepage.control.change


import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.language.control.LanguageMapStorage
import it.evadid.core.datastructures.user.*
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.UserTokenInfo.SignedToken
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.*
import it.evadid.homepage.control.FakeBackend
import it.evadid.homepage.control.model.{AllDisplayInfo, FullInfo, HomepageInfo}
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import org.scalajs.dom
import upickle.default.write
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/** Sign-in, registration and session changes against an in-memory backend. */
class UserSessionControlSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals :+ "localStorage"
  private val backend = FakeBackend()
  override def beforeEach(context: BeforeEach): Unit = { dom.window.localStorage.clear(); backend.install() }
  override def afterEach(context: AfterEach): Unit = backend.uninstall()

  private def page() = FullInfo(HomepageInfo(AppLanguage.German, None, None, AllDisplayInfo(false, None), LanguageMapStorage.empty))
  private val localConfig = UserConfig(Nil, isOnlineAccount = false)
  private val student = AllUserInfo.createNewUser("Ada Student", "ada@school.example", UserConfig(Nil, isOnlineAccount = true))
  private def signed(user: User) = SignedToken(UserTokenInfo(user, Instant.now(), Instant.now().plus(java.time.Duration.ofDays(1))), "signature")
  private def failure[T](future: Future[T]): Future[String] = future.failed.map {
    case AuthenticationFailure(key) => key
    case other => s"unexpected: $other"
  }

  test("registration validates mail and name, reports refused accounts and signs in with the issued token") {
    val control = page().userSessionControl
    val issued = Var(Vector.empty[CreateAccountRequest])
    val responses = Var(List(CreateAccountResponse(false, None), CreateAccountResponse(true, None)))
    backend.answer(UserCommands.createAccountCommand) { request =>
      issued.update(_ :+ request)
      val next = responses.now().headOption.getOrElse(CreateAccountResponse(true, Some(signed(request.userInfo.user))))
      responses.update(_.drop(1))
      Future.successful(next)
    }
    for {
      invalidMail <- failure(control.tryOnlineRegistration("Ada", "not-a-mail"))
      noName <- failure(control.tryOnlineRegistration("  ", "ada@school.example"))
      exists <- failure(control.tryOnlineRegistration("Ada", "ada@school.example"))
      noToken <- failure(control.tryOnlineRegistration("Ada", "ada@school.example"))
      _ <- control.tryOnlineRegistration(" Ada Student ", "  ADA@School.example ")
    } yield {
      assertEquals(List(invalidMail, noName, exists, noToken), List("login/errorEmail", "login/errorName", "login/errorAccountExists", "login/errorRegistration"))
      assertEquals(issued.now().size, 3, "invalid input is rejected before contacting the backend")
      assertEquals(issued.now().last.userInfo.user.mail, "ada@school.example", "mail addresses are normalized")
      val user = control.fullInfo.current.userInfo.getOrElse(fail("not signed in"))
      assertEquals(user.user.name, "Ada Student")
      assert(user.config.isOnlineAccount)
      assertEquals(user.credentials.signedBackendToken.map(_.info.user), Some(issued.now().last.userInfo.user))
      val saved = dom.window.localStorage.getItem("edusquirrel-alluserinfo")
      assert(saved.contains("ada@school.example") && !saved.contains("signature"), "only public preferences are saved")
    }
  }

  test("signing in with an access token reports unknown users, invalid tokens and missing accounts") {
    val control = page().userSessionControl
    val token = SingleAccessToken("123456")
    val logins = Var(Vector.empty[LoginRequest])
    val responses = Var(List(LoginResponse(false, false, None), LoginResponse(true, false, None), LoginResponse(true, true, None)))
    backend.answer(UserCommands.loginCommand) { request =>
      logins.update(_ :+ request)
      val next = responses.now().headOption.getOrElse(LoginResponse(true, true,
        Some(student.copy(credentials = UserCredentials(signedBackendToken = Some(signed(student.user)))))))
      responses.update(_.drop(1))
      Future.successful(next)
    }
    dom.window.localStorage.setItem("edusquirrel-alluserinfo", write(SavedUserPreferences(student.user, student.config)))
    for {
      unknown <- failure(control.tryLoginWith(" Ada@School.example", token))
      _ = assertEquals(dom.window.localStorage.getItem("edusquirrel-alluserinfo"), null, "a failed login forgets the saved login")
      invalid <- failure(control.tryLoginWith("ada@school.example", token))
      missing <- failure(control.tryLoginWith("ada@school.example", token))
      _ <- control.tryLoginWith("ada@school.example", token)
    } yield {
      assertEquals(List(unknown, invalid, missing), List("login/errorUnknownUser", "login/errorInvalidToken", "login/errorLogin"))
      assertEquals(logins.now().head, LoginRequest("ada@school.example", Left(token)))
      val credentials = control.fullInfo.current.userInfo.get.credentials
      assertEquals(credentials.singleAccessToken, Some(token), "the access token is kept for this session")
      assertEquals(credentials.signedBackendToken.map(_.tokenStringWithSignature), Some("signature"))
      assertEquals(backend.requests(UserCommands.loginCommand.name).head, "", "token logins send no signed token")
    }
  }

  test("an unreachable backend fails the login with a readable error") {
    val control = page().userSessionControl
    control.tryLoginWith("ada@school.example", SingleAccessToken("1")).failed.map { error =>
      assert(error.getMessage.contains("503"), error.getMessage)
      assertEquals(control.fullInfo.current.userInfo, None)
    }
  }

  test("automatic login restores a named local profile; an online account it cannot sign in keeps only its pre-fill") {
    val local = AllUserInfo.createNewUser("Local Learner", "local@school.example", localConfig)
    val control = page().userSessionControl
    for {
      nothing <- control.tryAutoLogin().failed.map(_.getMessage)
      _ = dom.window.localStorage.setItem("edusquirrel-alluserinfo", write(SavedUserPreferences(student.user, student.config)))
      online <- failure(control.tryAutoLogin())
      _ = assertEquals(control.fullInfo.current.userInfo, None, "the login page shows")
      _ = assertEquals(control.loginIdentity.now(), Some(student.user), "pre-filled with the cached account")
      _ = assertEquals(dom.window.localStorage.getItem("edusquirrel-alluserinfo"), null, "the cached login is cleared")
      _ = dom.window.localStorage.setItem("edusquirrel-alluserinfo", write(SavedUserPreferences(local.user, local.config)))
      _ <- control.tryAutoLogin()
    } yield {
      assertEquals(nothing, "No cached login available")
      assertEquals(online, "login/errorLogin", "saved preferences never contain the signed token")
      assertEquals(control.fullInfo.current.userInfo.map(_.user), Some(local.user))
      assertEquals(control.fullInfo.current.userInfo.map(_.credentials), Some(UserCredentials()))
    }
  }

  test("an online account the backend cannot confirm is never continued locally or anonymously") {
    val control = page().userSessionControl
    for {
      _ <- control.tryLoginOrLocalRegistration(Some(student.user), Some(student.config)).failed
    } yield {
      assertEquals(control.fullInfo.current.userInfo, None, "the login page shows")
      assertEquals(control.loginIdentity.now(), Some(student.user), "pre-filled with the account")
    }
  }

  test("a local start without a name is anonymous and survives a reload until an explicit logout") {
    val control = page().userSessionControl
    val local = AllUserInfo.createNewUser("Local Learner", "local@school.example", localConfig)
    for {
      _ <- control.tryLocalRegistration(Some(local.user), Some(localConfig))
      _ <- control.tryLoginOrLocalRegistration(None, None)
      anonymous = control.fullInfo.current.userInfo.getOrElse(fail("not signed in"))
      _ = {
        assert(!anonymous.config.isOnlineAccount, "the config flag marks the account as local")
        assertEquals(anonymous.config, UserConfig.defaults(isLocalAccount = true), "local users sync only to the browser")
        assertEquals((anonymous.user.name, anonymous.user.mail), ("Anonymous", "anonymous@evadid.it"))
        assertEquals(control.loginIdentity.now(), Some(anonymous.user), "the login page shows the local user's defaults")
      }
      reloaded = page().userSessionControl
      _ <- reloaded.tryAutoLogin()
      _ = {
        assertEquals(reloaded.fullInfo.current.userInfo.map(_.user), Some(anonymous.user), "a reload keeps the local user")
        assertEquals(reloaded.loginIdentity.now(), Some(anonymous.user), "the restored local user pre-fills the login page")
      }
      _ <- reloaded.changeUser(None)
      afterLogout = page().userSessionControl
      reason <- afterLogout.tryAutoLogin().failed.map(_.getMessage)
    } yield {
      assertEquals(reason, "No cached login available")
      assertEquals(afterLogout.fullInfo.current.userInfo, None, "logging out ends the local session")
      assertEquals(dom.window.localStorage.getItem("edusquirrel-alluserinfo"), null)
    }
  }

  test("changing users replaces the bindings; repeating a change or signing out keeps state consistent") {
    val element = it.evadid.workbook.interactions.basic.TextInteraction("answer")
    val section = it.evadid.workbook.elements.structure.WorkbookSection("section",
      it.evadid.workbook.elements.structure.WorkbookSection.WorkbookSectionMetadata(LanguageMapContentId("spec/section")), Seq(element))
    val workbook = it.evadid.workbook.elements.structure.Workbook("workbook", it.evadid.workbook.elements.structure.Workbook.WorkbookMetadata(
      Set.empty, Set.empty, LanguageMapContentId("spec/title"), Seq(AppLanguage.English)), Seq(section))
    val info = FullInfo(HomepageInfo(AppLanguage.German, Some(it.evadid.workbook.session.AllWorkbookInfo(workbook,
      it.evadid.workbook.session.WorkbookConfig(Some(section)), Map.empty)), None, AllDisplayInfo(false, None), LanguageMapStorage.empty))
    val control = info.userSessionControl
    val first = AllUserInfo.createNewUser("First", "first@school.example", localConfig)
    val second = AllUserInfo.createNewUser("Second", "second@school.example", localConfig)
    def session = info.interactions.info.session
    val initial = session
    for {
      _ <- control.changeUser(Some(first))
      afterFirst = session
      _ <- control.changeUser(Some(first))
      _ = assertEquals(session, afterFirst, "the same user keeps the answers")
      _ <- control.changeUser(Some(second))
      _ = assertNotEquals(session, afterFirst, "another user gets default answers of their own")
      _ = assert(dom.window.localStorage.getItem("edusquirrel-alluserinfo").contains("second@school.example"))
      _ <- control.changeUser(None)
    } yield {
      assertNotEquals(afterFirst, initial)
      assertEquals(info.current.userInfo, None)
      assertEquals(dom.window.localStorage.getItem("edusquirrel-alluserinfo"), null, "signing out removes the saved login")
      intercept[IllegalArgumentException](control.updateCredentials(UserCredentials()))
    }
  }

  test("the page starts in German") {
    assertEquals(it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo.homepageInfoNow().currentLanguage, AppLanguage.German)
  }

  test("credentials, display, language and workbook settings change only the page's state") {
    val info = page()
    val control = info.userSessionControl
    val user = AllUserInfo.createNewUser("Cred", "cred@school.example", localConfig)
    info.displayControl.updateDisplay(_.copy(collapsedNavigation = true))
    assert(info.homepageInfoNow().displayInfo.collapsedNavigation)
    info.displayControl.changeLanguage(AppLanguage.English)
    assertEquals(info.homepageInfoNow().currentLanguage, AppLanguage.English)
    info.workbookNavigationControl.updateWorkbookConfig(identity)
    assertEquals(info.homepageInfoNow().workbookInfo, None, "no workbook, nothing to configure")
    val llm = it.evadid.llm.LlmConfiguration("http://localhost:1234/v1/chat/completions", "local-model", None, Some(100))
    for {
      _ <- control.changeUser(Some(user))
      _ <- control.updateCredentials(UserCredentials(llmApiConfig = Some(llm)))
    } yield {
      assertEquals(info.current.userInfo.flatMap(_.credentials.llmApiConfig), Some(llm))
      assertEquals(info.workbookContextInformation.llmConfiguration, Some(llm), "feedback requests use the new configuration")
      assert(!dom.window.localStorage.getItem("edusquirrel-alluserinfo").contains("local-model"), "credentials stay out of browser storage")
    }
  }
}
