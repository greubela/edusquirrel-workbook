package `export`.traits

import `export`.traits.WorkerTraits.WorkerCommand
import org.scalajs.dom
import org.scalajs.dom.OffscreenCanvas
import util.web.JsHelpers

import scala.scalajs.js

// helper functions that bind everything together (not type conversions, those are done in JsHelper).
private object WorkerWire {


  def init(paramsForInit: Map[String, String], canvas: Option[OffscreenCanvas]): js.Object =
    if (canvas.nonEmpty) {
      js.Dynamic.literal(
        kind = "init",
        params = JsHelpers.stringMapHelper.fromScalaToJs(paramsForInit),
        canvas = canvas.get
      ).asInstanceOf[js.Object]

    } else {
      js.Dynamic.literal(kind = "init", params = JsHelpers.stringMapHelper.fromScalaToJs(paramsForInit)).asInstanceOf[js.Object]

    }

  def request(id: String, command: WorkerCommand): js.Object =
    if (command.canvas.nonEmpty) {
      js.Dynamic.literal(
        kind = "request",
        id = id,
        name = command.name,
        params = JsHelpers.stringMapHelper.fromScalaToJs(command.params),
        canvas = command.canvas.get
      ).asInstanceOf[js.Object]
    } else {
      js.Dynamic.literal(
        kind = "request",
        id = id,
        name = command.name,
        params = JsHelpers.stringMapHelper.fromScalaToJs(command.params)
      ).asInstanceOf[js.Object]
    }

  def response(
                id: String,
                data: Map[String, String],
                timestampReceived: String,
                timestampStarted: String,
                timestampFinished: String
              ): js.Object =
    js.Dynamic.literal(
      kind = "response",
      id = id,
      ok = true,
      data = JsHelpers.stringMapHelper.fromScalaToJs(data),
      timestampReceived = timestampReceived,
      timestampStarted = timestampStarted,
      timestampFinished = timestampFinished
    ).asInstanceOf[js.Object]

  def error(
             id: String,
             message: String,
             timestampReceived: String,
             timestampStarted: String,
             timestampFinished: String
           ): js.Object =
    js.Dynamic.literal(
      kind = "response",
      id = id,
      ok = false,
      error = message,
      timestampReceived = timestampReceived,
      timestampStarted = timestampStarted,
      timestampFinished = timestampFinished
    ).asInstanceOf[js.Object]


}
