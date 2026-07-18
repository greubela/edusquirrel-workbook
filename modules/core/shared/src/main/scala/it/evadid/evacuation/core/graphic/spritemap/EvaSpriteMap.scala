package it.evadid.evacuation.core.graphic.spritemap

import it.evadid.core.datastructures.matrix.MatrixDimension
import it.evadid.evacuation.core.graphic.sprites.traits.{FloorSprite, OverlaySprite, PersonSprite, Sprite}
import it.evadid.evacuation.core.io.instances.eva.config.SpriteMapMetaConfig

case class EvaSpriteMap(id: SpriteMapResourceIdentifier, sprites: List[Sprite], defaultEmpty: FloorSprite, config: SpriteMapMetaConfig, selectionDim: MatrixDimension) extends SpriteMap {

  val tiles: List[FloorSprite] = sprites.filter(_.isInstanceOf[FloorSprite]).map(_.asInstanceOf[FloorSprite])

  val overlays: List[OverlaySprite] = sprites.filter(_.isInstanceOf[OverlaySprite]).map(_.asInstanceOf[OverlaySprite])

  val persons: List[PersonSprite] = sprites.filter(_.isInstanceOf[PersonSprite]).map(_.asInstanceOf[PersonSprite])

  /*def directionSprite(dir: Direction): Option[OverlaySprite] = {
    val dirSpriteName = config.directionSpriteName(dir)
    overlays.find(_.name == dirSpriteName)
  }*/

  /*def personSprites(): List[OverlaySprite] = {
    val spriteNames = config.personSpriteNames()
    overlays.filter(overlay => spriteNames.contains(overlay.name))
  }*/
  override val name: String = id.layout
  override val spriteSize: Int = id.size
}
