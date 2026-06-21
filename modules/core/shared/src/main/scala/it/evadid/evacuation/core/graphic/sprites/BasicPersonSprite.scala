package it.evadid.evacuation.core.graphic.sprites

import it.evadid.evacuation.core.datastructures.Direction
import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits.PersonSprite

case class BasicPersonSprite(id: Int, name: String, frameData: FrameData) extends PersonSprite {

  override def getFrame(nr: Long, dir: Direction): FrameData = frameData

}
