package it.evadid.homepage.webElements.editor.evacuation

import it.evadid.core.datastructures.matrix.*
import it.evadid.evacuation.eva2.control.modes.ScenarioEditorMode
import it.evadid.workbook.model.evacuation.*
import munit.FunSuite

class EvacuationScenarioSpec extends FunSuite {
  private val adapter = EvacuationFloorAdapter
  test("answer adapter preserves mixed tiles, persons and dimensions") {
    val plan = EvacuationFloorPlan(3, 2, List(EvacuationTile.Floor, EvacuationTile.Wall, EvacuationTile.Exit,
      EvacuationTile.Exit, EvacuationTile.Floor, EvacuationTile.Floor), Set(0, 2, 4))
    val map = adapter.decode(plan)
    assertEquals(adapter.encode(map), plan)
    assertEquals(map.persons.map(_.id), Set(0, 1, 2))
    assert(map.floorMatrix.get(MatrixPosition(1, 0)).get.properties.isFullyClosed())
    assert(map.floorMatrix.get(MatrixPosition(2, 0)).get.isSave)
    assertEquals(adapter.encode(adapter.decode(EvacuationFloorPlan.empty(1, 1))), EvacuationFloorPlan.empty(1, 1))
  }
  test("injected EVA2 mode edits only its own floor, toggles persons and paints walls") {
    var first = adapter.decode(EvacuationFloorPlan.empty(2, 2))
    val other = adapter.decode(EvacuationFloorPlan.empty(2, 2))
    val mode = ScenarioEditorMode(() => first, updated => first = adapter.decode(adapter.encode(updated)),
      () => adapter.sprites, () => ())
    val pos = first.floorMatrix.dim.positions.head
    def click(): Unit = mode.mainAreaTileMapController.onMouseClickingOnTile(pos)
    mode.selectSprite(adapter.person); click()
    assertEquals(adapter.encode(first).people, Set(0))
    click(); assertEquals(first.persons.size, 0)
    click(); mode.selectSprite(adapter.wall); click()
    assertEquals(first.persons.size, 0)
    assertEquals(adapter.encode(first).tiles.head, EvacuationTile.Wall)
    mode.selectSprite(adapter.exit); click()
    assertEquals(adapter.encode(first).exitCount, 1)
    assertEquals(adapter.encode(other), EvacuationFloorPlan.empty(2, 2))
    mode.onLeavingMode(); click()
    assertEquals(adapter.encode(first).exitCount, 1)
  }
  test("locked injected mode refuses painting and resizing, and the dimension limit is enforced") {
    var floor = adapter.decode(EvacuationFloorPlan.empty(2, 2))
    var locked = true
    val mode = ScenarioEditorMode(() => floor, updated => floor = updated, () => adapter.sprites,
      () => (), () => !locked, Some(3))
    mode.selectSprite(adapter.exit)
    mode.mainAreaTileMapController.onMouseClickingOnTile(floor.floorMatrix.dim.positions.head)
    mode.handleExtend(true, true, false, false)
    mode.handleShrink(true, true, false, false)
    assertEquals(adapter.encode(floor), EvacuationFloorPlan.empty(2, 2))
    locked = false
    mode.handleExtend(false, false, true, true)
    assertEquals(floor.floorMatrix.dim, MatrixDimension(3, 3))
    mode.handleExtend(false, false, true, true)
    assertEquals(floor.floorMatrix.dim, MatrixDimension(3, 3))
  }
  test("extending top and left translates people and uses the injected empty tile") {
    val floor = adapter.decode(EvacuationFloorPlan.empty(2, 2).copy(people = Set(0, 3)))
    val bigger = floor.extendMatrix(true, true, false, false, adapter.floor)
    assertEquals(adapter.encode(bigger).people, Set(4, 8))
    assertEquals(bigger.shrinkMatrix(true, true, false, false), floor)
  }
  test("shrinking two rows and columns to one shifts survivors and removes cropped people") {
    val floor = adapter.decode(EvacuationFloorPlan.empty(2, 2).copy(people = Set(0, 1, 2, 3)))
    val smaller = floor.shrinkMatrix(true, true, false, false)
    assertEquals(smaller.persons.size, 1)
    assertEquals(smaller.persons.head.pos.cPos, MatrixPosition(0, 0))
    assertEquals(smaller.persons.head.id, floor.persons.find(_.pos.cPos == MatrixPosition(1, 1)).get.id)
    assertEquals(smaller.shrinkMatrix(true, true, true, true), smaller)
  }
  test("removing bottom and right drops occupants without moving remaining people") {
    val floor = adapter.decode(EvacuationFloorPlan.empty(2, 2).copy(people = Set(0, 3)))
    val smaller = floor.shrinkMatrix(false, false, true, true)
    assertEquals(adapter.encode(smaller).people, Set(0))
  }
  test("injected hover redraws locally and stale or out-of-range clicks are ignored") {
    var floor = adapter.decode(EvacuationFloorPlan.empty(2, 2))
    var redraws = 0
    val mode = ScenarioEditorMode(() => floor, updated => floor = updated, () => adapter.sprites,
      () => redraws += 1)
    val oldPos = floor.floorMatrix.dim.positions.head
    val controller = mode.mainAreaTileMapController
    controller.onMouseEnteringTileMap(oldPos)
    assertEquals(redraws, 1)
    assertEquals(mode.getOverlays().size, 1)
    controller.onMouseLeavingTileMap(oldPos)
    assertEquals(redraws, 2)
    assertEquals(mode.getOverlays().size, 0)
    mode.selectSprite(adapter.exit)
    mode.handleExtend(false, false, true, false)
    controller.onMouseClickingOnTile(oldPos)
    controller.onMouseClickingOnTile(MatrixPosition(-1, 0).in(floor.floorMatrix.dim))
    assertEquals(adapter.encode(floor).exitCount, 0)
  }
  test("adapter rejects unsupported sprites, invalid positions and wrapped floors") {
    val map = adapter.decode(EvacuationFloorPlan.empty(2, 2))
    intercept[IllegalArgumentException](adapter.encode(map.copy(floorMatrix =
      map.floorMatrix.replace(MatrixPosition(0, 0), adapter.floor.copy(name = "unsupported")))))
    val outside = it.evadid.evacuation.eva2.model.Person(0, MatrixPosition(-1, 0).in(map.floorMatrix.dim), adapter.person)
    intercept[IllegalArgumentException](adapter.encode(map.copy(persons = Set(outside))))
    intercept[IllegalArgumentException](adapter.encode(map.copy(floorMatrix =
      Matrix(MatrixDimension(2, 2, true), map.floorMatrix.elements))))
  }

}
