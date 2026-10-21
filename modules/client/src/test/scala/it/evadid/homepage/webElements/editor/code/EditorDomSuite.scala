package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import munit.FunSuite
import org.scalajs.dom
import scala.scalajs.js

/** Real mount ownership for editor tests, including unmount cleanup. */
trait EditorDomSuite extends FunSuite {
  private val globals = js.Dynamic.global.globalThis
  private val names = List("window", "document", "Element", "Node")
  private val previous = Var(List.empty[(String, js.Dynamic)])
  private lazy val window = {
    val req = js.Dynamic.global.selectDynamic("require")
    js.Dynamic.newInstance(req(js.Dynamic.global.process.cwd().asInstanceOf[String] + "/node_modules/jsdom").JSDOM)(
      "<html><body></body></html>", js.Dynamic.literal(url = "http://localhost/")).window
  }
  override def beforeAll(): Unit = {
    previous.set(names.map(name => name -> js.Dynamic.global.Object.getOwnPropertyDescriptor(globals, name)))
    names.foreach(name => globals.updateDynamic(name)(if name == "window" then window else window.selectDynamic(name)))
  }
  override def afterAll(): Unit = {
    previous.now().foreach { (name, descriptor) =>
      if js.isUndefined(descriptor) then js.special.delete(globals, name)
      else js.Dynamic.global.Object.defineProperty(globals, name, descriptor)
    }
    window.close()
  }
  protected def mount(element: Element): () => Unit = {
    val host = dom.document.createElement("div")
    dom.document.body.appendChild(host)
    val root = render(host, element)
    () => { root.unmount(); host.remove() }
  }
}
