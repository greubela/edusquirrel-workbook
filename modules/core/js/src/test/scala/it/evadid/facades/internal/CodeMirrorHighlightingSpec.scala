package it.evadid.facades.internal

import it.evadid.facades.CodeMirrorSuite
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

class CodeMirrorHighlightingSpec extends CodeMirrorSuite {
  private val source = List(
    "def go(x):",
    "    led.on()  # TODO later",
    "    s = 'a (b)'",
    "    receive_go ()",
    "    TODO_list = HIGH",
    "    [x]").mkString("\n")

  /** Runs `body` once the background parse covers the whole document, as decorations depend on it. */
  private def withView[A](styleComments: Option[Boolean])(body: (js.Dynamic, js.Dynamic, js.Dynamic, js.Dynamic) => A): Future[A] =
    withLibraries { libs =>
      val plugin = styleComments.fold(CodeMirrorHighlighting.extension())(CodeMirrorHighlighting.extension)
      val language = js.Dynamic.newInstance(libs.Compartment)()
      val view = js.Dynamic.newInstance(libs.EditorView)(js.Dynamic.literal(doc = source, parent = host(),
        extensions = js.Array(language.of(libs.python()), plugin)))
      parsed(libs, view, attempts = 500).map(_ => body(libs, view, view.plugin(plugin.asInstanceOf[js.Any]), language))
        .andThen(_ => view.destroy())
    }

  private def parsed(libs: js.Dynamic, view: js.Dynamic, attempts: Int): Future[Unit] =
    if libs.syntaxTree(view.state).length.asInstanceOf[Int] >= view.state.doc.length.asInstanceOf[Int] then Future.unit
    else if attempts == 0 then Future.failed(AssertionError("the document was not parsed"))
    else {
      val waited = scala.concurrent.Promise[Unit]()
      js.timers.setTimeout(10)(waited.success(()))
      waited.future.flatMap(_ => parsed(libs, view, attempts - 1))
    }

  private val plain = "cm-plain-name"
  private val accent = "cm-accent-name"
  private val expected = List("go" -> accent, "(" -> plain, "x" -> plain, ")" -> plain,
    "led" -> accent, "on" -> accent, "(" -> plain, ")" -> plain,
    "s" -> plain,
    "receive_go" -> "cm-receive-go", "(" -> plain, ")" -> plain,
    "TODO_list" -> "cm-todo-token", "HIGH" -> accent,
    "[" -> plain, "x" -> plain, "]" -> plain)

  test("identifiers and brackets are classified by role while keywords, comments and strings keep their syntax colors") {
    withView(styleComments = None) { (_, view, plugin, _) =>
      assertEquals(decorated(plugin.decorations, view.state.doc), expected)
    }
  }

  test("comment styling marks whole comments when requested") {
    withView(styleComments = Some(true)) { (_, view, plugin, _) =>
      val (before, after) = expected.splitAt(8)
      assertEquals(decorated(plugin.decorations, view.state.doc), before ++ List("# TODO later" -> "cm-comment") ++ after)
    }
  }

  test("decorations are rebuilt for edits and reconfiguration but kept for selection changes") {
    withView(styleComments = Some(false)) { (libs, view, plugin, language) =>
      val initial = plugin.decorations
      view.dispatch(js.Dynamic.literal(selection = js.Dynamic.literal(anchor = 3)))
      assert(plugin.decorations eq initial, "selection changes keep the decorations")
      view.dispatch(js.Dynamic.literal(changes = js.Dynamic.literal(from = 0, insert = "y.z\n")))
      assertEquals(decorated(plugin.decorations, view.state.doc).take(3), List("y" -> accent, "z" -> plain, "go" -> accent))
      view.dispatch(js.Dynamic.literal(effects = language.reconfigure(js.Array())))
      val words = decorated(plugin.decorations, view.state.doc).map(_._1)
      assert(words.contains("a") && words.contains("later"), "without a parser strings and comments are not recognised")
      assert(words.contains("TODO"))
    }
  }
}
