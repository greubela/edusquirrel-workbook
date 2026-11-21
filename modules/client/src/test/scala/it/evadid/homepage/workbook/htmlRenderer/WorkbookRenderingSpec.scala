package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.airstream.core.AirstreamError
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.homepage.workbook.content.DigitalWorkbookCatalog
import it.evadid.homepage.workbook.content.test.CreateTestWorkbook
import scala.util.Try

/** Every section of every catalogue workbook mounts as the page shows it, opened in the page's own state:
  * every element mounts content, none falls back to a placeholder, and mounting raises no error.
  */
class WorkbookRenderingSpec extends EditorDomSuite {
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals ++ List("HTMLElement", "HTMLDivElement",
    "HTMLSpanElement", "HTMLCanvasElement", "HTMLImageElement", "HTMLAnchorElement", "SVGElement", "Text", "Event",
    "MouseEvent", "KeyboardEvent", "CustomEvent", "DOMParser", "XMLSerializer", "MutationObserver", "getComputedStyle",
    "localStorage", "sessionStorage")
  private val factories = DigitalWorkbookCatalog.entries.flatMap(entry => entry.native.map(native => entry.id -> native.factory)) :+
    ("design" -> CreateTestWorkbook.apply)
  private val placeholders = List("Cannot render Element", "cannot yet render")

  factories.foreach { (name, factory) =>
    test(s"$name: every section renders all of its elements") {
      val control = HtmlFullWorkbookApp.fullInfo.workbookNavigationControl
      control.changeWorkbook(factory(null))
      val workbook = HtmlFullWorkbookApp.fullInfo.current.workbookInfo.getOrElse(fail("the workbook did not open")).loadedWorkbook
      val current = Var("")
      val errors = Var(List.empty[String])
      def report(message: String): Unit = errors.update(_ :+ s"${current.now()}: ${message.take(300)}")
      val record: Throwable => Unit = error => report(String.valueOf(error))
      AirstreamError.registerUnhandledErrorCallback(record)
      try workbook.sections.foreach { section =>
        section.sectionContent.foreach { element =>
          current.set(s"${section.elementId}/${element.elementId}")
          Try(HtmlRenderFactory.render(element).getDomElement()).fold(error => report(String.valueOf(error)), { root =>
            val unmount = mount(div(root))
            val text = root.ref.textContent
            if root.ref.childNodes.length == 0 && root.ref.textContent.trim.isEmpty then report("renders no content")
            placeholders.find(text.contains).foreach(placeholder => report(text.substring(text.indexOf(placeholder))))
            unmount()
          })
        }
      } finally {
        AirstreamError.unregisterUnhandledErrorCallback(record)
        control.changeWorkbook(None)
      }
      assertEquals(errors.now(), Nil, s"${workbook.workbookId}: ${workbook.sections.size} sections")
    }
  }
}
