package todomove.`export`.workers

import org.scalajs.dom
import it.evadid.homepage.util.web.{WorkerProtocolHelpers, WorkerRequestTracker}
import it.evadid.homepage.util.web.JsHelpers.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Promise as JsPromise
import scala.scalajs.js.JSConverters.*

/**
 * Minimal client facade around turtle-worker.js.
 *
 * API design choices:
 * - Keep worker protocol private (`request`) and expose typed methods for supported operations only.
 * - Expose both capabilities needed by the feature:
 *   1) scripts snapshot (`calcProgramPng`)
 *   2) stage preview after green-flag simulation (`simulateGreenFlag`)
 * - Keep `getGreenFlagPng` for compatibility with existing call sites.
 * - Keep worker as a classic script worker (no module mode), because turtle-worker.js loads classic
 *   Snap/TurtleStitch scripts via `importScripts`.
 */
case class TurtleStitchWorker(
    workerUrl: String = "../../resources/programs/20260212TurtleStitch/turtle-worker.js" // sth wrong :(
) {

  private val worker = new dom.Worker(workerUrl)
  private val tracker = new WorkerRequestTracker

  WorkerProtocolHelpers.wireMessages(worker, tracker)

  worker.onerror = { (event: dom.ErrorEvent) =>
    WorkerProtocolHelpers.failAllFromWorkerError(
      tracker,
      event
    )
  }

  def init(): JsPromise[Unit] =
    requestUnit("init")

  def getGreenFlagPng(xml_content: String, language: String): JsPromise[String] =
    simulateGreenFlag(xml_content, language)

  /** Green-flag program-code snapshot (scripts/blocks rendering) as PNG data URL, not executed stage output. */
  def calcProgramPng(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "calcProgramPng",
      payload = obj(
        "xml_content" -> xml_content,
        "language" -> language.asInstanceOf[js.Any]
      )
    )

  /** Backwards-compatible alias; returns PNG data URL. */
  def calcProgramSvg(xml_content: String, language: String): JsPromise[String] =
    calcProgramPng(xml_content, language)

  def simulateGreenFlag(xml_content: String): JsPromise[String] =
    simulateGreenFlag(xml_content, "en")

  /** Executed-stage snapshot after reset + single green-flag run. */
  def simulateGreenFlag(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "simulateGreenFlag",
      payload = obj(
        "xml_content" -> xml_content,
        "language" -> language.asInstanceOf[js.Any]
      )
    )

  def getGreenFlagAsLispCode(xml_content: String): JsPromise[String] =
    getGreenFlagAsLispCode(xml_content, "en")

  def getGreenFlagAsLispCode(xml_content: String, language: String): JsPromise[String] =
    requestString(
      operation = "getGreenFlagAsLispCode",
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

  private def request(operation: String, payload: js.Object): JsPromise[js.Any] =
    WorkerProtocolHelpers
      .request(
        worker = worker,
        tracker = tracker,
        operation = operation,
        payload = payload,
        operationFieldName = "type",
        responsePayloadFieldName = "result",
        onError = value => {
          val error = asDynamic(value)
          val message =
            error.selectDynamic("message")
              .asInstanceOf[js.Any]
              .asInstanceOf[js.UndefOr[String]]
              .getOrElse(s"Worker operation failed: $operation")
          js.JavaScriptException(message)
        }
      )
      .toJSPromise
}
