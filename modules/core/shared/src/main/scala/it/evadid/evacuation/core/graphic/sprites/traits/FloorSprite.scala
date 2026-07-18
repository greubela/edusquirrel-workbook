package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.evacuation.core.graphic.spritemap.FloorSpriteProperties

trait FloorSprite extends Sprite {

  val properties: FloorSpriteProperties
  val isSave: Boolean

  override val toString: String = "FS(" + id + ": " + properties.toString + ")"

}
