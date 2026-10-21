package it.evadid.homepage.control.startup

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object GraphRuntimeLoading {
  def available: Boolean = {
    val library = js.Dynamic.global.globalThis.selectDynamic("JXG")
    !js.isUndefined(library) && library != null
  }
  def load(): Future[Unit] = if available then Future.unit
    else BrowserScriptLoading.load(BrowserAssets.resource("programs/20261004JSXGraph/jsxgraphcore.js"))
  def preheat(): Unit = { load().failed.foreach(_ => ()); () }
}
