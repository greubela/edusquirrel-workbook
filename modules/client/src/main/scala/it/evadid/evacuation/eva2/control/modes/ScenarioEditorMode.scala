package it.evadid.evacuation.eva2.control.modes

import it.evadid.evacuation.core.datastructures.matrix.{Matrix, PositionInMatrix}
import it.evadid.evacuation.core.graphic.sprites.BasicOverlaySprite
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, OverlaySprite, PersonSprite, Sprite}
import it.evadid.evacuation.eva2.control._
import it.evadid.evacuation.eva2.control.floorMaps.{ControllableHtmlFloorMap, FloorMap}
import it.evadid.evacuation.eva2.control.traits.{SimpleTileMapController, TileMapController}
import it.evadid.evacuation.eva2.graphic.ImageConfigFactory
import it.evadid.evacuation.eva2.model
import it.evadid.evacuation.eva2.model.ProgramState._
import it.evadid.evacuation.eva2.model.{EvaFloorMap, ProgramState}
import org.scalajs.dom.{Element, document}

case class ScenarioEditorMode() extends Eva2ControlMode {

  private case class SelectionSpriteArea(selectionSprites: Seq[Sprite]) {
    val selectionMatrix: Matrix[Sprite] = Matrix(spriteMap.selectionDim, selectionSprites)
    val selectionFloorMap: FloorMap = new ControllableHtmlFloorMap(spriteMap, selectionMatrix, selectionTileMapController)
    //val selectionFloorMap: FloorMap = new ControllableCanvasFloorMap(spriteMap, selectionMatrix, selectionTileMapController)
  }

  private var selectionArea: SelectionSpriteArea = null

  private var overlays: List[(PositionInMatrix, OverlaySprite)] = List()

  override def onEnteringMode(): Unit = {
    println("entered scenario editor mode!")
  }

  override def onLeavingMode(): Unit = {
    println("leaving scenario editor mode!")
    overlays = List()
    selectedTile = Option.empty
  }

  override def getOverlays(): List[(PositionInMatrix, OverlaySprite)] = {
    overlays.toList
  }

  def getControlElement: Element = {
    val tileSize = ProgramState.spriteMap.spriteSize

    val tile = document.createElement("div")
    tile.setAttribute("id", "map-editor-control")

    tile.appendChild(createAddControl(tileSize))
    tile.appendChild(createRemoveControl(tileSize))

    selectionArea = SelectionSpriteArea(spriteMap.selectionDim.positions.map(pos => spriteMap.config.selectorSpriteAtPosition(pos, spriteMap)))

    tile.appendChild(selectionArea.selectionFloorMap.floorMapElement)
    tile
  }


  private var selectedTile: Option[Sprite] = Option.empty

  def mainAreaTileMapController2: TileMapController = TileMapController.loggingController

  def mainAreaTileMapController: TileMapController = TileMapController.from(new SimpleTileMapController {

    override def onOver(pos: PositionInMatrix): Unit = {
      overlays = if (selectedTile.isEmpty) List((pos, BasicOverlaySprite.whiteOverlay))
      else List((pos, BasicOverlaySprite.whiteOverlay), (pos, OverlaySprite.fromSprite(selectedTile.get)))
      Eva2Control.requestRedrawTiles()
    }

    override def onClick(pos: PositionInMatrix): Unit = selectedTile match {
      case None => println("Clicked without selection!")
      case Some(sprite: FloorSprite) =>
        val newState = EvaFloorMap(floorMatrix.replace(pos, sprite), persons)
        ProgramState.instance.floorMap.setValue(newState)
      case Some(sprite: PersonSprite) => {
        personAddedRequest(pos, sprite)
      }
      case Some(sprite: Sprite) => throw new UnsupportedOperationException("Cannot handly sprite type " + sprite.getClass + " in ScenarioEditorMode Clicking!")

    }

    override def onLeaving(): Unit = {
      overlays = List()
      Eva2Control.requestRedrawTiles()
    }

    override def onDragAndDrop(startTile: PositionInMatrix, endTile: PositionInMatrix, draggedOver: Set[PositionInMatrix]): Unit = {

    }
  })

  private def personAddedRequest(pos: PositionInMatrix, sprite: PersonSprite): Unit = {
    val existingAtPos = ProgramState.persons.find(_.pos == pos)
    // Same sprite to existing location: Remove
    val newState =
      if (existingAtPos.nonEmpty && existingAtPos.get.sprite == sprite) {
        model.EvaFloorMap(ProgramState.floorMatrix, persons.filter(_.pos != pos))
      }
      // Different sprite or existing location: Replace // Add new
      else {
        ProgramState.instance.floorMap.currentValue.insertOrSetPersonAtPosition(pos, sprite)
      }

    ProgramState.instance.floorMap.setValue(newState)
  }

  private def selectionTileMapController: TileMapController = TileMapController.from(new SimpleTileMapController {

    override def onOver(pos: PositionInMatrix): Unit = {
      selectionArea.selectionFloorMap.redraw(List((pos, BasicOverlaySprite.whiteOverlay)), Set(), Map())
    }

    override def onClick(pos: PositionInMatrix): Unit = {
      assert(selectionArea != null, "clicked on selection tile before selection Matrix was inited!")
      selectedTile = selectionArea.selectionMatrix.get(pos.cPos)
    }

    override def onLeaving(): Unit = {
      selectionArea.selectionFloorMap.redraw(List(), Set(), Map())
    }

    override def onDragAndDrop(startTile: PositionInMatrix, endTile: PositionInMatrix, draggedOver: Set[PositionInMatrix]): Unit = {
    }
  })


  private def handleExtend(top: Boolean, left: Boolean, bottom: Boolean, right: Boolean): Unit = {
    val updated = ProgramState.instance.floorMap.currentValue.extendMatrix(top, left, bottom, right)
    ProgramState.instance.floorMap.setValue(updated)
  }

  private def handleShrink(top: Boolean, left: Boolean, bottom: Boolean, right: Boolean): Unit = {
    val updated = ProgramState.instance.floorMap.currentValue.shrinkMatrix(top, left, bottom, right)
    ProgramState.instance.floorMap.setValue(updated)
  }


  private def createAddControl(tileSize: Int): Element = {

    val control = document.createElement("div")
    control.setAttribute("id", "map-editor-control-a")
    control.setAttribute("class", "inner-map-editor-control")

    val addControl = control

    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionTL", "ArrowTL", _ => handleExtend(true, true, false, false)).createImage())
    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionT", "ArrowT", _ => handleExtend(true, false, false, false)).createImage())
    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionTR", "ArrowTR", _ => handleExtend(true, false, false, true)).createImage())

    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionL", "ArrowL", _ => handleExtend(false, true, false, false)).createImage())
    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionEP", "Plus", _ => handleExtend(true, true, true, true)).createImage())
    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionR", "ArrowR", _ => handleExtend(false, false, false, true)).createImage())

    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionBL", "ArrowBL", e => handleExtend(false, true, true, false)).createImage())
    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionB", "ArrowB", e => handleExtend(false, false, true, false)).createImage())
    addControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionBR", "ArrowBR", e => handleExtend(false, false, true, true)).createImage())

    addControl
  }

  private def createRemoveControl(tileSize: Int): Element = {


    val control = document.createElement("div")
    control.setAttribute("id", "map-editor-control-b")
    control.setAttribute("class", "inner-map-editor-control")

    val remControl = control

    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionTL", "ArrowBR", e => handleShrink(true, true, false, false)).createImage())
    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionB", "ArrowB", e => handleShrink(true, false, false, false)).createImage())
    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionTR", "ArrowBL", e => handleShrink(true, false, false, true)).createImage())

    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionL", "ArrowR", e => handleShrink(false, true, false, false)).createImage())
    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionEM", "Minus", e => handleShrink(true, true, true, true)).createImage())
    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionR", "ArrowL", e => handleShrink(false, false, false, true)).createImage())

    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionBL", "ArrowTR", e => handleShrink(false, true, true, false)).createImage())
    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionB", "ArrowT", e => handleShrink(false, false, true, false)).createImage())
    remControl.appendChild(ImageConfigFactory.forExtensionButton("ExtensionBR", "ArrowTL", e => handleShrink(false, false, true, true)).createImage())

    remControl
  }

}
