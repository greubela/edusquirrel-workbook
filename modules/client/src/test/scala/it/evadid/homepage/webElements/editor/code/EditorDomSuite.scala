package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import munit.FunSuite
import org.scalajs.dom
import scala.scalajs.js

/** Real mount ownership for editor tests, including unmount cleanup. */
trait EditorDomSuite extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  /** jsdom window properties installed as globals for the suite; a suite may add the ones its components use. */
  protected def jsdomGlobals: List[String] = List("window", "document", "Element", "Node", "HTMLInputElement", "HTMLTextAreaElement", "HTMLSelectElement", "HTMLButtonElement")
  private lazy val names = jsdomGlobals.distinct
  private val previous = Var(List.empty[(String, js.Dynamic)])
  private def window = EditorDomSuite.window
  override def beforeAll(): Unit = {
    previous.set(names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name)))
    names.foreach(name => globals.updateDynamic(name)(if name == "window" then window else window.selectDynamic(name)))
  }
  override def afterAll(): Unit = {
    previous.now().foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
  }
  /** Polls until `condition` holds, for views that compute in Futures or after a debounce. */
  protected def eventually(condition: => Boolean, clue: => String = "", attempts: Int = 300): scala.concurrent.Future[Unit] = {
    val promise = scala.concurrent.Promise[Unit]()
    def poll(remaining: Int): Unit =
      if scala.util.Try(condition).getOrElse(false) then promise.success(())
      else if remaining <= 0 then promise.failure(AssertionError(s"Condition not reached. $clue"))
      else scala.scalajs.js.timers.setTimeout(10)(poll(remaining - 1))
    poll(attempts)
    promise.future
  }

  protected def mount(element: Element): () => Unit = {
    val host = dom.document.createElement("div")
    dom.document.body.appendChild(host)
    val root = render(host, element)
    () => { root.unmount(); host.remove() }
  }
}

object EditorDomSuite {
  /** One jsdom window for the whole test run. Libraries cache DOM nodes from the first document they see
    * (Laminar keeps its HTML parser container), so a window per suite would leak nodes across windows.
    */
  lazy val window: js.Dynamic = {
    val req = js.Dynamic.global.selectDynamic("require")
    js.Dynamic.newInstance(req(js.Dynamic.global.process.cwd().asInstanceOf[String] + "/node_modules/jsdom").JSDOM)(
      "<html><body></body></html>", js.Dynamic.literal(url = "http://localhost/")).window
  }
}
