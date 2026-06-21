package it.evadid.evacuation.eva2.control.traits

import it.evadid.evacuation.core.datastructures.matrix.PositionInMatrix

trait SimpleTileMapController {

  def onOver(pos: PositionInMatrix): Unit

  def onClick( pos: PositionInMatrix): Unit

  def onLeaving(): Unit

  def onDragAndDrop(startTile: PositionInMatrix, endTile: PositionInMatrix, draggedOver: Set[PositionInMatrix]): Unit

}
