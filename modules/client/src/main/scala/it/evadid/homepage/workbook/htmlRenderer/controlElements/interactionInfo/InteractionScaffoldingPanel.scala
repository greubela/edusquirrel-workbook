package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult

import scala.concurrent.Future
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout}

object InteractionScaffoldingPanel {

  case class Labels(request: Signal[String], loading: Signal[String], dismiss: Signal[String],
                    error: Signal[String], timeout: Signal[String], notice: Signal[String])

  private enum Phase {
    case Idle, Failed, TimedOut
    case Pending(id: AnyRef)
    case Ready(text: String)
  }

  private case class View(mount: Option[AnyRef] = None, revision: Long = 0, phase: Phase = Phase.Idle,
                          timer: Option[SetTimeoutHandle] = None)
}

final class InteractionScaffoldingPanel[T, A](
                                               currentState: () => T,
                                               stateChanges: EventStream[T],
                                               language: StrictSignal[HumanLanguage],
                                               contextChanges: EventStream[Unit],
                                               generateFeedback: T => Future[ScaffoldingResult[T, A]],
                                               accept: ScaffoldingResult[T, A] => Unit,
                                               labels: InteractionScaffoldingPanel.Labels,
                                               timeoutMillis: Int = 60000
                                             ) extends HtmlAppElement {

  import InteractionScaffoldingPanel.*

  require(timeoutMillis > 0)
  private val view = Var(View())

  private def invalidate(mount: Option[AnyRef]): Unit = {
    view.now().timer.foreach(clearTimeout)
    view.update(previous => View(mount, previous.revision + 1))
  }

  private def request(): Unit = {
    val source = currentState()
    generateFeedback(source)
  }

  private lazy val domElement = div(
    cls := "interaction-scaffolding",
    onMountCallback(_ => invalidate(Some(new Object))),
    onUnmountCallback(_ => invalidate(None)),
    onMountBind(_ => stateChanges.toSignal(currentState()).distinct.changes --> (_ => invalidate(view.now().mount))),
    onMountBind(_ => language.distinct.changes --> (_ => invalidate(view.now().mount))),
    onMountBind(_ => contextChanges --> (_ => invalidate(view.now().mount))),
    div(cls := "interaction-scaffolding__actions",
      button(typ := "button", cls := "interaction-scaffolding__request",
        disabled <-- view.signal.map(_.phase.isInstanceOf[Phase.Pending]),
        child.text <-- labels.request, onClick --> (_ => request())),
      button(typ := "button", cls := "interaction-scaffolding__dismiss",
        hidden <-- view.signal.map(_.phase == Phase.Idle),
        child.text <-- labels.dismiss, onClick --> (_ => invalidate(view.now().mount)))
    ),
    p(cls := "interaction-scaffolding__notice", child.text <-- labels.notice),
    div(role := "status", aria.live := "polite", aria.atomic := true,
      aria.busy <-- view.signal.map(_.phase.isInstanceOf[Phase.Pending]),
      child.maybe <-- view.signal.map(_.phase).distinct.map {
        case Phase.Idle => None
        case Phase.Pending(_) => Some(p(cls := "interaction-scaffolding__message", child.text <-- labels.loading))
        case Phase.Ready(text) => Some(p(cls := "interaction-scaffolding__message", text))
        case Phase.Failed => Some(p(cls := "interaction-scaffolding__message interaction-scaffolding__message--error", child.text <-- labels.error))
        case Phase.TimedOut => Some(p(cls := "interaction-scaffolding__message interaction-scaffolding__message--error", child.text <-- labels.timeout))
      }
    )
  )

  override def getDomElement(): Element = domElement
}
