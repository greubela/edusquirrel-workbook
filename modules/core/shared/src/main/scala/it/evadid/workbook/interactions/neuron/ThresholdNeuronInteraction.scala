package it.evadid.workbook.interactions.neuron

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.workbook.history.InteractionHistory
import upickle.default.*

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
case class NeuronParameters(weights: Seq[Double], threshold: Double) derives ReadWriter {
  require(weights.nonEmpty && weights.forall(_.isFinite) && threshold.isFinite, "Neuron parameters must be finite")
  def weightedSum(inputs: Seq[Double]): Double = {
    require(inputs.size == weights.size && inputs.forall(_.isFinite), "Inputs must be finite and match the weights")
    val sum = inputs.zip(weights).map((x, w) => x * w).sum
    require(sum.isFinite, "Weighted sum overflow")
    sum
  }
  def activates(inputs: Seq[Double]): Boolean = weightedSum(inputs) >= threshold
}
case class NeuronExample(label: LanguageMapContentId, inputs: Seq[Double], expected: Boolean) derives ReadWriter {
  require(inputs.nonEmpty && inputs.forall(x => x == 0 || x == 1), "Worksheet inputs must be binary")
}
case class ThresholdNeuronInteraction(elementId: String, inputLabels: Seq[LanguageMapContentId], examples: Seq[NeuronExample],
    initial: NeuronParameters) extends WorkbookInteractionElement[ThresholdNeuronInteractionFormat.type] {

  require(inputLabels.nonEmpty && inputLabels.size == initial.weights.size, "Labels must match weights")
  require(examples.nonEmpty && examples.forall(_.inputs.size == inputLabels.size), "Examples must match inputs")
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = initial

  override val associatedFactory = ThresholdNeuronInteraction.factory

  def matches(parameters: NeuronParameters): Seq[Boolean] = {
    require(parameters.weights.size == inputLabels.size, "Wrong number of weights")
    examples.map(e => parameters.activates(e.inputs) == e.expected)
  }
  def isPassed(parameters: NeuronParameters): Boolean = matches(parameters).forall(identity)
}
object ThresholdNeuronInteraction  {

  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[ThresholdNeuronInteraction] {
    override protected val constructorFieldOrder = List("elementId", "inputLabels", "examples", "initial")
    override def finishSerialization(base: WorkbookElementSerializable, e: ThresholdNeuronInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("inputLabels", e.inputLabels).withElementAddedAs("examples", e.examples).withElementAddedAs("initial", e.initial)
    override def finishDeserialization(e: WorkbookElementSerializable): ThresholdNeuronInteraction =
      ThresholdNeuronInteraction(e.elementId, e.getElementAs[Seq[LanguageMapContentId]]("inputLabels"),
        e.getElementAs[Seq[NeuronExample]]("examples"), e.getElementAs[NeuronParameters]("initial"))
  }
}
