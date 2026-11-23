package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.laminar.api.L.Var
import org.scalajs.dom

import scala.scalajs.js

/** Starts Snap preview renders one per browser task, each once its canvas nears the viewport.
  *
  * A preview builds a temporary native world and blocks the page for its whole render; started together (Snap's
  * readiness resolves them as microtasks), a section of previews froze the page until all were done. Between two
  * renders the browser now paints and handles input, and off-screen previews wait until scrolled to.
  */
private[SnapEditor] object SnapPreviewScheduler:
  /** Previews start rendering this far outside the viewport, so they are usually ready when they scroll in. */
  private val margin = "300px"
  private val pending = Var(Vector.empty[() => Unit])
  private val running = Var(false)

  /** Queues `start` once `target` nears the viewport; returns the cancellation of that wait (call on unmount).
    * Without IntersectionObserver (e.g. jsdom) it starts at once. */
  def whenVisible(target: dom.Element)(start: () => Unit): () => Unit =
    if js.isUndefined(js.Dynamic.global.globalThis.IntersectionObserver) then
      start()
      () => ()
    else {
      val observer = new dom.IntersectionObserver((entries, observer) =>
        if entries.exists(_.isIntersecting) then {
          observer.disconnect()
          enqueue(start)
        }, new dom.IntersectionObserverInit { rootMargin = margin })
      observer.observe(target)
      () => observer.disconnect()
    }

  /** Queues a start; starts run in order, one per task. */
  private def enqueue(start: () => Unit): Unit =
    pending.update(_ :+ start)
    if !running.now() then {
      running.set(true)
      next()
    }

  private def next(): Unit =
    js.timers.setTimeout(0) {
      pending.now().headOption match {
        case None => running.set(false)
        case Some(start) =>
          pending.update(_.tail)
          try start()
          finally next()
      }
    }
