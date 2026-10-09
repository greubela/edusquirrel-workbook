package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.evacuation.*
import it.evadid.workbook.model.evacuation.*
import it.evadid.workbook.elements.structureElements.Workbook

/** Partial adaptation of the original grid-automaton PDF. The classic PDF remains available. */
case class CreateEvacuationWorkbook(fullInfo: FullInfo) extends WorkbookFactory {
  override val workbookId = "workbookEvacuation"
  override def createWorkbook: Workbook = workbook("digitalWorkbooks/evacuationWorkbookTitle", List(
    section("evacuation-introduction", "digitalWorkbooks/evacuationIntroduction", List(
      container("digitalWorkbooks/evacuationIntroduction", List(
        instructionMarkdown("digitalWorkbooks/evacuationScope"),
        instructionPlaintext("digitalWorkbooks/evacuationPrediction"), createTextInput("evacuation-prediction"))))),
    section("evacuation-experiment", "digitalWorkbooks/evacuationSimulationTitle", List(
      container("digitalWorkbooks/evacuationSimulationTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationExperimentTask"),
        EvacuationSimulationInteraction("evacuation-layout-experiment", EvacuationExperiment(CreateEvacuationWorkbook.corridor)),
        instructionPlaintext("digitalWorkbooks/evacuationComparison"), createTextInput("evacuation-comparison"))))),
    section("evacuation-model-time", "digitalWorkbooks/evacuationTimeTitle", List(
      container("digitalWorkbooks/evacuationTimeTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationTimeTask"), createTextInput("evacuation-time-derivation"),
        instructionPlaintext("digitalWorkbooks/evacuationSpeedClaims"), createTextInput("evacuation-speed-claims"))))),
    section("evacuation-construction", "digitalWorkbooks/evacuationTitle", List(
      container("digitalWorkbooks/evacuationTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationExampleTask"),
        EvacuationConstructFloorInteraction("evacuation-own-floor"))))),
    section("evacuation-limits", "digitalWorkbooks/evacuationLimitsTitle", List(
      container("digitalWorkbooks/evacuationLimitsTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationLimitsTask"), createTextInput("evacuation-model-limits"),
        instructionPlaintext("digitalWorkbooks/evacuationTransferTask"), createTextInput("evacuation-transfer")))))
  ), User.AndreGreubel)
}
object CreateEvacuationWorkbook {
  // Narrow corridor: deterministic one-person-wide bottleneck, not a reconstruction of the PDF sports hall.
  val corridor = EvacuationFloorPlan(7, 3,
    List.fill(7)(EvacuationTile.Wall) ++
      List.fill(6)(EvacuationTile.Floor) ++ List(EvacuationTile.Exit) ++ List.fill(7)(EvacuationTile.Wall),
    Set(7, 8, 9, 10))
}
