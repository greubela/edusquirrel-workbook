package it.evadid.evacuation.core.graphic.sprites

import upickle.default.ReadWriter

import it.evadid.evacuation.core.graphic.spritemap.{FloorSpriteProperties, FrameData}
import it.evadid.evacuation.core.graphic.sprites.traits.FloorSprite

case class BasicFloorSprite(id: Int, name: String, frameData: FrameData, properties: FloorSpriteProperties, isSave: Boolean) extends FloorSprite derives ReadWriter {

}
