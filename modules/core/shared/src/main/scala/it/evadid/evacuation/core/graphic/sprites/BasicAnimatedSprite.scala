package it.evadid.evacuation.core.graphic.sprites

import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits.AnimatedSprite

case class BasicAnimatedSprite(id: Int, name: String, frameList: List[FrameData]) extends AnimatedSprite{


}
