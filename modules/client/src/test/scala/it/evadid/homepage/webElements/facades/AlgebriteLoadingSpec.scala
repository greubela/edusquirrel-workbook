package it.evadid.homepage.webElements.facades

import it.evadid.facades.Algebrite
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class AlgebriteLoadingSpec extends EditorDomSuite {
  test("Algebrite shares a pending load, retries failure and returns its typed engine") {
    val root = js.Dynamic.global.globalThis
    val previous = root.selectDynamic("Algebrite")
    root.updateDynamic("Algebrite")(js.undefined)
    intercept[IllegalArgumentException](Algebrite.loaded)
    def dispatch(element: dom.Element, name: String): Unit = {
      val event = js.Dynamic.newInstance(dom.window.asInstanceOf[js.Dynamic].Event)(name).asInstanceOf[dom.Event]
      element.dispatchEvent(event)
    }
    val first = Algebrite.preload()
    assert(first eq Algebrite.preload())
    val failedScript = dom.document.querySelector("script[src*='algebrite']")
    assert(failedScript != null)
    assert(!first.isCompleted, "preloading returns without waiting for a script")
    dispatch(failedScript, "error")
    first.failed.flatMap { _ =>
      assert(!failedScript.isConnected)
      val retry = Algebrite.createInstance()
      val script = dom.document.querySelector("script[src*='algebrite']")
      assert(script != null && script != failedScript)
      root.updateDynamic("Algebrite")(js.Dynamic.literal(run = ((expression: String) => expression.reverse): js.Function1[String, String]))
      dispatch(script, "load")
      retry.map { engine =>
        assertEquals(engine.run("abc"), "cba")
        assert(engine eq Algebrite.loaded)
        assertEquals(dom.document.querySelectorAll("script[src*='algebrite']").length, 1)
      }
    }.andThen { case _ => root.updateDynamic("Algebrite")(previous) }
  }
}
