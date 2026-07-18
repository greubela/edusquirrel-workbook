package it.evadid.evacuation.core.graphic.spritemap

import it.evadid.evacuation.core.graphic.sprites.traits.Sprite

trait SpriteMap {
  val name: String
  val spriteSize: Int
  val sprites: List[Sprite]
  val id: SpriteMapResourceIdentifier

  def getFullSpritePath(frameData: FrameData): String = {
    if(id.folderName.isDefined) "./img/tiles/" + name + "/" + id.folderName.get + "/" + frameData.filename + ".png"
    else "./img/tiles/" + name + "/" + spriteSize + "/" + frameData.filename + ".png"
  }

}

object SpriteMap{


}
