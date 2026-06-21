package it.evadid.evacuation.eva2.control

import it.evadid.evacuation.control.EvaControl
import it.evadid.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.spritemap.SpriteMapResourceIdentifier
import it.evadid.evacuation.core.graphic.sprites.traits.{OverlaySprite, Sprite}
import it.evadid.evacuation.core.utility.BufferedExecution
import it.evadid.evacuation.eva2.control.floorMaps.{ControllableCanvasFloorMap, FloorMap}
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.model.ProgramState
import it.evadid.evacuation.eva2.model.ProgramState._
import it.evadid.evacuation.html.HtmlHelper
import org.scalajs.dom.{Element, document, window}

object Eva2Control extends EvaControl[Eva2ControlMode] {


  private var ctrlFloorMap: FloorMap = null
  val redrawingBuffer: BufferedExecution = new BufferedExecution(() => redrawTiles(), 100, 10)

  def adjustSpriteSize(): Unit = {

    val sameSpritemaps = SpriteMapResourceIdentifier.availableSpriteMaps.filter(id => id.layout == ProgramState.spriteMap.id.layout)
    val dest: Option[SpriteMapResourceIdentifier] = sameSpritemaps.filter(_.size * floorMatrix.dim.cols < window.innerWidth * 0.7).filter(_.size * floorMatrix.dim.rows < window.innerHeight * 0.8).maxByOption(_.size)

    if(dest.isDefined && dest.get != ProgramState.spriteMap.id){
      ProgramState.instance.cache.loadSpriteMap(dest.get)
      /*val newSpriteOp = SpriteMapResourceLoader.loadWithSize(spriteMap, destSize.get)
      newSpriteOp.foreach(_.foreach(newSpriteMap => ProgramState.instance.spriteMap.setValue(newSpriteMap)))*/
    }

  }


  def requestRedrawTiles(): Unit = {
    redrawingBuffer.requestExecution()
  }

  private def redrawTiles(): Unit = {

    val timeStart = System.currentTimeMillis()
    if (ctrlFloorMap == null) {
      // assert(ctrlFloorMap != null, "Error: Called ctrlFloorMap before initialization!!")
      redrawMainArea()
    } else {
      //  val overlaysPersons = ProgramState.persons.map(person => (person.pos, person.sprite)).toList
      val overlaysControl = controlMode.getOverlays().filter(_._1.isInRange)

      ctrlFloorMap.redraw(overlaysControl, ProgramState.persons, controlMode.getDrawingInformation())

    }
    val diff = (System.currentTimeMillis() - timeStart) / 1000.0
    println("[Eva2Control::redrawTiles()] finished drawing in " + diff + "s!")

  }

  def redrawMainArea(): Unit = {

    //ctrlFloorMap = new ControllableHtmlFloorMap(spriteMap, floorMatrix.mapTiles(_.asInstanceOf[Sprite]), controlMode.mainAreaTileMapController)

    redrawTiles()
  }

  override def getNoopMode(): Eva2ControlMode = new Eva2ControlMode {
    override def mainAreaTileMapController: TileMapController = TileMapController.noopController

    override def onEnteringMode(): Unit = {}

    override def onLeavingMode(): Unit = {}

    override def getOverlays(): List[(PositionInMatrix, OverlaySprite)] = List()

    override def getControlElement: Element = document.createElement("div")
  }

  override def reloadMainArea(): Unit = {
    HtmlHelper.clearChildrenFromId("eva-main")

    ctrlFloorMap = new ControllableCanvasFloorMap(spriteMap, floorMatrix.mapTiles(_.asInstanceOf[Sprite]), controlMode.mainAreaTileMapController)
    val mainDiv: Element = document.getElementById("eva-main")
    mainDiv.appendChild(ctrlFloorMap.floorMapElement)

    redrawMainArea()
  }
}
