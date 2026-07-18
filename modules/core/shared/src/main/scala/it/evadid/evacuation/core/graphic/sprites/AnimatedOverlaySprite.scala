package it.evadid.evacuation.core.graphic.sprites

import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits._

case class AnimatedOverlaySprite(id: Int, name: String, frameList: List[FrameData], opacityUpTo255: Int) extends AnimatedSprite with OverlaySprite {

}
