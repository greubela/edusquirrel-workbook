package it.evadid.evacuation.eva2.control.modes

import it.evadid.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.spritemap.SpriteMapResourceIdentifier
import it.evadid.evacuation.core.graphic.sprites.traits.OverlaySprite
import it.evadid.evacuation.eva2.control.Eva2ControlMode
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.model.ProgramState
import it.evadid.evacuation.html.EvaHtmlFactory
import org.scalajs.dom.{Element, document}

import scala.concurrent.ExecutionContextExecutor

case class ChangeConfigurationMode() extends Eva2ControlMode {

  private implicit val context: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  override def onEnteringMode(): Unit = {}

  override def onLeavingMode(): Unit = {}

  override def getOverlays(): List[(PositionInMatrix, OverlaySprite)] = List()

  override def getControlElement: Element = {

    val control = document.createElement("div")
    control.setAttribute("id", "configuration-control")

    control.appendChild(EvaHtmlFactory.createRadioButtonForm(ProgramState.config.neighbourhood))
    control.appendChild(EvaHtmlFactory.createRadioButtonForm(ProgramState.config.strategy))

    control.appendChild(EvaHtmlFactory.createPropertyTickBox(ProgramState.config.showAnimations))

    val func: SpriteMapResourceIdentifier => Boolean = _.layout == ProgramState.graphicConfig.spriteMapProperty.getValue.value.layout
    println("cur Val:" + ProgramState.graphicConfig.spriteMapProperty.getValue.value)
    control.appendChild(EvaHtmlFactory.createRadioButtonForm(ProgramState.graphicConfig.spriteMapProperty, func))
    control

  }

  override def mainAreaTileMapController: TileMapController = TileMapController.noopController

}
