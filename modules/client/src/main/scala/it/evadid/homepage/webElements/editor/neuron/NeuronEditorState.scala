package it.evadid.homepage.webElements.editor.neuron

import com.raquo.airstream.state.Var
import it.evadid.workbook.elements.interactionElements.neuron.*

/** Invalid drafts remain local; only parameters safe for every example enter persistent state. */
final class NeuronEditorState(val state: Var[NeuronParameters], examples: List[NeuronExample]) {
  val weights = Var(state.now().weights.map(_.toString))
  val threshold = Var(state.now().threshold.toString)
  val invalid = Var(false)
  def restore(parameters: NeuronParameters): Unit = {
    weights.set(parameters.weights.map(_.toString)); threshold.set(parameters.threshold.toString); invalid.set(false)
  }
  def commit(): Unit = {
    val parsedWeights = weights.now().map(_.toDoubleOption.filter(_.isFinite))
    val parsedThreshold = threshold.now().toDoubleOption.filter(_.isFinite)
    val parameters = if parsedWeights.forall(_.nonEmpty) && parsedThreshold.nonEmpty then
      Some(NeuronParameters(parsedWeights.flatten, parsedThreshold.get)) else None
    val valid = parameters.filter(p => examples.forall(e =>
      e.inputs.size == p.weights.size && e.inputs.zip(p.weights).map((x, w) => x * w).sum.isFinite))
    invalid.set(valid.isEmpty)
    valid.foreach(state.set)
  }
}
