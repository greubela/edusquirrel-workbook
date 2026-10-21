package it.evadid.homepage.webElements.editor.code.codemirror

import com.raquo.airstream.state.Var
import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try

/** One asynchronous module load shared by startup warmup and all editors; never a startup dependency.
  * CodeMirror itself requires the DOM, so mounting still runs on the browser thread,
  * after yielding to the browser rather than inside a Laminar mount.
  */
object CodeMirrorEditorLoading {
  private val pending = Var(Option.empty[Future[CodeMirrorEditor.CodeMirrorFacade]])

  private def facade: Option[CodeMirrorEditor.CodeMirrorFacade] = {
    val value = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EduSquirrelCodeMirror")
    if (js.isUndefined(value) || value == null || js.typeOf(value.selectDynamic("createEditor")) != "function") None
    else Some(value.asInstanceOf[CodeMirrorEditor.CodeMirrorFacade])
  }

  private[code] def inBackground[A](work: => A): Future[A] = {
    val result = Promise[A]()
    setTimeout(0) { result.complete(Try(work)) }
    result.future
  }

  private[code] def bootstrapUrl: String = it.evadid.homepage.control.startup.BrowserAssets.bootstrapUrl

  private[code] def workerUrl: String = it.evadid.homepage.control.startup.ClientRuntimeConfig.workerUrl

  def preheat(): Unit = { load(background = true).failed.foreach(_ => ()); () }

  // Check the shared load after the calling reactive transaction has committed its Vars.
  private[code] def load(background: Boolean = false): Future[CodeMirrorEditor.CodeMirrorFacade] =
    Future.unit.flatMap(_ => loadOnce(background))

  private def loadOnce(background: Boolean): Future[CodeMirrorEditor.CodeMirrorFacade] = facade match {
    case Some(value) => Future.successful(value)
    case None => pending.now().getOrElse {
      val result = Promise[CodeMirrorEditor.CodeMirrorFacade]()
      pending.set(Some(result.future))
      inBackground {
        val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
        script.`type` = "module"
        script.async = true
        if background then script.setAttribute("fetchpriority", "low")
        script.src = new dom.URL("CodeMirrorLoader.js", bootstrapUrl).href
        val timeout = setTimeout(15000) {
          result.tryFailure(new RuntimeException("CodeMirror module load timed out"))
          script.remove()
        }
        script.onload = _ => {
          clearTimeout(timeout)
          facade match {
            case Some(value) => result.trySuccess(value)
            case None => result.tryFailure(new RuntimeException("CodeMirror module did not expose its facade"))
          }
          ()
        }
        script.addEventListener("error", (_: dom.Event) => {
          clearTimeout(timeout)
          result.tryFailure(new RuntimeException("CodeMirror module failed to load"))
          script.remove()
        })
        dom.document.head.appendChild(script)
      }.failed.foreach(result.tryFailure)
      result.future.failed.foreach { _ => if (pending.now().contains(result.future)) pending.set(None) }
      result.future
    }
  }
}
