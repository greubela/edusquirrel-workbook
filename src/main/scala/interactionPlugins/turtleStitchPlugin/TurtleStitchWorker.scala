package interactionPlugins.turtleStitchPlugin

import org.scalajs.dom

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

  private var nextId = 1
  private val pending = js.Dictionary[js.Function1[js.Dynamic, Unit]]()

  worker.onmessage = { (event: dom.MessageEvent) =>
    val data = event.data.asInstanceOf[js.Dynamic]
    val id = data.selectDynamic("id").asInstanceOf[Int].toString

    pending.get(id).foreach { callback =>
      pending -= id
      callback(data)
    }
  }

  worker.onerror = { (event: dom.ErrorEvent) =>
    val errorMessage =
      s"${event.message} (${event.filename}:${event.lineno}:${event.colno})"

    pending.keys.foreach { id =>
      val handler = pending(id)
      pending -= id
      handler(
        js.Dynamic.literal(
          ok = false,
          error = js.Dynamic.literal(message = errorMessage)
        )
      )
    }
  }

  def init(): JsPromise[Unit] =
    requestUnit("init")

  def getGreenFlagPng(xml_content: String, language: String): JsPromise[String] =
    simulateGreenFlag(xml_content, language)

  def calcProgramSvg(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "calcProgramSvg",
      payload = js.Dynamic.literal(
        xml_content = xml_content,
        language = language.asInstanceOf[js.Any]
      )
    )

  def simulateGreenFlag(xml_content: String): JsPromise[String] =
    simulateGreenFlag(xml_content, "en")

  def simulateGreenFlag(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "simulateGreenFlag",
      payload = js.Dynamic.literal(
        xml_content = xml_content,
        language = language.asInstanceOf[js.Any]
      )
    )

  def destroy(): Unit = {
    requestUnit("destroy")
    worker.terminate()
    pending.clear()
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
    val id = nextId
    nextId += 1

    new JsPromise[js.Any]((resolve, reject) => {
      pending(id.toString) = { (data: js.Dynamic) =>
        val ok = data.selectDynamic("ok").asInstanceOf[Boolean]
        if (ok) resolve(data.selectDynamic("result"))
        else {
          val message =
            data.selectDynamic("error")
              .selectDynamic("message")
              .asInstanceOf[js.UndefOr[String]]
              .getOrElse(s"Worker operation failed: $operation")
          reject(js.JavaScriptException(message))
        }
      }

      worker.postMessage(
        js.Dynamic.literal(
          id = id,
          `type` = operation,
          payload = payload
        )
      )
    })
  }

  private val emptyObj: js.Object =
    (new js.Object).asInstanceOf[js.Object]
}
