package todomove.`export`.workers.server

import it.evadid.homepage.util.web.JsHelpers
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

/**
 * Helper for worker-side DOM/dynamic interop conversions.
 *
 * This class centralizes dynamic conversions and data-url encoding helpers so
 * worker/server orchestration code can stay focused on command handling.
 */
final class DomHelper {

  /** Resolve a script URL against worker location when possible. */
  def normalizeScriptUrl(src: String): String = {
    val workerHref =
      js.Dynamic.global.self
        .selectDynamic("location")
        .selectDynamic("href")
        .asInstanceOf[js.UndefOr[String]]
        .getOrElse("./")

    try {
      js.Dynamic.newInstance(js.Dynamic.global.URL)(src, workerHref).href.asInstanceOf[String]
    } catch {
      case _: Throwable => src
    }
  }

  /** Read a dynamic string field with a default fallback. */
  def dynamicString(target: js.Dynamic, field: String, default: String = ""): String =
    target.selectDynamic(field).asInstanceOf[js.UndefOr[String]].getOrElse(default)

  /** Read a dynamic integer field with a default fallback. */
  def dynamicInt(target: js.Dynamic, field: String, default: Int = 0): Int =
    target.selectDynamic(field).asInstanceOf[js.UndefOr[Int]].getOrElse(default)

  /** Check whether a dynamic member exists (used for JS function presence checks). */
  def hasFunction(target: js.Dynamic, field: String): Boolean =
    !js.isUndefined(target.selectDynamic(field))

  /** Convert an HTML/Offscreen canvas-like value into a PNG data URL. */
  def canvasToPngDataUrl(anyCanvas: js.Dynamic): Future[String] = {
    if (hasFunction(anyCanvas, "toDataURL")) {
      Future.successful(anyCanvas.toDataURL("image/png").asInstanceOf[String])
    } else {
      val convertPromise = anyCanvas.convertToBlob(js.Dynamic.literal(`type` = "image/png")).asInstanceOf[js.Promise[dom.Blob]]
      convertPromise.toFuture.flatMap(JsHelpers.blobToDataUrl)
    }
  }
}
