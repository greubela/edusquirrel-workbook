package it.evadid.homepage.webElements.editor.neuron

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.workbook.elements.interactionElements.neuron.{NeuronExample, NeuronParameters}
import org.scalajs.dom

class ThresholdNeuronEditorSpec extends UiDomSuite {
  private val labels = Seq(LanguageMapContentId("neuron/rain"), LanguageMapContentId("neuron/wind"))
  /** An AND gate. */
  private val examples = Seq(
    NeuronExample(LanguageMapContentId("neuron/none"), Seq(0, 0), false),
    NeuronExample(LanguageMapContentId("neuron/one"), Seq(1, 0), false),
    NeuronExample(LanguageMapContentId("neuron/both"), Seq(1, 1), true))
  private val initial = NeuronParameters(Seq(1, 1), 1)
  private def rows(root: dom.Element) = all(root, "tbody tr").map(row => all(row, "td").map(_.textContent))
  private def inputs(root: dom.Element) = all(root, "input[type=number]")

  test("weights and threshold drive the weighted sums, outputs and comparisons for every example") {
    val state = Var(initial)
    val editor = ThresholdNeuronEditor(pageInfo, state, labels, examples, initial)
    assert(!editor.dismissOnOutsideClick)
    mounted(editor.getDomElement()) { root =>
      assertEquals(rows(root).map(_.take(4)), List(List("0", "0", "0", "0"), List("1", "0", "1", "1"), List("1", "1", "2", "1")))
      assertEquals(rows(root)(1)(4), "0", "expected output")
      assert(rows(root)(1)(5).contains(loadingText("digitalWorkbooks/differs")))
      typeInto(inputs(root)(2), "1.5")
      assertEquals(state.now(), NeuronParameters(Seq(1, 1), 1.5))
      assertEquals(rows(root).map(_(3)), List("0", "0", "1"))
      assert(rows(root).forall(_(5).contains(loadingText("digitalWorkbooks/matches"))), "the AND gate is solved")
      typeInto(inputs(root).head, "0.5")
      assertEquals(state.now().weights, Seq(0.5, 1.0))
      assertEquals(rows(root)(2)(2), "1.5")
    }
  }

  test("invalid numbers are flagged and never saved; reset and restored answers replace the drafts") {
    val state = Var(initial)
    mounted(ThresholdNeuronEditor(pageInfo, state, labels, examples, initial).getDomElement()) { root =>
      typeInto(inputs(root)(1), "heavy")
      assert(one(root, ".neuron-error").textContent.contains(loadingText("digitalWorkbooks/invalidNumbers")))
      assertEquals(state.now(), initial)
      typeInto(inputs(root)(1), "2")
      assertEquals(one(root, ".neuron-error").textContent, "")
      assertEquals(state.now().weights, Seq(1.0, 2.0))
      click(buttonNamed(root, loadingText("digitalWorkbooks/reset")))
      assertEquals(state.now(), initial)
      assertEquals(inputs(root).map(valueOf), List("1", "1", "1"))
      state.set(NeuronParameters(Seq(3, 4), 5))
      assertEquals(inputs(root).map(valueOf), List("3", "4", "5"), "a restored answer is shown")
    }
  }
}
