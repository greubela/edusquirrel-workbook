package it.evadid.workbook.interactions.evacuation

import upickle.default.*

enum EvacuationNeighbourhood derives ReadWriter {
  @upickle.implicits.key("Four") case Four
  @upickle.implicits.key("Eight") case Eight
}

enum EvacuationOutcome derives ReadWriter {
  @upickle.implicits.key("Evacuated") case Evacuated
  @upickle.implicits.key("Blocked") case Blocked
  @upickle.implicits.key("StepLimit") case StepLimit
}

case class EvacuationSettings(neighbourhood: EvacuationNeighbourhood = EvacuationNeighbourhood.Four,
                              speedMetresPerSecond: Double = 1.0) derives ReadWriter {
  require(speedMetresPerSecond.isFinite && speedMetresPerSecond >= 0.01 && speedMetresPerSecond <= 20,
    "Speed must be finite and between 0.01 and 20 metres per second")

  /** Source PDF: 50 cm cells; a macro step permits one move or waiting per person. */
  def secondsFor(steps: Int): Double = {
    require(steps >= 0 && steps <= EvacuationExperiment.maxSteps, "Invalid movement step count")
    steps * 0.5 / speedMetresPerSecond
  }
}

object EvacuationSettings {
  def parseSpeed(value: String): Option[Double] = scala.util.Try(value.trim.replace(',', '.').toDouble).toOption
    .filter(v => v.isFinite && v >= 0.01 && v <= 20)
}

case class EvacuationMeasurement(label: String, floor: EvacuationFloorPlan, settings: EvacuationSettings,
                                 steps: Int, remainingPeople: Int, outcome: EvacuationOutcome) derives ReadWriter {
  require(label.trim.nonEmpty && label.length <= 80, "Run label must contain 1 to 80 characters")
  require(steps >= 0 && steps <= EvacuationExperiment.maxSteps, "Invalid step count")
  require(remainingPeople >= 0 && remainingPeople <= floor.people.size, "Invalid remaining person count")
  require((outcome == EvacuationOutcome.Evacuated) == (remainingPeople == 0), "Outcome and remaining people disagree")
  require(outcome != EvacuationOutcome.StepLimit || steps == EvacuationExperiment.maxSteps, "Step limit not reached")

  def seconds: Double = settings.secondsFor(steps)
}

case class EvacuationExperiment(floor: EvacuationFloorPlan, settings: EvacuationSettings = EvacuationSettings(),
                                measurements: Seq[EvacuationMeasurement] = Nil) derives ReadWriter {
  require(measurements.size <= EvacuationExperiment.maxMeasurements, "Keep at most 12 comparison runs")

  def record(run: EvacuationMeasurement): EvacuationExperiment = {
    require(run.floor == floor && run.settings == settings, "Measurement must describe the current experiment")
    require(measurements.size < EvacuationExperiment.maxMeasurements, "Comparison table is full")
    copy(measurements = measurements :+ run)
  }
}

object EvacuationExperiment {
  val maxSteps = 250
  val maxMeasurements = 12

  def initial: EvacuationExperiment = EvacuationExperiment(EvacuationFloorPlan.empty())
}
