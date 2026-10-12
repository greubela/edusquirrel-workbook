package it.evadid.workbook.model.evacuation

import it.evadid.workbook.elements.interactionElements.evacuation.*
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite
import upickle.default.*

class EvacuationFloorPlanSpec extends FunSuite {
  test("floor bounds, rectangular data and walkable person positions are validated") {
    for ((cols, rows) <- List((0, 2), (2, 0), (41, 1), (1, 41), (Int.MaxValue, 2)))
      intercept[IllegalArgumentException](EvacuationFloorPlan(cols, rows, Nil))
    intercept[IllegalArgumentException](EvacuationFloorPlan(2, 2, List(EvacuationTile.Floor)))
    for (people <- List(Set(-1), Set(2)))
      intercept[IllegalArgumentException](EvacuationFloorPlan(2, 1, List.fill(2)(EvacuationTile.Floor), people))
    intercept[IllegalArgumentException](EvacuationFloorPlan(1, 1, List(EvacuationTile.Wall), Set(0)))
    intercept[IllegalArgumentException](EvacuationFloorPlan.empty(Int.MaxValue, 2))
    assertEquals(EvacuationFloorPlan.empty(40, 40).tiles.size, 1600)
  }
  test("requirements count actual people and exits, including boundary values") {
    val empty = EvacuationFloorPlan.empty(2, 1)
    val complete = empty.copy(tiles = List(EvacuationTile.Exit, EvacuationTile.Floor), people = Set(1))
    assert(!EvacuationFloorRequirements().isSatisfiedBy(empty))
    assert(!EvacuationFloorRequirements().isSatisfiedBy(complete.copy(people = Set.empty)))
    assert(!EvacuationFloorRequirements().isSatisfiedBy(complete.copy(tiles = empty.tiles)))
    assert(EvacuationFloorRequirements().isSatisfiedBy(complete))
    assert(!EvacuationFloorRequirements(minPeople = 2).isSatisfiedBy(complete))
    assert(EvacuationFloorRequirements(0, 0).isSatisfiedBy(empty))
    intercept[IllegalArgumentException](EvacuationFloorRequirements(-1, 0))
    intercept[IllegalArgumentException](EvacuationFloorRequirements(0, -1))
  }
  test("saved answers round-trip and malformed persisted data is rejected") {
    val floor = EvacuationFloorPlan(2, 2, List(EvacuationTile.Floor, EvacuationTile.Wall, EvacuationTile.Exit, EvacuationTile.Floor), Set(0, 2))
    val interaction = EvacuationConstructFloorInteraction("floor", floor)
    assertEquals(interaction.serializerInteractionContent.deserialize(interaction.serializerInteractionContent.serialize(floor)), floor)
    assert(interaction.isPassed)
    val bad = writeJs(floor)
    bad("people") = ujson.Arr(1)
    assert(scala.util.Try(read[EvacuationFloorPlan](bad)).isFailure)
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(interaction)), interaction)
  }
}
