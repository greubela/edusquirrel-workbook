package it.evadid.homepage.webElements.editor.code

import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers.{clearTimeout, setTimeout}
import scala.util.Try

/** One asynchronous module load shared by all editors; never a homepage startup dependency.
  * CodeMirror itself requires the DOM, so mounting still runs on the browser thread,
  * in an idle task rather than inside a Laminar mount or a Promise microtask.
  */
object CodeMirrorEditorLoading {
  private var pending: Option[Future[CodeMirrorEditor.CodeMirrorFacade]] = None

  private def facade: Option[CodeMirrorEditor.CodeMirrorFacade] = {
    val value = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EduSquirrelCodeMirror")
    if (js.isUndefined(value) || value == null || js.typeOf(value.selectDynamic("createEditor")) != "function") None
    else Some(value.asInstanceOf[CodeMirrorEditor.CodeMirrorFacade])
  }

  private[code] def inBackground[A](work: => A): Future[A] = {
    val result = Promise[A]()
    val run: js.Function1[js.Any, Unit] = _ => { result.complete(Try(work)); () }
    val idle = dom.window.asInstanceOf[js.Dynamic].selectDynamic("requestIdleCallback")
    if (js.typeOf(idle) == "function")
      dom.window.asInstanceOf[js.Dynamic].requestIdleCallback(run, js.Dynamic.literal(timeout = 200))
    else setTimeout(0) { run(js.undefined) }
    result.future
  }

  private[code] def load(): Future[CodeMirrorEditor.CodeMirrorFacade] = facade match {
    case Some(value) => Future.successful(value)
    case None => pending.getOrElse {
      val result = Promise[CodeMirrorEditor.CodeMirrorFacade]()
      pending = Some(result.future)
      inBackground {
        val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
        script.`type` = "module"
        script.async = true
        // Resolve beside the app bootstrap, including deployments under a subpath.
        val scripts = dom.document.querySelectorAll("script[src]")
        val bootstrap = (0 until scripts.length).iterator
          .map(i => scripts(i).asInstanceOf[dom.html.Script].src)
          .find(src => src.takeWhile(_ != '?').endsWith("/app-loader.js"))
        script.src = new dom.URL("CodeMirrorLoader.js",
          bootstrap.getOrElse(new dom.URL("../js/app-loader.js", dom.document.baseURI).href)).href
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
      result.future.failed.foreach { _ => if (pending.contains(result.future)) pending = None }
      result.future
    }
  }
}
