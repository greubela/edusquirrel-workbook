package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.evacuation.core.graphic.sprites.BasicOverlaySprite

trait OverlaySprite extends Sprite {

  val opacityUpTo255: Int

}


object OverlaySprite {

  def fromSprite(sprite: Sprite, animationFrame: Int = 0, opacity: Int = 200): OverlaySprite = sprite match {
    case overlay: OverlaySprite => overlay
    //case animation: AnimatedSprite => ???
    case _ => new BasicOverlaySprite(sprite.id, sprite.name, sprite.frameData, opacity)
  }

  //

}