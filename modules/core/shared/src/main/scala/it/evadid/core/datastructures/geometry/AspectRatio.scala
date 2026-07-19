package it.evadid.core.datastructures.geometry

case class AspectRatio(widthToHeight: Double) {

}

object AspectRatio {

  def apply(width: Double, height: Double): AspectRatio = {
    AspectRatio(width/height)
  }



}

