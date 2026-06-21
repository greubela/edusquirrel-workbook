package it.evadid.evacuation.core.graphic.sprites

import it.evadid.evacuation.core.datastructures.Direction
import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits.PersonSprite
import it.evadid.evacuation.core.utility.DataStructureHelper

case class AnimatedPersonSprite(id: Int, name: String, data: Map[Direction, List[FrameData]]) extends PersonSprite {

  def getFrame(nr: Long, dir: Direction): FrameData = {
    val spriteList = data(dir)
    DataStructureHelper.getElementFromSeqSafelyMod(spriteList, nr)
  }

  override def frameData: FrameData = data(Direction.BOTTOM).head

}
