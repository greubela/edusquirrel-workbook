package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import org.scalajs.dom

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.Try

/** Redraws a costly view only after its source has rested for `delay`, so typing stays fluent.
  *
  * Every change waits `delay` knowing its state; if a newer state arrived meanwhile, that state
  * requested its own drawing and the older one does nothing. `prepare` then runs in a Future after
  * the status line has been painted. Results of superseded states are discarded. Views mounted on
  * one instance share its prepared results; each keeps showing its previous drawing meanwhile.
  */
final class DeferredRendering[S, R](source: Signal[S], prepare: S => R, delay: FiniteDuration = DeferredRendering.defaultDelay)(
    using ExecutionContext) {
  import DeferredRendering.*

  private val settled: Signal[S] = source.composeChanges(_.debounce(delay.toMillis.toInt))

  private val prepared: Signal[Option[Prepared[S, R]]] =
    settled.flatMapSwitch(state => EventStream.fromFuture(afterPaint().map(_ => Prepared(state, Try(prepare(state))))))
      .toWeakSignal

  /** Only a new result updates the view; an equal result of a newer state keeps the existing drawing. */
  val result: Signal[Option[Try[R]]] = prepared.map(_.map(_.result)).distinct

  val phase: Signal[Phase] = source.combineWith(settled, prepared).map { (current, started, done) =>
    if done.exists(_.state == current) then Phase.Ready
    else if started == current then Phase.Rendering
    else Phase.Waiting
  }

  /** The status line shown while the visible drawing is outdated or being redrawn. */
  def status(text: LanguageMapContentId => Signal[String]): Element =
    p(cls := "deferred-rendering__status", role := "status", aria.live := "polite",
      cls("deferred-rendering__status--hidden") <-- phase.map(_ == Phase.Ready),
      child.text <-- LabelSelection(phase, Phase.values.flatMap(statusText), text)(statusText))

  /** Status line followed by the drawing. `view` is built once, when the first result is ready, and then follows
    * the results, so a view can keep its board instead of being rebuilt.
    */
  def element(text: LanguageMapContentId => Signal[String])(view: Signal[Try[R]] => Element): Element =
    div(cls := "deferred-rendering",
      cls("deferred-rendering--outdated") <-- phase.map(_ != Phase.Ready),
      status(text),
      child.maybe <-- result.map(_.isDefined).distinct.map(ready => Option.when(ready)(view(result.map(_.get)))))
}

object DeferredRendering {
  val defaultDelay: FiniteDuration = 1.second
  val waitingText: LanguageMapContentId = LanguageMapContentId("basic/renderingWaiting")
  val renderingText: LanguageMapContentId = LanguageMapContentId("basic/renderingInProgress")

  enum Phase {
    /** The source changed; the drawing is redrawn once the source has rested. */
    case Waiting
    /** The current state is being prepared and drawn. */
    case Rendering
    /** The drawing shows the current state. */
    case Ready
  }

  private final case class Prepared[S, R](state: S, result: Try[R])

  def statusText(phase: Phase): Option[LanguageMapContentId] = phase match {
    case Phase.Waiting => Some(waitingText)
    case Phase.Rendering => Some(renderingText)
    case Phase.Ready => None
  }

  /** Completes after the next paint, so a status set before is visible during the preparation. */
  private def afterPaint(): Future[Unit] = {
    val painted = Promise[Unit]()
    def next(): Unit = js.timers.setTimeout(0)(painted.trySuccess(()))
    if js.typeOf(js.Dynamic.global.requestAnimationFrame) == "function" then dom.window.requestAnimationFrame(_ => next())
    else next()
    painted.future
  }
}
