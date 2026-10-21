package it.evadid.facades

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/** Shared symbolic engine; preloading creates no expressions or application state. */
object Algebrite extends EvaExternalDependency[Algebrite.Api] {
  @js.native trait Api extends js.Object {
    def run(expression: String): String = js.native
  }

  @js.native @JSGlobal("Algebrite") private object Native extends Api

  private def available: Boolean = {
    val value = js.Dynamic.global.globalThis.selectDynamic("Algebrite")
    !js.isUndefined(value) && value != null
  }

  override def preload(): Future[Unit] = if available then Future.unit
    else BrowserLoading.load("https://unpkg.com/algebrite@1.4.0/dist/algebrite.bundle-for-browser.js")

  /** For synchronous calculations after preload/createInstance has completed. */
  def loaded: Api = {
    require(available, "Algebrite has not been preloaded")
    Native
  }

  override def createInstance(): Future[Api] = preload().map(_ => loaded)
}
