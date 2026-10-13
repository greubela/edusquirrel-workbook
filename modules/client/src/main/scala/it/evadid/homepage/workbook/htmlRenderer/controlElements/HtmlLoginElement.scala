package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.core.datastructures.user.{AllUserInfo, User}
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.AuthMailRequest
import it.evadid.homepage.control.change.AuthenticationFailure
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.util.logging.Logger
import org.scalajs.dom.HTMLInputElement

import scala.concurrent.{ExecutionContext, Future, Promise}

case class HtmlLoginElement() extends HtmlAppElement {

  private given ExecutionContext = ExecutionContext.global

  private val logger: Logger = fullInfo.loggerSystemInfo.uiAndDomLogger

  private val loginSucceededPromise: Promise[AllUserInfo] = Promise()

  private val savedUser = fullInfo.usageControl.tryParsingExistingUser().map(_.user)
  private val identity = new LoginIdentityState(
    savedUser.map(_.name).getOrElse(""), savedUser.map(_.mail).getOrElse(""))
  private val nameVar = identity.name
  private val emailVar = identity.email

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
    val action = new LoginActionState
    List[Element](
      ul(
        li(text <-- laminarHelper.plaintextStringSignal("login/onlineBenefit1")),
        li(text <-- laminarHelper.plaintextStringSignal("login/onlineBenefit2")),
        li(text <-- laminarHelper.plaintextStringSignal("login/onlineBenefit3")),
      ),

      createTextInput(nameVar, "login/insertNamePlaceholder", identity.changeName),
      createEmailInput(emailVar, "login/enterMail"),
      actionButton("login/buttonRequestRegistration", action) {
        action.run(fullInfo.usageControl.tryOnlineRegistration(nameVar.now(), emailVar.now()),
          "login/statusRegistered", "login/errorRegistration")
      },
      actionStatus(action)
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

  private def createTextInput(underlyingVar: Var[String], labelId: String, updateValue: String => Unit): Element = {
    input(
      cls := "login-name-input",
      label(text <-- laminarHelper.plaintextStringSignal(labelId)),
      placeholder <-- laminarHelper.plaintextStringSignal(labelId),
      typ := "text",
      controlled(
        value <-- underlyingVar.signal,
        onInput.mapToValue --> updateValue
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
        onInput.mapToValue --> identity.changeEmail
      )
    )
  }


  private val onlineContinueBody: List[Element] = {

    val tokenVar = Var[String]("")
    val tokenInput = createTextInput(tokenVar, "login/insertTokenPlaceholder", tokenVar.set)

    val action = new LoginActionState

    def validatedEmail: String = {
      val email = emailVar.now().trim.toLowerCase
      if (!User.isEmail(email)) throw AuthenticationFailure("login/errorEmail")
      email
    }

    List(
      createEmailInput(emailVar, "login/enterMail"),
      actionButton("login/buttonRequestToken", action) {
        action.run(
          UserCommands.authMailCommand.sendCommandTo(fullInfo.defaults.backendExecutor,
            AuthMailRequest(validatedEmail), Some(logger)).flatMap { response =>
            if (response.result.sent) Future.successful(())
            else Future.failed(AuthenticationFailure("login/errorTokenRequest"))
          }, "login/statusTokenSent", "login/errorTokenRequest")
      },
      tokenInput,
      actionButton("login/buttonRequestLogin", action) {
        action.run({
          val email = validatedEmail
          val token = tokenVar.now().trim
          if (token.isEmpty) Future.failed(AuthenticationFailure("login/errorTokenRequired"))
          else fullInfo.usageControl.tryLoginWith(email, SingleAccessToken(token))
        }, "login/statusLoggedIn", "login/errorLogin")
      },
      actionStatus(action)
    )
  }

  private def actionButton(labelId: String, state: LoginActionState)(action: => Future[Unit]): Element =
    button(
      typ := "button",
      text <-- laminarHelper.plaintextStringSignal(labelId),
      disabled <-- state.pending.signal,
      onClick --> { _ => action }
    )

  private def actionStatus(state: LoginActionState): Element =
    div(
      cls := "login-status",
      cls("login-status-error") <-- state.failed.signal,
      role := "status",
      child.text <-- state.messageKey.signal.flatMapSwitch { key =>
        if (key.isEmpty) Val("") else laminarHelper.plaintextStringSignal(key)
      }
    )


  override def getDomElement(): Element = domElement
}
