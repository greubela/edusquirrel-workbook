package it.evadid.core.datastructures.geometry

import upickle.default.*

case class AspectRatio(widthToHeight: Double) derives ReadWriter {

}

object AspectRatio {

  def apply(width: Double, height: Double): AspectRatio = {
    AspectRatio(width/height)
  }



}

