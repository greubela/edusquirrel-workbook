package it.evadid.evacuation.core.graphic.sprites

import upickle.default.ReadWriter

import it.evadid.core.datastructures.matrix.Direction
import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits.PersonSprite

case class BasicPersonSprite(id: Int, name: String, frameData: FrameData) extends PersonSprite derives ReadWriter {

  override def getFrame(nr: Long, dir: Direction): FrameData = frameData

}
