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
        instructionPlaintext("digitalWorkbooks/evacuationTransferTask"), createTextInput("evacuation-transfer"))))),
    section("evacuation-school-improvements", "digitalWorkbooks/evacuationSchoolTitle", List(
      container("digitalWorkbooks/evacuationSchoolTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationSchoolTask"), createTextInput("evacuation-school-proposals"))))),
    section("evacuation-lockers", "digitalWorkbooks/evacuationLockersTitle", List(
      container("digitalWorkbooks/evacuationLockersTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationLayoutProvenance"),
        instructionPlaintext("digitalWorkbooks/evacuationLockersPrediction"), createTextInput("evacuation-lockers-prediction"),
        EvacuationSimulationInteraction("evacuation-lockers-experiment", EvacuationExperiment(CreateEvacuationWorkbook.lockerHall)),
        instructionPlaintext("digitalWorkbooks/evacuationLockersResults"), createTextInput("evacuation-lockers-results"),
        instructionPlaintext("digitalWorkbooks/evacuationGapsPrediction"), createTextInput("evacuation-gaps-prediction"),
        instructionPlaintext("digitalWorkbooks/evacuationGapsResults"), createTextInput("evacuation-gaps-results"))))),
    section("evacuation-door-width", "digitalWorkbooks/evacuationDoorTitle", List(
      container("digitalWorkbooks/evacuationDoorTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationLayoutProvenance"),
        instructionPlaintext("digitalWorkbooks/evacuationDoorPrediction"), createTextInput("evacuation-door-prediction"),
        EvacuationSimulationInteraction("evacuation-door-experiment", EvacuationExperiment(CreateEvacuationWorkbook.narrowDoorHall)),
        instructionPlaintext("digitalWorkbooks/evacuationDoorResults"), createTextInput("evacuation-door-results"))))),
    section("evacuation-masterplan", "digitalWorkbooks/evacuationBudgetTitle", List(
      container("digitalWorkbooks/evacuationBudgetTitle", List(
        instructionMarkdown("digitalWorkbooks/evacuationBudgetTask")) ++
        CreateEvacuationWorkbook.budgetMeasures.map { id =>
          checklist(s"digitalWorkbooks/evacuationBudget$id", s"evacuation-budget-$id")
        } ++ List(instructionPlaintext("digitalWorkbooks/evacuationBudgetJustification"), createTextInput("evacuation-budget-plan"))))),
    section("evacuation-model-audit", "digitalWorkbooks/evacuationAuditTitle", List(
      container("digitalWorkbooks/evacuationAuditTitle", List(
        instructionPlaintext("digitalWorkbooks/evacuationOrderTask"), createTextInput("evacuation-order"),
        instructionPlaintext("digitalWorkbooks/evacuationNeighbourTask"),
        EvacuationSimulationInteraction("evacuation-neighbours-experiment", EvacuationExperiment(CreateEvacuationWorkbook.diagonalRoom)),
        createTextInput("evacuation-neighbours-analysis"),
        instructionPlaintext("digitalWorkbooks/evacuationThirdFactor"), createTextInput("evacuation-third-factor"),
        instructionPlaintext("digitalWorkbooks/evacuationStumbleTask"), createTextInput("evacuation-stumble-analysis")))))
  ), User.AndreGreubel)
}
object CreateEvacuationWorkbook {
  // Narrow corridor: deterministic one-person-wide bottleneck, not a reconstruction of the PDF sports hall.
  val corridor = EvacuationFloorPlan(7, 3,
    List.fill(7)(EvacuationTile.Wall) ++
      List.fill(6)(EvacuationTile.Floor) ++ List(EvacuationTile.Exit) ++ List.fill(7)(EvacuationTile.Wall),
    Set(7, 8, 9, 10))

  // Authored comparison layouts: source PDF sports-hall geometry is not yet reproduced.
  private def room(cols: Int, rows: Int, exits: Set[(Int, Int)], walls: Set[(Int, Int)], people: Set[(Int, Int)]): EvacuationFloorPlan =
    EvacuationFloorPlan(cols, rows, (for (y <- 0 until rows; x <- 0 until cols) yield {
      if (exits((x, y))) EvacuationTile.Exit
      else if (x == 0 || x == cols - 1 || y == 0 || y == rows - 1 || walls((x, y))) EvacuationTile.Wall
      else EvacuationTile.Floor
    }).toList, people.map { case (x, y) => y * cols + x })

  val lockerHall = room(11, 9, Set((10, 4), (10, 5)),
    Set((4, 3), (6, 3), (4, 5), (6, 5)),
    Set((1, 2), (2, 2), (1, 4), (2, 4), (1, 6), (2, 6)))
  val narrowDoorHall = lockerHall.copy(tiles = lockerHall.tiles.updated(5 * 11 + 10, EvacuationTile.Wall))
  val diagonalRoom = room(7, 7, Set((5, 5)), Set.empty, Set((1, 1)))

  // Stable selection IDs; the translated labels contain the quotation's costs (PDF p. 16).
  val budgetMeasures: List[String] = List("Training", "MoveAssembly", "AddAssembly", "WidenDoor",
    "RemoveObstacles", "HalfPeople", "AddObstacles", "Custom")
}
