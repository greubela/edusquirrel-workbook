package interactionPlugins.programmingExercise.pythonExercisesUnsorted

import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/**
  * Utility that loads the CodeMirror 5 assets from a CDN only once and exposes the global factory.
  * This keeps the Python editor implementation self-contained within the plugin package.
  */
private[pythonExercises] object CodeMirrorLoader {

  private val BaseCssHref = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.css"
  private val ThemeCssHref = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/theme/eclipse.min.css"
  private val BaseJsSrc = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.js"
  private val PythonModeSrc = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/mode/python/python.min.js"

  private var loadFuture: Option[Future[CodeMirrorFactory]] = None

  def ensureLoaded(): Future[CodeMirrorFactory] = loadFuture.getOrElse {
    val promise = Promise[CodeMirrorFactory]()

    existingFactory() match {
      case Some(factory) =>
        promise.success(factory)
      case None =>
        appendStyles()
        loadScript(BaseJsSrc).flatMap(_ => loadScript(PythonModeSrc)).foreach { _ =>
          existingFactory() match {
            case Some(factory) => promise.success(factory)
            case None           => promise.failure(new IllegalStateException("CodeMirror failed to load from CDN."))
          }
        }(queue)
      }

    val future = promise.future
    loadFuture = Some(future)
    future
  }

  private def existingFactory(): Option[CodeMirrorFactory] =
    val codeMirror = js.Dynamic.global.selectDynamic("CodeMirror")
    if js.typeOf(codeMirror) == "function" then
      Some(codeMirror.asInstanceOf[CodeMirrorFactory])
    else None

  private def appendStyles(): Unit =
    ensureStylesheet(BaseCssHref)
    ensureStylesheet(ThemeCssHref)

  private def ensureStylesheet(href: String): Unit =
    val links = dom.document.getElementsByTagName("link")
    val alreadyPresent = (0 until links.length).exists { idx =>
      links(idx) match {
        case link: dom.html.Link => link.rel == "stylesheet" && link.href == href
        case _                   => false
      }
    }
    if !alreadyPresent then
      val link = dom.document.createElement("link").asInstanceOf[dom.html.Link]
      link.rel = "stylesheet"
      link.href = href
      dom.document.head.appendChild(link)

  private def loadScript(src: String): Future[Unit] = {
    val promise = Promise[Unit]()
    val script = dom.document.createElement("script").asInstanceOf[dom.html.Script]
    script.src = src
    script.`type` = "text/javascript"
    script.async = false
    script.addEventListener("load", _ => promise.success(()))
    script.addEventListener(
      "error",
      _ => promise.failure(new IllegalStateException(s"Failed to load CodeMirror asset: $src"))
    )
    dom.document.head.appendChild(script)
    promise.future
  }
}

@js.native
private[pythonExercises] trait CodeMirrorFactory extends js.Object {
  def fromTextArea(textArea: dom.html.TextArea, options: js.Any): CodeMirrorInstance = js.native
}

@js.native
private[pythonExercises] trait CodeMirrorInstance extends js.Object {
  def getValue(): String = js.native
  def setValue(value: String): Unit = js.native
  def on(eventName: String, handler: js.Function2[CodeMirrorInstance, js.Any, Any]): Unit = js.native
  def setOption(option: String, value: js.Any): Unit = js.native
  def toTextArea(): Unit = js.native
}
