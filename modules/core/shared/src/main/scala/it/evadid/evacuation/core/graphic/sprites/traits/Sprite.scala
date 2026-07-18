package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.evacuation.core.graphic.spritemap.FrameData

trait Sprite {

  val name: String
  val id: Int

  def frameData: FrameData

}
