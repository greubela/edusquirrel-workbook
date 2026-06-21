package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.evacuation.core.datastructures.Direction
import it.evadid.evacuation.core.graphic.spritemap.FrameData

trait PersonSprite extends Sprite {

  def getFrame(nr: Long, dir: Direction): FrameData

}
