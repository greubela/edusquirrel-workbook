package it.evadid.evacuation.eva2.control

import it.evadid.evacuation.control.EvaControlMode
import it.evadid.evacuation.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.sprites.traits.OverlaySprite
import it.evadid.evacuation.eva2.control.traits.TileMapController

abstract class EvaControlModeWithSubmodes extends Eva2ControlMode {

  var currentMode: Eva2ControlMode = controlModes.head

  def switchMode(newMode: Eva2ControlMode): Unit = {

  }

  override def onEnteringMode(): Unit = {
    controlModes.foreach(_.onEnteringMode())
  }

  override def onLeavingMode(): Unit = {
    controlModes.foreach(_.onLeavingMode())
  }

  override def getOverlays(): List[(PositionInMatrix, OverlaySprite)] = {
    currentMode.getOverlays()
  }

  override def mainAreaTileMapController: TileMapController = ???


  val controlModes: List[Eva2ControlMode]


}
