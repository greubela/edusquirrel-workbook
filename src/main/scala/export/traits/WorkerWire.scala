package `export`.traits

import util.web.JsHelpers

import scala.scalajs.js

// helper functions that bind everything together (not type conversions, those are done in JsHelper). 
private object WorkerWire {
  
  def canvasBind(
                  name: String,
                  canvas: js.Any,
                  args: Map[String, String]
                ): js.Object =
    js.Dynamic.literal(
      kind = "bind-canvas",
      name = name,
      canvas = canvas,
      args = JsHelpers.stringMapHelper.fromScalaToJs(args)
    ).asInstanceOf[js.Object]
    
    
}

