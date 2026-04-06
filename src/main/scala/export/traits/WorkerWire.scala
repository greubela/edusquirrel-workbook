package `export`.traits

import org.scalajs.dom
import util.web.JsHelpers

import scala.scalajs.js

// helper functions that bind everything together (not type conversions, those are done in JsHelper).
private object WorkerWire {

  def request(
               id: String,
               name: String,
               params: Map[String, String]
             ): js.Object =
    js.Dynamic.literal(
      kind = "request",
      id = id,
      name = name,
      params = JsHelpers.stringMapHelper.fromScalaToJs(params)
    ).asInstanceOf[js.Object]

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

  def init: js.Object =
    js.Dynamic.literal(kind = "init").asInstanceOf[js.Object]

  def canvasBind(
                  name: String,
                  canvas: dom.OffscreenCanvas,
                  args: Map[String, String]
                ): js.Object =
    js.Dynamic.literal(
      kind = "bind-canvas",
      name = name,
      canvas = canvas,
      args = JsHelpers.stringMapHelper.fromScalaToJs(args)
    ).asInstanceOf[js.Object]


}
