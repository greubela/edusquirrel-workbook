package it.evadid.workbook.elements.interactionElements.neuron

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

case class NeuronParameters(weights: List[Double], threshold: Double) derives ReadWriter {
  require(weights.nonEmpty && weights.forall(_.isFinite) && threshold.isFinite, "Neuron parameters must be finite")
  def weightedSum(inputs: List[Double]): Double = {
    require(inputs.size == weights.size && inputs.forall(_.isFinite), "Inputs must be finite and match the weights")
    val sum = inputs.zip(weights).map((x, w) => x * w).sum
    require(sum.isFinite, "Weighted sum overflow")
    sum
  }
  def activates(inputs: List[Double]): Boolean = weightedSum(inputs) >= threshold
}
case class NeuronExample(label: LanguageMapContentId, inputs: List[Double], expected: Boolean) derives ReadWriter {
  require(inputs.nonEmpty && inputs.forall(x => x == 0 || x == 1), "Worksheet inputs must be binary")
}
case class ThresholdNeuronInteraction(elementId: String, inputLabels: List[LanguageMapContentId], examples: List[NeuronExample],
    initial: NeuronParameters) extends WorkbookInteractionElement[NeuronParameters] {
  require(inputLabels.nonEmpty && inputLabels.size == initial.weights.size, "Labels must match weights")
  require(examples.nonEmpty && examples.forall(_.inputs.size == inputLabels.size), "Examples must match inputs")
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial
  override val serializerInteractionContent = Serializer.fromUpickleJson(summon[ReadWriter[NeuronParameters]])
  override val associatedFactory = ThresholdNeuronInteraction.factory
  def matches(parameters: NeuronParameters): List[Boolean] = {
    require(parameters.weights.size == inputLabels.size, "Wrong number of weights")
    examples.map(e => parameters.activates(e.inputs) == e.expected)
  }
  def isPassed(parameters: NeuronParameters): Boolean = matches(parameters).forall(identity)
}
object ThresholdNeuronInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[ThresholdNeuronInteraction] {
    override protected val constructorFieldOrder = List("elementId", "inputLabels", "examples", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: ThresholdNeuronInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("inputLabels", e.inputLabels).withElementAddedAs("examples", e.examples).withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): ThresholdNeuronInteraction =
      ThresholdNeuronInteraction(e.elementId, e.getElementAs[List[LanguageMapContentId]]("inputLabels"),
        e.getElementAs[List[NeuronExample]]("examples"), e.getElementAs[NeuronParameters]("initial"))
  }
}
