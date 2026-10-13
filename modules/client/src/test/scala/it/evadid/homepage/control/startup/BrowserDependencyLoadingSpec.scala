package it.evadid.homepage.control.startup

import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapRuntimeLoading
import munit.FunSuite
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

class BrowserDependencyLoadingSpec extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val requireFn = js.Dynamic.global.selectDynamic("require")
  private val root = js.Dynamic.global.process.cwd().asInstanceOf[String]
  private val jsdom = requireFn(root + "/node_modules/jsdom")
  private val window = js.Dynamic.newInstance(jsdom.JSDOM)(
    "<html><head><script src='https://example.test/project/homepage/js/app-loader.js'></script></head></html>").window
  private var previous = List.empty[(String, js.Dynamic)]
  private var idle = List.empty[js.Function1[js.Any, Unit]]

  override def beforeAll(): Unit = {
    previous = List("window", "document", "Event").map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name))
    globals.updateDynamic("window")(window)
    globals.updateDynamic("document")(window.document)
    globals.updateDynamic("Event")(window.Event)
    window.requestIdleCallback = ((callback: js.Function1[js.Any, Unit], _: js.Any) => {
      idle = idle :+ callback; idle.size
    }): js.Function2[js.Function1[js.Any, Unit], js.Any, Int]
  }
  override def afterAll(): Unit = {
    previous.foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
    window.close()
  }

  test("asset roots preserve project prefixes in source and assembled layouts") {
    assertEquals(BrowserDependencyLoading.siteUrl("resources/program.js"), "https://example.test/project/resources/program.js")
    val bootstrap = dom.document.querySelector("script").asInstanceOf[dom.html.Script]
    bootstrap.src = "https://example.test/project/js/app-loader.js"
    assertEquals(BrowserDependencyLoading.siteUrl("resources/program.js"), "https://example.test/project/resources/program.js")
    bootstrap.src = "https://example.test/project/homepage/js/app-loader.js"
  }

  test("simultaneous consumers share one low-priority script") {
    val url = "https://example.test/shared.js"
    val first = BrowserDependencyLoading.script(url)
    assert(first eq BrowserDependencyLoading.script(url))
    val script = dom.document.querySelector(s"script[src='$url']").asInstanceOf[dom.html.Script]
    assertEquals(script.getAttribute("fetchpriority"), "low")
    script.dispatchEvent(new dom.Event("load"))
    first
  }

  test("failed loads are removed and can be retried") {
    val url = "https://example.test/retry.js"
    val first = BrowserDependencyLoading.script(url)
    dom.document.querySelector(s"script[src='$url']").dispatchEvent(new dom.Event("error"))
    first.failed.flatMap { _ =>
      val retry = BrowserDependencyLoading.script(url)
      assert(!(first eq retry))
      assertEquals(dom.document.querySelectorAll(s"script[src='$url']").length, 1)
      dom.document.querySelector(s"script[src='$url']").dispatchEvent(new dom.Event("load"))
      retry
    }
  }

  test("Snap dependency loading yields between scripts, preserves order and shares readiness") {
    val ready = SnapRuntimeLoading.ready()
    assert(ready eq SnapRuntimeLoading.ready())
    val loaded = scala.collection.mutable.ListBuffer.empty[String]
    val done = Promise[Unit]()
    var attempts = 0
    def pump(): Unit = {
      attempts += 1
      idle.headOption.foreach { callback => idle = idle.tail; callback(js.undefined) }
      val scripts = dom.document.querySelectorAll("script[src*='20260212TurtleStitch/']")
      if (scripts.length > loaded.size) {
        assertEquals(scripts.length, loaded.size + 1)
        val script = scripts(scripts.length - 1).asInstanceOf[dom.html.Script]
        loaded += script.src
        script.dispatchEvent(new dom.Event("load"))
      }
      if (ready.isCompleted) done.tryComplete(ready.value.get)
      else if (attempts > 300) done.tryFailure(new RuntimeException("Snap did not finish ordered loading"))
      else setTimeout(0)(pump())
    }
    setTimeout(0)(pump())
    done.future.map { _ =>
      assertEquals(loaded.size, 23)
      assert(loaded.head.endsWith("/adjusted/adjustedMorphic.js"))
      assert(loaded.last.endsWith("/fullturtle.js"))
      assertEquals(loaded.distinct.size, loaded.size)
    }
  }
}
