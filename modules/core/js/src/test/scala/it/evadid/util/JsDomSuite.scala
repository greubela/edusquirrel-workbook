package it.evadid.util

import com.raquo.airstream.state.Var
import munit.FunSuite
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** One jsdom window per test process; suites install the globals they need and restore the previous ones. */
private[evadid] object JsDom {
  private val require = js.Dynamic.global.selectDynamic("require")
  val root: String = js.Dynamic.global.process.cwd().asInstanceOf[String]

  lazy val window: js.Dynamic = {
    val created = js.Dynamic.newInstance(require(root + "/node_modules/jsdom").JSDOM)(
      "<html><head></head><body></body></html>",
      js.Dynamic.literal(url = "http://localhost/workbook/pages/index.html", pretendToBeVisual = true)).window
    // jsdom has no layout; text ranges report no boxes, as for content outside the viewport.
    created.Range.prototype.getClientRects = (() => js.Array()): js.Function0[js.Array[js.Any]]
    created.Range.prototype.getBoundingClientRect = (() => js.Dynamic.newInstance(created.DOMRect)()): js.Function0[js.Any]
    created
  }

  val browserGlobals: List[String] = List("window", "document", "navigator", "Window", "Element", "HTMLElement",
    "Node", "Text", "Event", "MouseEvent", "KeyboardEvent", "MutationObserver", "requestAnimationFrame",
    "cancelAnimationFrame", "getComputedStyle", "getSelection", "DOMRect", "Blob", "File", "FileReader")
}

/** Installs jsdom browser globals for the whole suite. */
private[evadid] trait JsDomSuite extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val previous = Var(List.empty[(String, js.Any)])
  protected def domGlobals: List[String] = JsDom.browserGlobals
  protected def window: js.Dynamic = JsDom.window

  override def beforeAll(): Unit = {
    previous.set(domGlobals.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name)))
    domGlobals.foreach { name =>
      val value = if name == "window" then window else window.selectDynamic(name)
      js.Dynamic.global.Object.defineProperty(globals, name,
        js.Dynamic.literal(value = value, writable = true, configurable = true))
    }
  }

  override def afterAll(): Unit = previous.now().foreach { (name, descriptor) =>
    if js.isUndefined(descriptor) then js.special.delete(globals, name)
    else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
  }

  /** Completes after the given delay, once pending timers and promise callbacks have run. */
  protected def tick(milliseconds: Int = 0): Future[Unit] = {
    val done = Promise[Unit]()
    js.timers.setTimeout(milliseconds)(done.success(()))
    done.future
  }

  protected def waitUntil(condition: => Boolean, attempts: Int = 200): Future[Unit] =
    if condition then Future.unit
    else if attempts == 0 then Future.failed(AssertionError("condition was not reached"))
    else tick(5).flatMap(_ => waitUntil(condition, attempts - 1))

  /** Restores the named globals after a test body (possibly asynchronous), deleting ones that did not exist. */
  protected def restoringGlobals[A](names: String*)(body: => A): A = {
    val previous = names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name))
    def restore(): Unit = previous.foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
    val result = try body catch { case error: Throwable => restore(); throw error }
    result match {
      // The returned future completes after the restoration, so the next test starts with the previous globals.
      case future: Future[?] => future.andThen(_ => restore()).asInstanceOf[A]
      case _ => restore(); result
    }
  }

  /** Replaces one global for a test body (possibly asynchronous) and restores it afterwards. */
  protected def withGlobal[A](name: String, value: js.Any)(body: => A): A = restoringGlobals(name) {
    js.Dynamic.global.Object.defineProperty(globals, name, js.Dynamic.literal(value = value, writable = true, configurable = true))
    body
  }
}
