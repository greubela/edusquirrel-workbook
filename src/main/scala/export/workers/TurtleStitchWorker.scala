package `export`.workers

import org.scalajs.dom
import util.web.JsHelpers.*
import util.web.WorkerRequestTracker

import scala.scalajs.js
import scala.scalajs.js.Promise as JsPromise

/**
 * Minimal client facade around turtle-worker.js.
 *
 * API design choices:
 * - Keep worker protocol private (`request`) and expose typed methods for supported operations only.
 * - Expose both capabilities needed by the feature:
 *   1) scripts snapshot (`calcProgramSvg`)
 *   2) stage preview after green-flag simulation (`simulateGreenFlag`)
 * - Keep `getGreenFlagPng` for compatibility with existing call sites.
 * - Keep worker as a classic script worker (no module mode), because turtle-worker.js loads classic
 *   Snap/TurtleStitch scripts via `importScripts`.
 */
case class TurtleStitchWorker(
    workerUrl: String = "../resources/programs/20260212TurtleStitch/turtle-worker.js"
) {

  private val worker = new dom.Worker(workerUrl)
  private val tracker = new WorkerRequestTracker

  worker.onmessage = { (event: dom.MessageEvent) =>
    tracker.complete(event.data.asInstanceOf[js.Any])
  }

  worker.onerror = { (event: dom.ErrorEvent) =>
    val errorMessage =
      s"${event.message} (${event.filename}:${event.lineno}:${event.colno})"

    tracker.failAll(
      obj(
        "ok" -> false,
        "error" -> obj("message" -> errorMessage)
      ).asInstanceOf[js.Dynamic]
    )
  }

  def init(): JsPromise[Unit] =
    requestUnit("init")

  def getGreenFlagPng(xml_content: String, language: String): JsPromise[String] =
    simulateGreenFlag(xml_content, language)

  def calcProgramSvg(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "calcProgramSvg",
      payload = obj(
        "xml_content" -> xml_content,
        "language" -> language.asInstanceOf[js.Any]
      )
    )

  def simulateGreenFlag(xml_content: String): JsPromise[String] =
    simulateGreenFlag(xml_content, "en")

  def simulateGreenFlag(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "simulateGreenFlag",
      payload = obj(
        "xml_content" -> xml_content,
        "language" -> language.asInstanceOf[js.Any]
      )
    )

  def destroy(): Unit = {
    requestUnit("destroy")
    worker.terminate()
    tracker.clear()
  }

  private def requestString(operation: String, payload: js.Object): JsPromise[String] =
    request(operation, payload).`then`[String] { (value: js.Any) =>
      value.asInstanceOf[String]
    }

  private def requestUnit(operation: String, payload: js.Object = emptyObj): JsPromise[Unit] =
    request(operation, payload).`then`[Unit] { (_: js.Any) =>
      ()
    }

  private def request(operation: String, payload: js.Object): JsPromise[js.Any] = {
    new JsPromise[js.Any]((resolve, reject) => {
      val id = tracker.register { (data: js.Dynamic) =>
        val ok = asBoolean(data.selectDynamic("ok").asInstanceOf[js.Any])
        if (ok) resolve(data.selectDynamic("result"))
        else {
          val error = asDynamic(data.selectDynamic("error").asInstanceOf[js.Any])
          val message =
            error.selectDynamic("message")
              .asInstanceOf[js.Any]
              .asInstanceOf[js.UndefOr[String]]
              .getOrElse(s"Worker operation failed: $operation")
          reject(js.JavaScriptException(message))
        }
      }

      worker.postMessage(
        obj(
          "id" -> id,
          "type" -> operation,
          "payload" -> payload
        )
      )
    })
  }
}
