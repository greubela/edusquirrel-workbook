package it.evadid.core.datastructures.canvas

/** Platform-neutral image data accepted by an [[AppCanvas]]. */
trait AppImage {
  def imageSourceString: String
}
