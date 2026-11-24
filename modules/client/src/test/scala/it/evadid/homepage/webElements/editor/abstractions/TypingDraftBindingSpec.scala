package it.evadid.homepage.webElements.editor.abstractions

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import org.scalajs.dom

import scala.concurrent.ExecutionContext

class TypingDraftBindingSpec extends EditorDomSuite {
  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  override protected def jsdomGlobals: List[String] = super.jsdomGlobals ++ List("KeyboardEvent", "FocusEvent")

  private def press(target: dom.html.Element, typed: String): Unit =
    target.dispatchEvent(new dom.KeyboardEvent("keydown", new dom.KeyboardEventInit { key = typed; bubbles = true }))

  test("the answer receives a token after its ending key or a quiet period, not each keystroke") {
    val answer = Var("fo")
    val binding = TypingDraftBinding(answer)
    val input = textArea()
    val unmount = mount(div(binding.binders, input))
    def typeKey(key: String, text: String): Unit = { press(input.ref, key); binding.draft.set(text) }
    typeKey("o", "foo")
    typeKey("r", "foor")
    assertEquals(answer.now(), "fo", "keystrokes inside the token stay in the editor")
    typeKey(";", "foor;")
    assertEquals(answer.now(), "foor;")
    typeKey("x", "foor;x")
    assertEquals(answer.now(), "foor;")
    eventually(answer.now() == "foor;x", "the quiet period commits the last edit").map { _ =>
      answer.set("restored")
      assertEquals(binding.draft.now(), "restored", "an outside change replaces the draft")
      typeKey("y", "restoredy")
      input.ref.dispatchEvent(new dom.FocusEvent("focusout", new dom.FocusEventInit { bubbles = true }))
      assertEquals(answer.now(), "restoredy", "leaving the editor commits pending edits")
      typeKey("z", "restoredyz")
      unmount()
      eventually(answer.now() == "restoredyz", "closing the editor commits pending edits")
    }
  }

  test("a nested editor hands over its typed text before the binding commits on blur and on unmount") {
    val answer = Var("a")
    val binding = TypingDraftBinding(answer)
    // Like CodeMirrorEditor: the typed text reaches the draft only when focus leaves or the editor unmounts.
    var typed = Option.empty[String]
    def handOver(): Unit = typed.foreach { text => typed = None; binding.draft.set(text) }
    val input = textArea(onBlur.useCapture --> (_ => handOver()), onUnmountCallback(_ => handOver()))
    val unmount = mount(div(binding.binders, div(input)))
    press(input.ref, "b")
    typed = Some("ab")
    input.ref.dispatchEvent(new dom.FocusEvent("blur"))
    input.ref.dispatchEvent(new dom.FocusEvent("focusout", new dom.FocusEventInit { bubbles = true }))
    assertEquals(answer.now(), "ab", "the text typed just before leaving the editor is committed at once")
    press(input.ref, "c")
    typed = Some("abc")
    unmount()
    eventually(answer.now() == "abc", "the text typed just before closing the editor is committed")
  }
}
