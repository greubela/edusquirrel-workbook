package it.evadid.evacuation.core.graphic.sprites

import upickle.default.ReadWriter

import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits.Sprite

case class BasicSprite(id: Int, name: String, frameData: FrameData) extends Sprite derives ReadWriter {

}
