package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.evacuation.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class CreateEvacuationWorkbookSpec extends FunSuite {
  test("partial evacuation workbook includes independent construction, simulation and reflection tasks") {
    val workbook = CreateEvacuationWorkbook(null).createWorkbook
    assertEquals(workbook.sections.size, 10)
    val elements = workbook.allChildrenFullSubtree
    assertEquals(elements.collect { case e: EvacuationSimulationInteraction => e }.size, 4)
    assertEquals(elements.collect { case e: EvacuationConstructFloorInteraction => e }.size, 1)
    assert(elements.exists(_.elementId == "evacuation-time-derivation"))
    assert(elements.exists(_.elementId == "evacuation-model-limits"))
    assertEquals(elements.map(_.elementId).distinct.size, elements.size)
  }
  test("source comparison activities keep predictions separate from results and use independent saved experiments") {
    val workbook = CreateEvacuationWorkbook(null).createWorkbook
    val elements = workbook.allChildrenFullSubtree
    val simulations = elements.collect { case e: EvacuationSimulationInteraction => e }
    assertEquals(simulations.map(_.elementId).toSet, Set("evacuation-layout-experiment", "evacuation-lockers-experiment",
      "evacuation-door-experiment", "evacuation-neighbours-experiment"))
    for (id <- List("school-proposals", "lockers-prediction", "lockers-results", "gaps-prediction", "gaps-results",
        "door-prediction", "door-results", "budget-plan", "order", "neighbours-analysis", "third-factor", "stumble-analysis"))
      assert(elements.exists(_.elementId == s"evacuation-$id"), s"Missing activity: $id")
    val checkboxes = elements.collect {
      case c: it.evadid.workbook.elements.interactionElements.basic.LabeledCheckboxInteraction => c
    }
    assertEquals(checkboxes.map(_.elementId).toSet,
      CreateEvacuationWorkbook.budgetMeasures.map { id => s"evacuation-budget-$id" }.toSet)
    assert(checkboxes.forall(!_.defaultValue))
    assert(simulations.forall(_.defaultValue.measurements.isEmpty))
  }

  test("comparison layouts preserve people and change only the intended door cell") {
    import it.evadid.workbook.model.evacuation.EvacuationTile
    val hall = CreateEvacuationWorkbook.lockerHall
    val door = CreateEvacuationWorkbook.narrowDoorHall
    assertEquals(hall.people.size, 6)
    assertEquals(hall.people, door.people)
    assertEquals(hall.exitCount, 2)
    assertEquals(door.exitCount, 1)
    assertEquals(hall.tiles.zip(door.tiles).zipWithIndex.collect {
      case ((a, b), i) if a != b => i
    }, List(5 * 11 + 10))
    for ((x, y) <- List((4, 3), (6, 3), (4, 5), (6, 5)))
      assertEquals(hall.tiles(y * hall.cols + x), EvacuationTile.Wall)
    for (y <- List(3, 5)) assertEquals(hall.tiles(y * hall.cols + 5), EvacuationTile.Floor)
  }

  test("entire evacuation workbook round-trips through both definition formats") {
    val workbook = CreateEvacuationWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
  }
}
