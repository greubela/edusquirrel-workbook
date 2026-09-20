package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.core.datastructures.user.User.SingleAccessToken
import it.evadid.distribution.commandTypes.UserCommands
import it.evadid.distribution.commandTypes.UserCommands.AuthMailRequest
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.syncDestination.LocalStorageSync
import it.evadid.util.logging.Logger
import it.evadid.workbook.interaction.sync.{SyncFormatter, SyncInformation, SyncStrategy}
import org.scalajs.dom.HTMLInputElement

import scala.concurrent.{Future, Promise}

case class HtmlLoginElement() extends HtmlAppElement {

  private val logger: Logger = fullInfo.loggerSystemInfo.uiAndDomLogger

  private val loginSucceededPromise: Promise[AllUserInfo] = Promise()

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
        titleElement("basic/titleLoginRegister", 1),
        div(
          cls := "container-login-body",
          div(
            cls := "login-area",
            titleElement("basic/titleLoginStartOnline", 2),
            onlineStartBody
          ),
          div(
            cls := "login-area",
            titleElement("basic/titleLoginStartLocal", 2),
            localStartBody
          ),
        )
      ),
      div(
        cls := "container-login-section section-continue",
        titleElement("basic/titleLoginContinue", 1),
        div(
          cls := "container-login-body",
          div(
            cls := "login-area",
            titleElement("basic/titleLoginContinueOnline", 2),
            onlineContinueBody
          ),
          div(
            cls := "login-area",
            titleElement("basic/titleLoginContinueLocal", 2),
            localContinueBody
          ),
        )

      )
    )
  }


  private val onlineStartBody: List[Element] = {

    val (nameInput, nameVar) = createTextInput("No Name", "basic/insertNamePlaceholder")
    val (emailInput, emailVar) = createEmailInput()

    def onRegistrationRequested(): Unit = {
      fullInfo.usageControl.tryRegistration(nameVar.now(), emailVar.now())
    }

    List(
      ul(
        li("✓ Your process is backed up online"),
        li("✓ Access to limited LLM functions"),
        li("✓ Share progress with a teacher")
      ),
      nameInput,
      emailInput,
      HtmlButtonElement.withTextLabel("basic/buttonRequestRegistration", _ => onRegistrationRequested()).getDomElement()
    )
  }

  // Start and Continue together (Insert Mail --> Insert Name or Token)

  private val localStartBody: List[Element] = {

    def onLocalLoginRequested(): Unit = {
      val onlyLocalSync = List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_LAST, SyncFormatter.serializeHistory))
      val aui = AllUserInfo.createNewUser("Anonymous User", "no-reply@evadid.it", onlyLocalSync)
      fullInfo.usageControl.changeUser(Some(aui))
    }

    List(
      ul(
        li("✓ No data leaves your device"),
        li("✗ No access to LLM feedback"),
        li("✗ Loose Progress not Downloaded"),
      ),
      HtmlButtonElement.withTextLabel("basic/buttonLocalLogin", _ => onLocalLoginRequested()).getDomElement()
    )
  }

  private val localContinueBody: List[Element] = {

    val uploadInput: ReactiveHtmlElement[HTMLInputElement] = laminarHelper.sessionFileUploadInput(logger, loadedFile => fullInfo.usageControl.tryContinueWithSessionFile(loadedFile))

    List(
      uploadInput,
      HtmlButtonElement.withTextLabel("basic/buttonLocalUpload", _ => uploadInput.ref.click()).getDomElement()
    )
  }

  private def createFileInput(): Element = {
    input(
      label(text <-- laminarHelper.plaintextStringSignal("basic/uploadFile")),
      typ := "file"
    )
  }

  private def createTextInput(defaultValue: String, labelId: String): (Element, Var[String]) = {
    val underlyingVar = Var[String](defaultValue)
    val element = input(
      cls := "login-name-input",
      label(text <-- laminarHelper.plaintextStringSignal(labelId)),
      placeholder <-- laminarHelper.plaintextStringSignal(labelId),
      typ := "text",
      controlled(
        value <-- underlyingVar.signal,
        onInput.mapToValue --> underlyingVar.writer
      )
    )
    val res = (element, underlyingVar)
    res
  }


  private def createEmailInput(): (Element, Var[String]) = {
    val underlyingVar = Var[String]("test@student.hu-berlin.de")
    val element = input(
      cls := "login-email-input",
      label(text <-- laminarHelper.plaintextStringSignal("basic/enterMail")),
      placeholder <-- laminarHelper.plaintextStringSignal("basic/enterMail"),
      required := true,
      typ := "email",
      controlled(
        value <-- underlyingVar.signal,
        onInput.mapToValue --> underlyingVar.writer
      )
    )
    (element, underlyingVar)
  }


  private val onlineContinueBody: List[Element] = {

    val (emailInput, emailVar) = createEmailInput()
    val (tokenInput, tokenVar) = createTextInput("", "basic/insertTokenPlaceholder")

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
      emailInput,
      HtmlButtonElement.withTextLabel("basic/buttonRequestToken", _ => onTokenRequested()).getDomElement(),
      tokenInput,
      HtmlButtonElement.withTextLabel("basic/buttonRequestLogin", _ => onLoginRequested()).getDomElement()
    )
  }


  private val infoPanel: Element = {
    div("Error go here!")
  }


  override def getDomElement(): Element = domElement
}
