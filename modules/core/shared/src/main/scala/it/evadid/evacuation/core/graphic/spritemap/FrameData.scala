package it.evadid.evacuation.core.graphic.spritemap

case class FrameData(filename: String)


object FrameData{


  def fromTilemapString(str: String): FrameData = new FrameData(str)

}
