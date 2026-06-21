package it.evadid.evacuation.eva2.control

import it.evadid.evacuation.control.EvaControlMode
import it.evadid.evacuation.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.sprites.traits.OverlaySprite
import it.evadid.evacuation.eva2.configuration.ui.PersonDrawingInformation
import it.evadid.evacuation.eva2.control.traits.TileMapController

trait Eva2ControlMode extends EvaControlMode {

  def mainAreaTileMapController: TileMapController

  def getOverlays(): List[(PositionInMatrix, OverlaySprite)]

  def getDrawingInformation(): Map[Int, PersonDrawingInformation] = Map()

}
