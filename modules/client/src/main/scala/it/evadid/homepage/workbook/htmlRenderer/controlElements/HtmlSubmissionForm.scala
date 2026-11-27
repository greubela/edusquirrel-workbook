package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.user.{AllUserInfo, User}
import it.evadid.distribution.commandTypes.SubmissionCommands.{SubmitSessionEvent, SubmitSessionResponse}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.util.logging.Logger
import it.evadid.workbook.session.SessionData

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Hands in the open workbook under an entered name and address, styled like the login page; the surrounding
  * container supplies the title. Online accounts
  * prefill both fields; local accounts start blank. The submitter is a new user that is never registered or
  * stored, and the bound user, its histories and its sync bindings stay untouched.
  *
  * @param sessionFor the open workbook's current session, handed in by the given submitter
  * @param submit     sends the event to the backend
  */
final class HtmlSubmissionForm(sessionFor: User => Option[SessionData], currentUser: Signal[Option[AllUserInfo]],
                               submit: SubmitSessionEvent => Future[SubmitSessionResponse], logger: Logger) extends HtmlAppElement {
  import HtmlSubmissionForm.*

  private given ExecutionContext = ExecutionContext.global

  private val name = Var("")
  private val mail = Var("")
  private val status = Var[Status](Status.Idle)

  /** The validated session to send, or the message key explaining why it cannot be sent. */
  private def submission(): Either[String, SessionData] = {
    val enteredName = name.now().trim
    val enteredMail = mail.now().trim
    if enteredName.isEmpty then Left("login/submissionErrorName")
    else if !User.isEmail(enteredMail) then Left("login/errorEmail")
    else sessionFor(User.createNewUser(enteredName, enteredMail)).toRight("login/submissionErrorNoWorkbook")
  }

  private def send(): Unit = if status.now() != Status.Pending then submission() match {
    case Left(key) => status.set(Status.Failed(key))
    case Right(session) =>
      status.set(Status.Pending)
      Try(submit(SubmitSessionEvent(session))).fold(Future.failed, identity).onComplete {
        case Success(response) => status.set(Status.Submitted(response.confirmationSent))
        case Failure(error) =>
          logger.logExceptionWarn("The workbook submission failed", error)
          status.set(Status.Failed("login/submissionError"))
      }
  }

  private def label(id: String): Signal[String] = laminarHelper.plaintextStringSignal(id)

  private def field(variable: Var[String], labelId: String, inputType: String, cssClass: String): Element =
    input(cls := cssClass, typ := inputType, placeholder <-- label(labelId), aria.label <-- label(labelId),
      controlled(value <-- variable.signal, onInput.mapToValue --> variable.writer))

  private lazy val domElement: Element = div(
    cls := "container-login container-submission",
    currentUser.map(prefill).distinct --> { (prefilledName, prefilledMail) =>
      name.set(prefilledName)
      mail.set(prefilledMail)
    },
    div(
      cls := "container-login-section",
      div(
        cls := "login-area submission-area",
        p(text <-- label("login/submissionExplanation")),
        field(name, "login/insertNamePlaceholder", "text", "login-name-input"),
        field(mail, "login/enterMail", "email", "login-email-input"),
        button(typ := "button", text <-- label("login/buttonSubmit"),
          disabled <-- status.signal.map(_ == Status.Pending), onClick --> (_ => send())),
        div(
          cls := "login-status", role := "status",
          cls("login-status-error") <-- status.signal.map(_.isInstanceOf[Status.Failed]),
          child.text <-- status.signal.flatMapSwitch(_.messageKey.fold(Val(""))(label))
        )
      )
    )
  )

  override def getDomElement(): Element = domElement
}

object HtmlSubmissionForm {
  private[controlElements] enum Status {
    case Idle, Pending
    case Submitted(confirmationSent: Boolean)
    case Failed(key: String)

    def messageKey: Option[String] = this match {
      case Idle => None
      case Pending => Some("login/statusPending")
      case Submitted(true) => Some("login/submissionSent")
      case Submitted(false) => Some("login/submissionSentWithoutConfirmation")
      case Failed(key) => Some(key)
    }
  }

  /** Online accounts prefill their own name and address; local and missing users leave both blank. */
  private def prefill(user: Option[AllUserInfo]): (String, String) =
    user.filter(_.config.isOnlineAccount).fold(("", ""))(info => (info.user.name, info.user.mail))
}
