package it.evadid.homepage.webElements.editor.neuron

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.neuron.*
import munit.FunSuite

class NeuronEditorStateSpec extends FunSuite {
  private def controller() = new NeuronEditorState(Var(NeuronParameters(List(1, 1), 2)),
    List(NeuronExample(LanguageMapContentId("test/row"), List(1, 1), true)))
  test("valid edits update the supplied state with signed and decimal numbers") {
    val c = controller(); c.weights.set(List("-0.25", "3")); c.threshold.set("1.5"); c.commit()
    assertEquals(c.state.now(), NeuronParameters(List(-0.25, 3), 1.5)); assert(!c.invalid.now())
  }
  test("empty, malformed, nonfinite and overflow drafts never overwrite the saved value") {
    for (weights <- List(List("", "1"), List("oops", "1"), List("NaN", "1"), List("Infinity", "1"),
      List("1e308", "1e308"), List("1"))) {
      val c = controller(); val initial = c.state.now(); c.weights.set(weights); c.commit()
      assert(c.invalid.now()); assertEquals(c.state.now(), initial); assertEquals(c.weights.now(), weights)
    }
  }
  test("invalid threshold stays local and a corrected draft saves") {
    val c = controller(); c.threshold.set(""); c.commit(); assert(c.invalid.now())
    c.threshold.set("0"); c.commit(); assertEquals(c.state.now().threshold, 0.0); assert(!c.invalid.now())
  }
  test("restoration updates controls and clears a stale invalid draft") {
    val c = controller(); c.weights.set(List("", "1")); c.commit()
    val restored = NeuronParameters(List(2, -1), 0.5); c.restore(restored)
    assertEquals(c.weights.now().map(_.toDouble), List(2.0, -1.0)); assertEquals(c.threshold.now(), "0.5"); assert(!c.invalid.now())
  }
}
