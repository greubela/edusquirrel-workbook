package it.evadid.evacuation.eva2.control.traits

import it.evadid.evacuation.core.datastructures.matrix.PositionInMatrix
import org.scalajs.dom.MouseEvent

import scala.collection.immutable.HashSet

trait TileMapController {

  def onMouseEnteringTileMap(onTile: PositionInMatrix): Unit

  def onMouseSwitchingTile(oldTile: PositionInMatrix, newTile: PositionInMatrix): Unit

  def onMouseLeavingTileMap(lastTile: PositionInMatrix): Unit

  def onMouseClickingOnTile(onTile: PositionInMatrix): Unit
/*
  def onTileDragStarted(dragEvent: DragEvent, onTile: PositionInMatrix)

  def onTileDragOver(dragEvent: DragEvent, onTile: PositionInMatrix)

  def onTileDragEnded(dragEvent: DragEvent)

  def onTileDragDropped(dragEvent: DragEvent, onTile: PositionInMatrix)
*/

}

object TileMapController {

  val noopController = new TileMapController {
    override def onMouseEnteringTileMap(onTile: PositionInMatrix): Unit = {}

    override def onMouseSwitchingTile(oldTile: PositionInMatrix, newTile: PositionInMatrix): Unit = {}

    override def onMouseLeavingTileMap(lastTile: PositionInMatrix): Unit = {}

    override def onMouseClickingOnTile(onTile: PositionInMatrix): Unit = {}
/*
    override def onTileDragStarted(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = {}

    override def onTileDragDropped(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = {}

    override def onTileDragOver(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = {}

    override def onTileDragEnded(dragEvent: DragEvent): Unit = {}*/
  }

  val loggingController: TileMapController = new TileMapController {
    override def onMouseEnteringTileMap(onTile: PositionInMatrix): Unit = println("entering on: " + onTile)

    override def onMouseSwitchingTile(oldTile: PositionInMatrix, newTile: PositionInMatrix): Unit = println("moved: " + oldTile + " --> " + newTile)

    override def onMouseLeavingTileMap(lastTile: PositionInMatrix): Unit = println("leaving from: " + lastTile)

    override def onMouseClickingOnTile(onTile: PositionInMatrix): Unit = println("clicked on: " + onTile)
/*
    override def onTileDragStarted(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = println("drag started on: " + onTile)

    override def onTileDragDropped(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = println("drag dropped on: " + onTile)

    override def onTileDragOver(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = println("drag over on: " + onTile)

    override def onTileDragEnded(dragEvent: DragEvent): Unit = println("drag ended!")*/
  }


  def from(simpleTileMapController: SimpleTileMapController): TileMapController = new TileMapController {

    var lastDragStarted: Option[PositionInMatrix] = None
    var draggedOver: Set[PositionInMatrix] = HashSet[PositionInMatrix]()

    override def onMouseEnteringTileMap(onTile: PositionInMatrix): Unit = simpleTileMapController.onOver(onTile)

    override def onMouseSwitchingTile(oldTile: PositionInMatrix, newTile: PositionInMatrix): Unit = simpleTileMapController.onOver(newTile)

    override def onMouseLeavingTileMap(lastTile: PositionInMatrix): Unit = simpleTileMapController.onLeaving()

    override def onMouseClickingOnTile( onTile: PositionInMatrix): Unit = simpleTileMapController.onClick(onTile)
/*
    override def onTileDragStarted(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = {
      lastDragStarted = Some(onTile)
    }

    override def onTileDragDropped(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = {
      // TODO: Separate Drag and Drop Controller
      simpleTileMapController.onDragAndDrop(dragEvent, lastDragStarted.get, onTile, draggedOver)
    }

    override def onTileDragOver(dragEvent: DragEvent, onTile: PositionInMatrix): Unit = {
      draggedOver = draggedOver + onTile
    }

    override def onTileDragEnded(dragEvent: DragEvent): Unit = {
      lastDragStarted = None
      draggedOver = HashSet[PositionInMatrix]()
    }*/
  }

}
