package it.evadid.evacuation.core.graphic.spritemap

import upickle.default.ReadWriter

case class FrameData(filename: String) derives ReadWriter


object FrameData{


  def fromTilemapString(str: String): FrameData = new FrameData(str)

}
