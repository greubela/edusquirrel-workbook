package it.evadid.core.datastructures.vectorShapes

import scala.scalajs.js

/** Deterministic canvas metrics for the shared layout tests in the Node test runner. */
private[vectorShapes] object FlowchartTestPlatform {
  private val globals = js.Dynamic.global.globalThis
  private var previousDocument: js.Dynamic = js.undefined.asInstanceOf[js.Dynamic]

  def setup(): Unit = {
    previousDocument = globals.selectDynamic("document")
    val context = js.Dynamic.literal(
      font = "",
      measureText = ((text: String) => js.Dynamic.literal(
        width = text.length * 7.0,
        actualBoundingBoxAscent = 10.0,
        actualBoundingBoxDescent = 2.0
      )): js.Function1[String, js.Dynamic]
    )
    val canvas = js.Dynamic.literal(getContext = ((_: String) => context): js.Function1[String, js.Dynamic])
    globals.updateDynamic("document")(js.Dynamic.literal(
      createElement = ((_: String) => canvas): js.Function1[String, js.Dynamic]
    ))
  }

  def cleanup(): Unit = globals.updateDynamic("document")(previousDocument)
}
