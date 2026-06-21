package it.evadid.evacuation.eva2.control.floorMaps

import it.evadid.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.core.graphic.sprites.traits.{OverlaySprite, Sprite}
import it.evadid.evacuation.eva2.configuration.ui.PersonDrawingInformation
import it.evadid.evacuation.eva2.control.traits.TileMapController
import it.evadid.evacuation.eva2.model.Person
import org.scalajs.dom.Element

trait FloorMap {


  def tileMapController: TileMapController

  def floorMapElement: Element

  def redraw(overlaySprites: List[(PositionInMatrix, OverlaySprite)], persons: Set[Person], personInformation: Map[Int, PersonDrawingInformation]): Unit


}

object FloorMap {

  private case class TileDrawingInfo(sprite: Sprite, pim: PositionInMatrix, overlays: List[Sprite])

}