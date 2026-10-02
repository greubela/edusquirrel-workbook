package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.{AllUserInfo, User}
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.AuthMailRequest
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.util.logging.Logger
import org.scalajs.dom.HTMLInputElement

import scala.concurrent.{ExecutionContext, Future, Promise}

case class HtmlLoginElement() extends HtmlAppElement {

  private given ExecutionContext = ExecutionContext.global

  private val logger: Logger = fullInfo.loggerSystemInfo.uiAndDomLogger

  private val loginSucceededPromise: Promise[AllUserInfo] = Promise()

  private val nameVar: Var[String] = {
    val default = fullInfo.usageControl.tryParsingExistingUser().map(_.user.name).getOrElse("Unknown User")
    Var[String](default)
  }
  private val emailVar: Var[String] = {
    val default = fullInfo.usageControl.tryParsingExistingUser().map(_.user.mail).getOrElse("unknown.user@student.hu-berlin.de")
    Var[String](default)
  }

  nameVar.signal.foreach(newValue => {
    val derived = User.deriveHuMail(newValue)
    if (derived.isDefined && emailVar.now() != derived.get) {
      emailVar.set(derived.get)
    }
  })(using unsafeWindowOwner)

  emailVar.signal.foreach(newValue => {
    val derived = User.deriveNameFromMail(newValue)
    if (derived.isDefined && nameVar.now() != derived.get) {
      nameVar.set(derived.get)
    }
  })(using unsafeWindowOwner)

  def loginSucceededFuture: Future[AllUserInfo] = loginSucceededPromise.future

  def titleElement(id: String, level: Int): Element = {
    def normalizedLevel: Int = math.max(0, math.min(5, level))

    val heading = normalizedLevel match {
      case 0 => h1(text <-- laminarHelper.plaintextStringSignal(id))
      case 1 => h2(text <-- laminarHelper.plaintextStringSignal(id))
      case 2 => h3(text <-- laminarHelper.plaintextStringSignal(id))
      case 3 => h4(text <-- laminarHelper.plaintextStringSignal(id))
      case 4 => h5(text <-- laminarHelper.plaintextStringSignal(id))
      case _ => h6(text <-- laminarHelper.plaintextStringSignal(id))
    }
    div(
      cls := s"container-title container-title-level-${normalizedLevel} structure-element title-login",
      heading
    )
  }


  lazy val domElement: Element = {
    div(
      cls := "container-login",
      div(
        cls := "container-login-section section-register",
        titleElement("login/titleLoginRegister", 1),
        div(
          cls := "container-login-body",
          div(
            cls := "login-area",
            titleElement("login/titleLoginStartOnline", 2),
            onlineStartBody
          ),
          div(
            cls := "login-area",
            titleElement("login/titleLoginStartLocal", 2),
            localStartBody
          ),
        )
      ),
      div(
        cls := "container-login-section section-continue",
        titleElement("login/titleLoginContinue", 1),
        div(
          cls := "container-login-body",
          div(
            cls := "login-area",
            titleElement("login/titleLoginContinueOnline", 2),
            onlineContinueBody
          ),
          div(
            cls := "login-area",
            titleElement("login/titleLoginContinueLocal", 2),
            localContinueBody
          ),
        )

      )
    )
  }


  private val onlineStartBody: List[Element] = {

    List[Element](
      ul(
        li(text <-- laminarHelper.plaintextStringSignal("login/onlineBenefit1")),
        li(text <-- laminarHelper.plaintextStringSignal("login/onlineBenefit2")),
        li(text <-- laminarHelper.plaintextStringSignal("login/onlineBenefit3")),
      ),

      createTextInput(nameVar, "login/insertNamePlaceholder"),
      createEmailInput(emailVar, "login/enterMail"),
      HtmlButtonElement.withTextLabel("login/buttonRequestRegistration", _ => fullInfo.usageControl.tryOnlineRegistration(nameVar.now(), emailVar.now())).getDomElement()
    )
  }

  // Start and Continue together (Insert Mail --> Insert Name or Token)

  private val localStartBody: List[Element] = {

    List(
      ul(
        li(text <-- laminarHelper.plaintextStringSignal("login/localBenefit1")),
        li(text <-- laminarHelper.plaintextStringSignal("login/localBenefit2")),
        li(text <-- laminarHelper.plaintextStringSignal("login/localBenefit3")),
      ),
      HtmlButtonElement.withTextLabel("login/buttonLocalLogin", _ => fullInfo.usageControl.tryLocalRegistration()).getDomElement()
    )
  }

  private val localContinueBody: List[Element] = {

    val uploadInput: ReactiveHtmlElement[HTMLInputElement] = laminarHelper.sessionFileUploadInput(logger, loadedFile => fullInfo.usageControl.tryContinueWithSessionFile(loadedFile))

    List(
      uploadInput,
      HtmlButtonElement.withTextLabel("login/buttonLocalUpload", _ => uploadInput.ref.click()).getDomElement()
    )
  }

  private def createFileInput(): Element = {
    input(
      label(text <-- laminarHelper.plaintextStringSignal("login/uploadFile")),
      typ := "file"
    )
  }

  private def createTextInput(underlyingVar: Var[String], labelId: String): Element = {
    input(
      cls := "login-name-input",
      label(text <-- laminarHelper.plaintextStringSignal(labelId)),
      placeholder <-- laminarHelper.plaintextStringSignal(labelId),
      typ := "text",
      controlled(
        value <-- underlyingVar.signal,
        onInput.mapToValue --> underlyingVar.writer
      )
    )
  }


  private def createEmailInput(underlyingVar: Var[String], labelId: String): Element = {
    input(
      cls := "login-email-input",
      label(text <-- laminarHelper.plaintextStringSignal(labelId)),
      placeholder <-- laminarHelper.plaintextStringSignal(labelId),
      required := true,
      typ := "email",
      controlled(
        value <-- underlyingVar.signal,
        onInput.mapToValue --> underlyingVar.writer
      )
    )
  }


  private val onlineContinueBody: List[Element] = {

    val tokenVar = Var[String]("")
    val tokenInput = createTextInput(tokenVar, "login/insertTokenPlaceholder")

    def onTokenRequested(): Unit = {
      val email = emailVar.now()
      UserCommands.authMailCommand.sendCommandTo(fullInfo.defaults.backendExecutor, AuthMailRequest(email), Some(logger))
    }

    def onLoginRequested(): Unit = {
      val email = emailVar.now()
      val token = tokenVar.now()
      val singleToken = SingleAccessToken(token)
      fullInfo.usageControl.tryLoginWith(email, SingleAccessToken(token))
    }

    List(
      createEmailInput(emailVar, "login/enterMail"),
      HtmlButtonElement.withTextLabel("login/buttonRequestToken", _ => onTokenRequested()).getDomElement(),
      tokenInput,
      HtmlButtonElement.withTextLabel("login/buttonRequestLogin", _ => onLoginRequested()).getDomElement()
    )
  }


  private val infoPanel: Element = {
    div("Error go here!")
  }


  override def getDomElement(): Element = domElement
}
