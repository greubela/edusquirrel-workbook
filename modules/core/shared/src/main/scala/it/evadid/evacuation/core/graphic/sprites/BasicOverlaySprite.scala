package it.evadid.evacuation.core.graphic.sprites

import it.evadid.evacuation.core.graphic.spritemap.FrameData
import it.evadid.evacuation.core.graphic.sprites.traits.OverlaySprite

case class BasicOverlaySprite(id: Int, name: String, frameData: FrameData, opacityUpTo255: Int) extends OverlaySprite {

}

object BasicOverlaySprite {
  def yellowOverlayPath(opToOne: Double) = new BasicOverlaySprite(1000, "yellowOverlay", FrameData("yellowOverlay"), (225 - opToOne * 175).toInt)

  def yellowOverlay(opTo255: Int): OverlaySprite = new BasicOverlaySprite(1000, "yellowOverlay", FrameData("yellowOverlay"), opTo255)

  val whiteOverlay: OverlaySprite = new BasicOverlaySprite(1000, "whiteOverlay", FrameData("whiteOverlay"), 225)
}
