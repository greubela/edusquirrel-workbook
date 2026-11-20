package it.evadid.facades.internal

import com.raquo.airstream.state.Var
import it.evadid.facades.CodeMirror.{CodeMirrorHandle, EditorConfig, Flowchart, FlowchartRow, FlowchartSlice}
import it.evadid.facades.CodeMirrorSuite
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CodeMirrorRuntimeSpec extends CodeMirrorSuite {
  private def editor(parent: dom.Element = host(), doc: String = "", language: String = "python",
      changes: js.Array[String] = js.Array(), firstLine: Int = 1): CodeMirrorHandle =
    CodeMirrorRuntime.createEditor(EditorConfig(parent, doc, value => changes.push(value), language, firstLineNumber = firstLine))

  private def viewIn(libs: js.Dynamic, parent: dom.Element): js.Dynamic =
    libs.EditorView.findFromDOM(parent.querySelector(".cm-editor"))

  private def edit(view: js.Dynamic, from: Int, to: Int, insert: String): Unit =
    view.dispatch(js.Dynamic.literal(changes = js.Dynamic.literal(from = from, to = to, insert = insert), userEvent = "input.type"))

  private def press(libs: js.Dynamic, view: js.Dynamic, key: String, shift: Boolean = false): Boolean =
    libs.runScopeHandlers(view, js.Dynamic.newInstance(window.KeyboardEvent)("keydown",
      js.Dynamic.literal(key = key, shiftKey = shift)), "editor").asInstanceOf[Boolean]

  private def select(view: js.Dynamic, anchor: Int, head: Int): Unit =
    view.dispatch(js.Dynamic.literal(selection = js.Dynamic.literal(anchor = anchor, head = head)))

  private def lineNumbers(parent: dom.Element): List[String] =
    parent.querySelectorAll(".cm-lineNumbers .cm-gutterElement").toList.drop(1).map(_.textContent)

  private def topNode(libs: js.Dynamic, view: js.Dynamic): String = libs.syntaxTree(view.state).topNode.name.asInstanceOf[String]

  /** Captures console warnings for one test. */
  private def capturingWarnings[A](body: js.Array[String] => Future[A]): Future[A] = {
    val console = js.Dynamic.global.console
    val original = console.warn
    val warnings = js.Array[String]()
    console.warn = ((message: String, _: js.Any) => { warnings.push(message); () }): js.Function2[String, js.Any, Unit]
    val result = try body(warnings) catch { case error: Throwable => console.warn = original; throw error }
    result.andThen(_ => console.warn = original)
  }

  test("Python editors normalize tabs, report user edits and keep programmatic documents unreported") {
    withLibraries { libs =>
      val parent = host()
      val changes = js.Array[String]()
      val handle = editor(parent, "if x:\n\tpass", "Python", changes)
      val view = viewIn(libs, parent)
      assertEquals(handle.getDoc(), "if x:\n    pass")
      assertEquals(topNode(libs, view), "Script")
      assert(parent.querySelector(".cm-editor").classList.contains("edusquirrel-code-mirror"))
      edit(view, 0, 0, "#")
      assertEquals(changes.toList, List("#if x:\n    pass"))
      handle.setDoc("a\tb")
      assertEquals(handle.getDoc(), "a    b")
      val unchanged = view.state
      handle.setDoc("a\tb")
      assert(view.state eq unchanged, "an identical document is not dispatched")
      handle.setDoc(null)
      assertEquals(handle.getDoc(), "")
      assertEquals(changes.length, 1)
      handle.focus()
      assert(view.hasFocus.asInstanceOf[Boolean])
      handle.destroy()
      handle.destroy()
      handle.setFirstLineNumber(4)
      assertEquals(parent.querySelector(".cm-editor"), null)
      Future.unit
    }
  }

  test("Tab inserts four spaces at the cursor, indents selections and Shift-Tab outdents") {
    withLibraries { libs =>
      val parent = host()
      val handle = editor(parent, "a\nb")
      val view = viewIn(libs, parent)
      select(view, 1, 1)
      assert(press(libs, view, "Tab"))
      assertEquals(handle.getDoc(), "a    \nb")
      assertEquals(view.state.selection.main.head.asInstanceOf[Int], 5)
      select(view, 0, 7)
      assert(press(libs, view, "Tab"))
      assertEquals(handle.getDoc(), "    a    \n    b")
      assert(press(libs, view, "Tab", shift = true))
      assertEquals(handle.getDoc(), "a    \nb")
      handle.destroy()
      Future.unit
    }
  }

  test("line numbers start at the configured line and later changes are not undoable") {
    withLibraries { libs =>
      val parent = host()
      val handle = editor(parent, "a\nb\nc", firstLine = 5)
      val view = viewIn(libs, parent)
      tick().flatMap { _ =>
        assertEquals(lineNumbers(parent), List("5", "6", "7"))
        handle.setFirstLineNumber(10)
        tick()
      }.flatMap { _ =>
        assertEquals(lineNumbers(parent), List("10", "11", "12"))
        assert(!libs.undo(view).asInstanceOf[Boolean], "line numbering is not an undoable edit")
        handle.setFirstLineNumber(0)
        tick()
      }.map { _ =>
        assertEquals(lineNumbers(parent), List("1", "2", "3"))
        handle.destroy()
      }
    }
  }

  test("diagnostics decorate the document until replaced, cleared or a new document is set") {
    withLibraries { libs =>
      val parent = host()
      val handle = editor(parent, "x = y\nprint(x)")
      def count(selector: String) = parent.querySelectorAll(selector).length
      val diagnostic = js.Array[js.Object](js.Dynamic.literal(line = 1, fromCh = 4, toCh = 5, severity = "error", message = "y?"))
      handle.setDiagnostics(diagnostic)
      assertEquals(count(".cm-edusquirrel-diagnostic-error"), 1)
      assertEquals(parent.querySelector(".cm-edusquirrel-diagnostic-mark").textContent, "y")
      assertEquals(parent.querySelector(".cm-edusquirrel-diagnostic-mark").getAttribute("title"), "y?")
      handle.setDiagnostics(null)
      assertEquals(count(".cm-edusquirrel-diagnostic"), 0)
      handle.setDiagnostics(diagnostic)
      handle.setDiagnostics(js.undefined.asInstanceOf[js.Array[js.Object]])
      assertEquals(count(".cm-edusquirrel-diagnostic"), 0)
      handle.setDiagnostics(diagnostic)
      handle.setDoc("z = 1")
      assertEquals(count(".cm-edusquirrel-diagnostic"), 0, "a replaced document drops stale diagnostics")
      handle.destroy()
      Future.unit
    }
  }

  test("editor themes follow their container or the page and update when the theme changes") {
    withLibraries { libs =>
      def dark(view: js.Dynamic) = view.state.facet(libs.EditorView.darkTheme).asInstanceOf[Boolean]
      val container = host()
      container.setAttribute("data-editor-theme", "light")
      val themed = dom.document.createElement("div")
      container.appendChild(themed)
      val page = host()
      page.setAttribute("class", "fd-page")
      val paged = dom.document.createElement("div")
      page.appendChild(paged)
      val root = dom.document.documentElement
      root.setAttribute("data-theme", "light")
      val plain = host()
      val handles = List(editor(themed, "# note"), editor(paged), editor(plain, "# note"))
      val List(themedView, pagedView, plainView) = List(themed, paged, plain).map(viewIn(libs, _)): @unchecked
      assertEquals(List(themedView, pagedView, plainView).map(dark), List(false, false, true))
      assertEquals(themed.querySelectorAll(".cm-comment").length, 1, "theme containers style comments")
      assertEquals(plain.querySelectorAll(".cm-comment").length, 0)
      container.setAttribute("data-editor-theme", "dark")
      root.setAttribute("data-theme", "dark")
      tick().map { _ =>
        assertEquals(List(themedView, pagedView).map(dark), List(true, true))
        handles.foreach(_.destroy())
        root.removeAttribute("data-theme")
      }
    }
  }

  test("style and class changes on the host request a re-measure until the editor is destroyed") {
    withLibraries { libs =>
      val parent = host()
      val handle = editor(parent)
      val view = viewIn(libs, parent)
      val measures = Var(0)
      val original = view.requestMeasure
      view.requestMeasure = (() => { measures.update(_ + 1); original.call(view) }): js.Function0[Unit]
      parent.setAttribute("style", "font-size: 20px")
      tick().flatMap { _ =>
        assert(measures.now() >= 1)
        val before = measures.now()
        handle.destroy()
        parent.setAttribute("class", "large")
        tick().map(_ => assertEquals(measures.now(), before))
      }
    }
  }

  test("languages select their parser and unknown languages fall back to Python") {
    withLibraries { libs =>
      def tree(language: String, doc: String): String = {
        val parent = host()
        val handle = editor(parent, doc, language)
        val view = viewIn(libs, parent)
        val names = js.Array[String]()
        libs.syntaxTree(view.state).iterate(js.Dynamic.literal(enter = ((node: js.Dynamic) => { names.push(node.name.asInstanceOf[String]); () }): js.Function1[js.Dynamic, Unit]))
        handle.destroy()
        names.mkString(",")
      }
      assertEquals(tree("sql", "SELECT 1"), "Script,Statement,Keyword,Number")
      assert(tree("cpp", "int x;").startsWith("Program"))
      assertEquals(tree("c", "int x;"), tree("c++", "int x;"))
      assertEquals(tree("ruby", "x = 1"), tree("python", "x = 1"))
      Future.unit
    }
  }

  test("Java editors preserve mixed line endings across edits, undo and programmatic documents") {
    withLibraries { libs =>
      capturingWarnings { warnings =>
        val parent = host()
        val changes = js.Array[String]()
        val source = "class A {\r\n  int x;\n}\r\n"
        val handle = editor(parent, source, "java", changes)
        val discarded = editor(host(), "class B {}", "java")
        discarded.destroy()
        val view = viewIn(libs, parent)
        assertEquals(handle.getDoc(), source)
        assertEquals(view.state.doc.toString().asInstanceOf[String], "class A {\n  int x;\n}\n")
        edit(view, 16, 17, "x")
        assertEquals(changes.length, 0, "replacing text by identical text is not a change")
        edit(view, 19, 19, "  int y;\n")
        assertEquals(changes.toList, List("class A {\r\n  int x;\n  int y;\r\n}\r\n"))
        assert(libs.undo(view).asInstanceOf[Boolean])
        assertEquals(changes.toList.last, source)
        handle.setDoc("a\nb\r\n\tc")
        assertEquals(handle.getDoc(), "a\nb\r\n\tc")
        handle.setDoc("a\nb\r\n\tc")
        assert(!libs.undo(view).asInstanceOf[Boolean], "programmatic documents start a new history")
        edit(view, 1, 1, "!")
        assertEquals(changes.toList.last, "a!\nb\r\n\tc")
        assertEquals(changes.length, 3)
        waitUntil(warnings.nonEmpty).flatMap(_ => tick()).map { _ =>
          assertEquals(warnings.toList, List("Java syntax support is unavailable; text editing remains available."),
            "only the live editor reports the missing syntax module")
          assertEquals(topNode(libs, view), "")
          handle.destroy()
        }
      }
    }
  }

  test("the JavaScript facade applies defaults and decodes asynchronous flowcharts") {
    withLibraries { libs =>
      val facade = CodeMirrorRuntime.facade.asInstanceOf[js.Dynamic]
      val minimal = host()
      val plain = facade.createEditor(js.Dynamic.literal(parent = minimal)).asInstanceOf[CodeMirrorHandle]
      val nulls = host()
      val defaults = facade.createEditor(js.Dynamic.literal(parent = nulls, doc = null, language = null,
        onDocChange = null, firstLineNumber = -3, flowchart = null)).asInstanceOf[CodeMirrorHandle]
      edit(viewIn(libs, minimal), 0, 0, "x")
      assertEquals(plain.getDoc(), "x")
      assertEquals(defaults.getDoc(), "")
      assertEquals(topNode(libs, viewIn(libs, nulls)), "Script")
      val chart = Flowchart(24, Map(1 -> FlowchartRow(List(FlowchartSlice("data:image/svg+xml,a", 24, 40, 0, 20)), "Start")))
      val sources = js.Array[String]()
      val changes = js.Array[String]()
      val charted = host()
      val analysis: js.Function1[String, js.Promise[js.Any]] = source => {
        sources.push(source)
        js.Promise.resolve[js.Any](js.JSON.parse(upickle.default.write(chart)))
      }
      val handle = facade.createEditor(js.Dynamic.literal(parent = charted, doc = "x = 1", language = "python",
        onDocChange = ((value: String) => { changes.push(value); () }): js.Function1[String, Unit], firstLineNumber = 3,
        flowchart = analysis)).asInstanceOf[CodeMirrorHandle]
      waitUntil(charted.querySelector(".cm-flowchart-row[title='Start']") != null).map { _ =>
        assertEquals(sources.toList, List("x = 1"))
        assertEquals(lineNumbers(charted).head, "3")
        edit(viewIn(libs, charted), 5, 5, "\n")
        assertEquals(changes.toList, List("x = 1\n"))
        List(plain, defaults, handle).foreach(_.destroy())
      }
    }
  }
}
