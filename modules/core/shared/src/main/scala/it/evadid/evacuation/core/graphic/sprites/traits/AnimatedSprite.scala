package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.utility.DataStructureHelper

trait AnimatedSprite extends Sprite {

  val frameList: List[FrameData]

  override def frameData: FrameData = frameList.head

  def getFrame(nr: Int): FrameData = DataStructureHelper.getElementFromSeqSafelyMod(frameList, nr)

}

object AnimatedSprite{

}
