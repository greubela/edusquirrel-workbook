package it.evadid.homepage.webElements.editor.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{Java, Python}
import it.evadid.facades.CodeMirror
import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.homepage.webElements.editor.config.CodeMirrorEditorConfig
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.vm.code.errors.{ProgrammingConversionError, ProgrammingConversionKind}
import it.evadid.workbook.elements.interactionElements.programming.state.*
import org.scalajs.dom
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

/** The Python editor: one native CodeMirror instance bound to the central answer, with a plain-text fallback. */
class PythonEditorSpec extends EditorDomSuite {
  private val logger = Logger.withNameAndPrefixes(Some("PythonEditorSpec"), PrintToStdLogger.printNothing)

  /** A native editor double recording what the Scala side asks of it. */
  private final class NativeEditor(val config: CodeMirror.EditorConfig) {
    val doc = Var(config.doc)
    val writes = Var(Vector.empty[String])
    val diagnostics = Var(Vector.empty[js.Dynamic])
    val destroyed = Var(false)
    /** What the user types in the native editor. */
    def type_(text: String): Unit = { doc.set(text); config.onDocChange(text) }
    val handle: CodeMirror.CodeMirrorHandle = js.Dynamic.literal(
      getDoc = (() => doc.now()): js.Function0[String],
      setDoc = ((value: String) => { writes.update(_ :+ value); doc.set(value) }): js.Function1[String, Unit],
      setDiagnostics = ((values: js.Array[js.Object]) => diagnostics.set(values.toVector.map(_.asInstanceOf[js.Dynamic]))): js.Function1[js.Array[js.Object], Unit],
      setFirstLineNumber = ((_: Int) => ()): js.Function1[Int, Unit],
      focus = (() => ()): js.Function0[Unit],
      destroy = (() => destroyed.set(true)): js.Function0[Unit]
    ).asInstanceOf[CodeMirror.CodeMirrorHandle]
  }

  private final class Fixture(source: String, sameSource: (String, String) => Boolean = _ == _,
      diagnostics: Signal[Seq[CodeMirrorEditor.Diagnostic]] = Val(Nil)) {
    val text = Var(source)
    val created = Var(Vector.empty[NativeEditor])
    val ready = Var(Option.empty[Promise[CodeMirror.CodeMirrorHandle]])
    private def create(config: CodeMirror.EditorConfig): Future[CodeMirror.CodeMirrorHandle] = {
      val native = NativeEditor(config)
      created.update(_ :+ native)
      val promise = Promise[CodeMirror.CodeMirrorHandle]()
      ready.set(Some(promise))
      promise.future.map(_ => native.handle)
    }
    val editor = CodeMirrorEditor(logger, text, Python, Var(CodeMirrorEditorConfig.Default), diagnostics = diagnostics,
      createEditor = create, loadingLabel = Val("loading"), sameSource = sameSource)
    val close = mount(editor.getDomElement())
    def native: NativeEditor = created.now().last
    def loadNative(): Unit = ready.now().get.trySuccess(native.handle)
    def failNative(): Unit = ready.now().get.tryFailure(IllegalStateException("CodeMirror unavailable"))
  }

  private def pause(millis: Int): Future[Unit] = { val done = Promise[Unit](); setTimeout(millis.toDouble)(done.success(())); done.future }
  private def tick(): Future[Unit] = { val done = Promise[Unit](); setTimeout(5)(done.success(())); done.future }
  private def until(condition: => Boolean, attempts: Int = 400): Future[Unit] =
    if condition then Future.unit
    else if attempts == 0 then Future.failed(AssertionError("condition not reached"))
    else tick().flatMap(_ => until(condition, attempts - 1))
  private def fallback = dom.document.querySelector(".code-mirror-editor__fallback").asInstanceOf[dom.html.TextArea]
  private def loading = dom.document.querySelector(".code-mirror-editor__loading").asInstanceOf[dom.html.Element]
  private def hidden(element: dom.Element): Boolean = element.hasAttribute("hidden")
  private def typeInFallback(text: String): Unit = {
    fallback.value = text
    fallback.dispatchEvent(js.Dynamic.newInstance(js.Dynamic.global.window.Event)("input",
      js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event])
  }
  private def finish(close: () => Unit)(run: => Future[Unit]): Future[Unit] =
    Future.fromTry(scala.util.Try(run)).flatten.transform { outcome => close(); outcome }

  test("until the native editor is ready the fallback shows and edits the answer; then it hides") {
    val fixture = Fixture("print(1)")
    finish(fixture.close) {
      assertEquals(fallback.value, "print(1)")
      assert(!hidden(fallback) && !hidden(loading))
      typeInFallback("print(2)")
      assertEquals(fixture.text.now(), "print(2)")
      until(fixture.created.now().nonEmpty).map { _ =>
        assertEquals(fixture.native.config.doc, "print(2)", "the native editor starts from the current answer")
        assertEquals(fixture.native.config.language, "python")
        fixture.loadNative()
      }.flatMap(_ => until(hidden(fallback))).map(_ => assert(hidden(loading)))
    }
  }

  test("native typing writes the answer and answer changes reach the native document exactly once") {
    val fixture = Fixture("x = 1")
    finish(fixture.close) {
      until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).flatMap { _ =>
        val native = fixture.native
        native.type_("x = 2")
        assertEquals(fixture.text.now(), "x = 1", "the answer follows once typing pauses")
        until(fixture.text.now() == "x = 2").map { _ =>
          assertEquals(native.writes.now(), Vector.empty[String], "the user's own edit is not written back")
          fixture.text.set("x = 3")
          assertEquals(native.doc.now(), "x = 3")
          fixture.text.set("x = 3")
          assertEquals(native.writes.now(), Vector("x = 3"))
          assertEquals(fixture.created.now().size, 1, "answer changes never rebuild the editor")
        }
      }
    }
  }

  test("typing writes the answer once per pause, and at once when the editor loses focus or is unmounted") {
    val fixture = Fixture("x = 1")
    until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).flatMap { _ =>
      val native = fixture.native
      native.type_("x = 2")
      native.type_("x = 23")
      assertEquals(fixture.text.now(), "x = 1", "keystrokes stay in the editor while typing")
      until(fixture.text.now() == "x = 23").map { _ =>
        native.type_("x = 4")
        dom.document.querySelector(".code-mirror-editor").dispatchEvent(js.Dynamic.newInstance(
          js.Dynamic.global.window.Event)("blur").asInstanceOf[dom.Event])
        assertEquals(fixture.text.now(), "x = 4", "leaving the editor writes the typed text")
        native.type_("x = 5")
        fixture.close()
        assertEquals(fixture.text.now(), "x = 5", "unmounting writes the typed text")
      }
    }
  }

  test("an equivalent document keeps the user's formatting in both directions") {
    val same = (a: String, b: String) => a.trim == b.trim
    val fixture = Fixture("y = 1", sameSource = same)
    finish(fixture.close) {
      until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).map { _ =>
        fixture.native.type_("y = 1\n\n")
        assertEquals(fixture.text.now(), "y = 1", "an equivalent edit does not change the answer")
        fixture.text.set("  y = 1")
        assertEquals(fixture.native.doc.now(), "y = 1\n\n", "an equivalent answer does not overwrite the typed text")
      }
    }
  }

  test("typed text never overwrites an answer change that arrived while it was pending") {
    val fixture = Fixture("x = 1")
    until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).flatMap { _ =>
      val native = fixture.native
      native.type_("x = 2")
      fixture.text.set("restored = 1")
      assertEquals(native.doc.now(), "restored = 1", "the outside change replaces the typed text")
      pause(CodeMirrorEditor.typingWriteDelayMillis + 100).map { _ =>
        assertEquals(fixture.text.now(), "restored = 1", "the typing pause does not write the replaced text back")
        native.type_("x = 3")
        fixture.text.set("converted")
        fixture.close()
        assertEquals(fixture.text.now(), "converted", "unmounting does not undo a conversion")
      }
    }.transform { outcome => scala.util.Try(fixture.close()); outcome }
  }

  test("hiding the page writes the typed text at once") {
    val fixture = Fixture("x = 1")
    finish(fixture.close) {
      until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).map { _ =>
        fixture.native.type_("x = 2")
        dom.document.dispatchEvent(js.Dynamic.newInstance(js.Dynamic.global.window.Event)("visibilitychange",
          js.Dynamic.literal(bubbles = true)).asInstanceOf[dom.Event])
        assertEquals(fixture.text.now(), "x = 2")
      }
    }
  }

  test("typing goes on over an answer change that equals the typed text") {
    val fixture = Fixture("x = 1")
    until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).flatMap { _ =>
      val native = fixture.native
      native.type_("x = 2")
      // e.g. the same edit synchronized from another tab
      fixture.text.set("x = 2")
      native.type_("x = 23")
      pause(CodeMirrorEditor.typingWriteDelayMillis + 100).map { _ =>
        assertEquals(fixture.text.now(), "x = 23", "the text typed afterwards is written")
      }
    }.transform { outcome => scala.util.Try(fixture.close()); outcome }
  }

  test("a failed native load leaves the fallback editable and hides the loading notice") {
    val fixture = Fixture("print(1)")
    finish(fixture.close) {
      until(fixture.created.now().nonEmpty).map(_ => fixture.failNative()).flatMap(_ => until(hidden(loading))).map { _ =>
        assert(!hidden(fallback))
        typeInFallback("print(3)")
        assertEquals(fixture.text.now(), "print(3)")
      }
    }
  }

  test("unmounting releases the native editor, also one that arrives late, and stops its writes") {
    val early = Fixture("a = 1")
    until(early.created.now().nonEmpty).flatMap { _ =>
      early.close()
      early.loadNative()
      until(early.native.destroyed.now()).map { _ =>
        early.native.type_("late")
        assertEquals(early.text.now(), "a = 1", "a released editor cannot write the answer")
      }
    }.flatMap { _ =>
      val mounted = Fixture("b = 1")
      until(mounted.created.now().nonEmpty).map(_ => mounted.loadNative()).flatMap(_ => until(hidden(fallback))).map { _ =>
        mounted.close()
        assert(mounted.native.destroyed.now())
        mounted.native.type_("after unmount")
        assertEquals(mounted.text.now(), "b = 1")
      }
    }
  }

  test("conversion errors mark their line and column in the native editor, explained in the page language") {
    val marker = Var(Option.empty[ProgrammingConversionError])
    val fixture = Fixture("for", diagnostics = marker.signal.map(_.toSeq.flatMap(CodeMirrorEditor.mark(_, "cannot convert"))))
    finish(fixture.close) {
      until(fixture.created.now().nonEmpty).map(_ => fixture.loadNative()).flatMap(_ => until(hidden(fallback))).map { _ =>
        marker.set(Some(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct, Some(2), Some(4))))
        val marked = fixture.native.diagnostics.now()
        assertEquals(marked.map(d => (d.line.asInstanceOf[Int], d.fromCh.asInstanceOf[Int], d.toCh.asInstanceOf[Int],
          d.severity.asInstanceOf[String], d.message.asInstanceOf[String])), Vector((2, 3, 4, "error", "cannot convert")))
        marker.set(Some(ProgrammingConversionError(ProgrammingConversionKind.UnsupportedConstruct)))
        assertEquals(fixture.native.diagnostics.now(), Vector.empty[js.Dynamic], "an error without a position marks nothing")
        marker.set(None)
        assertEquals(fixture.native.diagnostics.now(), Vector.empty[js.Dynamic])
      }
    }
  }

  test("the Eva Python tab shows Python answers as text and stores edits as structured Python") {
    val tab = it.evadid.homepage.webElements.editor.code.PythonEditor.PythonComponentEditor.forEvaTab(CodeMirrorEditorConfig.Default)
    assertEquals(tab.language, Python)
    val answer = Var[ProgrammingState](ProgrammingStateRaw(Python, "print('raw')"))
    val close = mount(tab.create(answer, EvaTestContext()).getDomElement())
    finish(close) {
      assertEquals(fallback.value, "print('raw')")
      typeInFallback("print('edited')")
      until(answer.now() == ProgrammingStatePythonStructured("print('edited')")).map { _ =>
      val structured = ProgrammingStatePythonStructured("class A:\n    pass\n\nprint(1)")
      answer.set(structured)
      assertEquals(fallback.value, structured.toString)
      answer.set(ProgrammingStateRaw(Java, "int x = 1;"))
      assertEquals(fallback.value, ProgrammingStatePythonStructured("").toString, "the outgoing editor shows only its inactive Python value")
      assertEquals(answer.now(), ProgrammingStateRaw(Java, "int x = 1;"): ProgrammingState)
      }
    }
  }

  test("languages map to their native CodeMirror modes") {
    assertEquals(CodeMirrorEditor.languageToJs(Python), "python")
    assertEquals(CodeMirrorEditor.languageToJs(Java), "java")
  }
}
