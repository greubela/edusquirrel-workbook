package it.evadid.evacuation.eva2.control.floorMaps

import it.evadid.evacuation.core.datastructures.matrix.{Matrix, PositionInMatrix}
import it.evadid.evacuation.core.graphic.spritemap.SpriteMap
import it.evadid.evacuation.core.graphic.sprites.traits.{OverlaySprite, Sprite}
import it.evadid.evacuation.core.utility.Timer
import it.evadid.evacuation.eva2.configuration.ui.PersonDrawingInformation
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.graphic.ImageConfigFactory
import it.evadid.evacuation.eva2.model.Person
import it.evadid.evacuation.html.HtmlHelper
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.Element


class ControllableHtmlFloorMap(spriteMap: SpriteMap, matrix: Matrix[Sprite], pTileMapController: TileMapController) extends FloorMap {

  private case class TileDrawingInfo(sprite: Sprite, pim: PositionInMatrix, overlays: List[Sprite])

  private val tileCache: collection.mutable.HashMap[TileDrawingInfo, Element] = new collection.mutable.HashMap()

  val floorMapElement: Element = {
    val div = document.createElement("div")
    div.setAttribute("id", "floor-map-container")
    div
  }

  private def createContentTable: Element = {
    val table = document.createElement("div")
    table.setAttribute("id", "content-table")
    table.setAttribute("class", "manualCols")
    table.addEventListener("mouseleave", { (e: dom.MouseEvent) => handleMouseExitedFloorMap(e) })
    table
  }

  override def redraw(overlays: List[(PositionInMatrix, OverlaySprite)], persons: Set[Person], drawingInfo: Map[Int, PersonDrawingInformation]): Unit = {

    val t = new Timer()

    HtmlHelper.clearChildrenFromElement(floorMapElement)
    t.addPoint("cleared children")

    val tiles = createContentTiles(overlays)
    t.addPoint("created content tiles")

    val contentTable = createContentTable
    t.addPoint("created content table")

    tiles.foreach(tile => contentTable.appendChild(tile))
    t.addPoint("appended tiles")

    floorMapElement.appendChild(contentTable)
    t.addPoint("appended content table")


    if (persons.nonEmpty) {
      //  ToDo: Draw Persons
      println("[WARN] ControllableHtmlFloorMap: not drawing persons!")
      ???
    }

  }

  redraw(List(), Set(), Map())

  private def createContentTiles(overlays: List[(PositionInMatrix, Sprite)]): List[Element] = matrix.elementsAtPosition.map(tup => {
    val pim = tup._2
    val curOverlays = overlays.filter(_._1.cPos == pim.cPos).map(_._2)
    val info = TileDrawingInfo(tup._1, pim, curOverlays)
    getEncodedTile(info)
  }).toList


  private def getEncodedTile(tileDrawingInfo: TileDrawingInfo): Element = {
    if (tileCache.contains(tileDrawingInfo)) {
      tileCache(tileDrawingInfo)
    } else {
      val encoded = encodeTile(tileDrawingInfo)
      tileCache.put(tileDrawingInfo, encoded)
      encoded
    }
  }


  private def enableDragAndDrop(tile: Element, tileDrawingInfo: TileDrawingInfo): Unit = {
    /*
        tile.setAttribute("draggable", "true")

        tile.addEventListener("dragstart", (e: dom.DragEvent) => {
          tileMapController.onTileDragStarted(e, tileDrawingInfo.pim)
          e.dataTransfer.setData("tile", tileDrawingInfo.pim.cPos.toString)
        })

        tile.addEventListener("dragover", (e:dom.DragEvent) => {
          tileMapController.onTileDragOver(e, tileDrawingInfo.pim)
          e.preventDefault()
        })

        tile.addEventListener("dragend", (e: dom.DragEvent) => {
          tileMapController.onTileDragEnded(e)
        })

        tile.addEventListener("drop", (e: dom.DragEvent) => {
          tileMapController.onTileDragDropped(e, tileDrawingInfo.pim)
          e.preventDefault()
        })
    */
  }


  private def encodeTile(tileDrawingInfo: TileDrawingInfo): Element = {
    val tile = document.createElement("div")

    tile.setAttribute("id", "gridTileElement")

    tile.addEventListener("mouseover", { (e: dom.MouseEvent) =>
      handleMouseEnteredTile(e, tileDrawingInfo.pim)
    })
    tile.addEventListener("click", { (e: dom.MouseEvent) => handleMouseClicked(e, tileDrawingInfo.pim) })

    enableDragAndDrop(tile, tileDrawingInfo)


    tile.appendChild(ImageConfigFactory.firstFrameForTile(tileDrawingInfo.sprite, spriteMap).createImage())

    tileDrawingInfo.overlays.zipWithIndex.map(tup => ImageConfigFactory.forOverlay(tup._1, spriteMap, 100 + tup._2).createImage())
      .foreach(tile.appendChild(_))

    tile
  }


  private var lastMouseOverPos: Option[PositionInMatrix] = Option.empty

  private def handleMouseEnteredTile(e: dom.MouseEvent, curPos: PositionInMatrix): Unit = {
    val oldPos = lastMouseOverPos

    if (oldPos.isEmpty) {
      lastMouseOverPos = Some(curPos)
      tileMapController.onMouseEnteringTileMap(curPos)
    }
    else if (oldPos.isDefined && oldPos.get != curPos) {
      lastMouseOverPos = Some(curPos)
      tileMapController.onMouseSwitchingTile(oldPos.get, curPos)
    }
  }

  private def handleMouseExitedFloorMap(e: dom.MouseEvent): Unit = {
    val oldPos = lastMouseOverPos

    if (oldPos.isDefined) {
      lastMouseOverPos = None
      tileMapController.onMouseLeavingTileMap(oldPos.get)
    }
  }

  private def handleMouseClicked(e: dom.MouseEvent, curPos: PositionInMatrix): Unit = {
    tileMapController.onMouseClickingOnTile(curPos)
  }

  override def tileMapController: TileMapController = pTileMapController
}
