package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.core.datastructures.matrix.Direction
import it.evadid.evacuation.core.graphic.spritemap.FrameData

trait PersonSprite extends Sprite {

  def getFrame(nr: Long, dir: Direction): FrameData

}
