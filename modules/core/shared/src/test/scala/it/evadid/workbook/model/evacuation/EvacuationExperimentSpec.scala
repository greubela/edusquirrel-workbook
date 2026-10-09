package it.evadid.workbook.model.evacuation

import it.evadid.workbook.elements.interactionElements.evacuation.EvacuationSimulationInteraction
import munit.FunSuite
import upickle.default.*

class EvacuationExperimentSpec extends FunSuite {
  private val floor = EvacuationFloorPlan(3, 1, List(EvacuationTile.Floor, EvacuationTile.Floor, EvacuationTile.Exit), Set(0))
  test("model time uses 0.5 metre cells and the saved speed, not playback wall time") {
    assertEquals(EvacuationSettings().secondsFor(6), 3.0)
    assertEquals(EvacuationSettings(speedMetresPerSecond = 3).secondsFor(6), 1.0)
    assertEquals(EvacuationSettings(speedMetresPerSecond = 6).secondsFor(6), 0.5)
    assertEquals(EvacuationSettings().secondsFor(0), 0.0)
    for (speed <- List(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity, 0.0, -1.0, 0.001, 21.0))
      intercept[IllegalArgumentException](EvacuationSettings(speedMetresPerSecond = speed))
    intercept[IllegalArgumentException](EvacuationSettings().secondsFor(-1))
    intercept[IllegalArgumentException](EvacuationSettings().secondsFor(251))
  }
  test("speed drafts accept dot or comma decimals and reject incomplete/nonfinite values") {
    assertEquals(EvacuationSettings.parseSpeed(" 1,5 "), Some(1.5))
    assertEquals(EvacuationSettings.parseSpeed("3.0"), Some(3.0))
    for (draft <- List("", "-", "abc", "NaN", "Infinity", "1,2,3", "0", "21"))
      assertEquals(EvacuationSettings.parseSpeed(draft), None)
  }
  test("recording snapshots preserves earlier layouts and settings and limits history") {
    val original = EvacuationExperiment(floor)
    val run = EvacuationMeasurement("Baseline", floor, original.settings, 2, 0, EvacuationOutcome.Evacuated)
    val saved = original.record(run)
    val changed = saved.copy(floor = floor.copy(people = Set(1)), settings = EvacuationSettings(speedMetresPerSecond = 2))
    assertEquals(changed.measurements.head.floor, floor)
    assertEquals(changed.measurements.head.seconds, 1.0)
    intercept[IllegalArgumentException](changed.record(run))
    val full = original.copy(measurements = List.fill(12)(run))
    intercept[IllegalArgumentException](full.record(run))
    intercept[IllegalArgumentException](original.copy(measurements = List.fill(13)(run)))
  }
  test("measurement validation distinguishes partial outcomes from evacuation") {
    val base = EvacuationMeasurement("Blocked", floor, EvacuationSettings(), 0, 1, EvacuationOutcome.Blocked)
    assertEquals(base.seconds, 0.0)
    intercept[IllegalArgumentException](base.copy(label = " "))
    intercept[IllegalArgumentException](base.copy(label = "x" * 81))
    intercept[IllegalArgumentException](base.copy(remainingPeople = -1))
    intercept[IllegalArgumentException](base.copy(remainingPeople = 2))
    intercept[IllegalArgumentException](base.copy(outcome = EvacuationOutcome.Evacuated))
    intercept[IllegalArgumentException](base.copy(remainingPeople = 0))
    intercept[IllegalArgumentException](base.copy(outcome = EvacuationOutcome.StepLimit))
    assertEquals(base.copy(steps = 250, outcome = EvacuationOutcome.StepLimit).remainingPeople, 1)
  }
  test("settings and named run snapshots survive learner-state serialization") {
    val settings = EvacuationSettings(EvacuationNeighbourhood.Eight, 3)
    val run = EvacuationMeasurement("Two exits", floor, settings, 2, 0, EvacuationOutcome.Evacuated)
    val answer = EvacuationExperiment(floor, settings).record(run)
    val interaction = EvacuationSimulationInteraction("experiment", answer)
    assertEquals(interaction.serializerInteractionContent.deserialize(interaction.serializerInteractionContent.serialize(answer)), answer)
    val malformed = writeJs(answer)
    malformed("settings")("speedMetresPerSecond") = 0
    assert(scala.util.Try(read[EvacuationExperiment](malformed)).isFailure)
  }
}
