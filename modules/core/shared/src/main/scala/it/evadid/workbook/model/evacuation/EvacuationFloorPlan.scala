package it.evadid.workbook.model.evacuation

import upickle.default.*

/** Browser-independent answer format. Rendering and simulation use the existing EVA2 floor model. */
enum EvacuationTile derives ReadWriter {
  case Floor, Wall, Exit
}
case class EvacuationFloorPlan(cols: Int, rows: Int, tiles: List[EvacuationTile], people: Set[Int] = Set.empty) derives ReadWriter {
  require(cols >= 1 && cols <= EvacuationFloorPlan.maxDimension && rows >= 1 && rows <= EvacuationFloorPlan.maxDimension,
    "Floor dimensions must be between 1 and 40")
  require(tiles.size == cols * rows, "The floor must have one tile per cell")
  require(people.forall(i => i >= 0 && i < tiles.size && tiles(i) != EvacuationTile.Wall),
    "People must occupy distinct walkable cells")
  def exitCount: Int = tiles.count(_ == EvacuationTile.Exit)
}
object EvacuationFloorPlan {
  val maxDimension = 40
  def empty(cols: Int = 8, rows: Int = 6): EvacuationFloorPlan = {
    require(cols >= 1 && cols <= maxDimension && rows >= 1 && rows <= maxDimension,
      "Floor dimensions must be between 1 and 40")
    EvacuationFloorPlan(cols, rows, List.fill(cols * rows)(EvacuationTile.Floor))
  }
}
