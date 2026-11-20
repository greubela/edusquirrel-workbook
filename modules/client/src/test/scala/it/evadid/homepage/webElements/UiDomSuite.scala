package it.evadid.homepage.webElements

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.homepage.control.model.{AllWorkbookInfo, WorkbookConfig}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structureElements.WorkbookSection.WorkbookSectionMetadata
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.js

/** DOM queries and user actions for editor suites: elements are found by what a learner sees and driven by real events. */
trait UiDomSuite extends EditorDomSuite {
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals ++ List("HTMLElement", "HTMLCanvasElement",
    "HTMLImageElement", "HTMLAnchorElement", "SVGElement", "Text", "Event", "MouseEvent", "KeyboardEvent", "DragEvent",
    "DOMParser", "XMLSerializer", "localStorage", "getComputedStyle")

  /** Mounts `element`, runs `body` with its DOM node and always unmounts afterwards. */
  protected def mounted[T](element: Element)(body: dom.Element => T): T = {
    val unmount = mount(element)
    try body(element.ref) finally unmount()
  }

  /** Mounts `element` until `body`'s future completes. */
  protected def mountedAsync[T](element: Element)(body: dom.Element => Future[T]): Future[T] = {
    val unmount = mount(element)
    val result = scala.util.Try(body(element.ref)).fold(Future.failed, identity)
    result.transform { outcome => unmount(); outcome }(using scala.concurrent.ExecutionContext.global)
  }

  protected def all(root: dom.Element, selector: String): List[dom.Element] =
    root.querySelectorAll(selector).toList

  protected def one(root: dom.Element, selector: String): dom.Element =
    Option(root.querySelector(selector)).getOrElse(fail(s"no element matches $selector"))

  /** Elements matching `selector` whose visible text contains `text`. */
  protected def withText(root: dom.Element, selector: String, text: String): List[dom.Element] =
    all(root, selector).filter(_.textContent.contains(text))

  protected def buttonNamed(root: dom.Element, text: String): dom.html.Button =
    withText(root, "button", text).headOption.getOrElse(fail(s"no button shows '$text' in: ${root.textContent.take(400)}"))
      .asInstanceOf[dom.html.Button]

  /** Clicks like a user; SVG shapes have no click() and receive a dispatched click event. */
  protected def click(element: dom.Element): Unit =
    if js.typeOf(element.asInstanceOf[js.Dynamic].click) == "function" then element.asInstanceOf[dom.html.Element].click()
    else dispatch(element, "click")

  protected def dispatch(element: dom.Element, kind: String): Unit =
    element.dispatchEvent(js.Dynamic.newInstance(EditorDomSuite.window.Event)(kind, js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event])

  /** Types `value` into an input or text area, as a single input event. */
  protected def typeInto(element: dom.Element, value: String): Unit = {
    element.asInstanceOf[js.Dynamic].value = value
    dispatch(element, "input")
  }

  /** Chooses `value` in a select element. */
  protected def choose(element: dom.Element, value: String): Unit = {
    element.asInstanceOf[js.Dynamic].value = value
    dispatch(element, "change")
  }

  protected def valueOf(element: dom.Element): String = element.asInstanceOf[js.Dynamic].value.asInstanceOf[String]

  protected def isDisabled(element: dom.Element): Boolean = element.asInstanceOf[js.Dynamic].disabled.asInstanceOf[Boolean]

  /** Text of a language-map label that has not been loaded in the test page: its (normalized) id is shown. */
  protected def loadingText(id: String): String = s"${id.toLowerCase}]"

  /** Opens a one-section workbook holding `interactions` in the page's own state while `body` runs. */
  protected def inOpenWorkbook[T](interactions: WorkbookElement*)(body: => T): T = {
    val section = WorkbookSection("ui-section", WorkbookSectionMetadata(LanguageMapContentId("ui/section")), interactions)
    val workbook = Workbook("ui/workbook", WorkbookMetadata(Set.empty, Set.empty, LanguageMapContentId("ui/title"),
      Seq(AppLanguage.English)), Seq(section))
    val control = HtmlFullWorkbookApp.fullInfo.usageControl
    control.changeWorkbook(Some(AllWorkbookInfo(workbook, WorkbookConfig(Some(section)), Map.empty)))
    try body finally control.changeWorkbook(None)
  }

  /** Completes once `condition` holds, polling the event loop; fails after `attempts` tries. */
  protected def eventually(condition: => Boolean, attempts: Int = 200, what: String = "condition"): Future[Unit] = {
    val done = Promise[Unit]()
    def poll(left: Int): Unit =
      if scala.util.Try(condition).getOrElse(false) then done.trySuccess(())
      else if left <= 0 then done.tryFailure(new AssertionError(s"timed out waiting for $what"))
      else js.timers.setTimeout(5)(poll(left - 1))
    poll(attempts)
    done.future
  }
}
