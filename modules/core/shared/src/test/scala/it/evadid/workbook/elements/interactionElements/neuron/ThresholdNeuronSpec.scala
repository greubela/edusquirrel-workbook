package it.evadid.workbook.elements.interactionElements.neuron

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite
import upickle.default.*

class ThresholdNeuronSpec extends FunSuite {
  private val id = LanguageMapContentId("test/input")
  private val exercise = ThresholdNeuronInteraction("neuron", List(id, id), List(
    NeuronExample(id, List(1, 0), true), NeuronExample(id, List(0, 1), false)), NeuronParameters(List(1, 1), 1))
  test("weighted sums use every input and signed/decimal weights") {
    assertEquals(NeuronParameters(List(2, -0.5, 0), 2).weightedSum(List(1, 2, 100)), 1.0)
  }
  test("activation includes the threshold boundary") {
    val p = NeuronParameters(List(2), 2)
    assert(p.activates(List(1)))
    assert(!p.activates(List(0.99)))
    assert(p.activates(List(2)))
  }
  test("passing requires all examples, not just one") {
    assertEquals(exercise.matches(exercise.initial), List(true, false))
    assert(!exercise.isPassed(exercise.initial))
    assert(exercise.isPassed(NeuronParameters(List(2, 0), 1)))
    assert(exercise.isPassed(NeuronParameters(List(3, -1), 2)))
  }
  test("finite numbers, dimensions and overflow are validated") {
    intercept[IllegalArgumentException](NeuronParameters(Nil, 0))
    for (bad <- List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)) {
      intercept[IllegalArgumentException](NeuronParameters(List(bad), 0))
      intercept[IllegalArgumentException](NeuronParameters(List(1), bad))
      intercept[IllegalArgumentException](NeuronParameters(List(1), 0).weightedSum(List(bad)))
    }
    intercept[IllegalArgumentException](NeuronParameters(List(1), 0).weightedSum(Nil))
    intercept[IllegalArgumentException](NeuronParameters(List(Double.MaxValue), 0).weightedSum(List(2)))
    intercept[IllegalArgumentException](exercise.matches(NeuronParameters(List(1), 0)))
  }
  test("training configuration rejects nonbinary, empty and mismatched examples") {
    intercept[IllegalArgumentException](NeuronExample(id, List(0.5), true))
    intercept[IllegalArgumentException](NeuronExample(id, Nil, true))
    intercept[IllegalArgumentException](exercise.copy(examples = Nil))
    intercept[IllegalArgumentException](exercise.copy(inputLabels = List(id)))
    intercept[IllegalArgumentException](exercise.copy(examples = List(NeuronExample(id, List(1), true))))
  }
  test("exercise definition round-trips both registered workbook formats") {
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(exercise)), exercise)
  }
  test("persisted parameters round-trip and malformed numbers are rejected") {
    val p = NeuronParameters(List(1.25, -2), 0.5)
    assertEquals(exercise.serializerInteractionContent.deserialize(exercise.serializerInteractionContent.serialize(p)), p)
    intercept[Exception](read[NeuronParameters]("""{"weights":[],"threshold":0}"""))
  }
}
