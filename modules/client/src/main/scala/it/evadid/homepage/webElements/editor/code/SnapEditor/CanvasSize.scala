package it.evadid.homepage.webElements.editor.code.SnapEditor

import org.scalajs.dom
import scala.scalajs.js

/** Canvas bitmap sizes as Morphic reports them.
  *
  * On displays with a device pixel ratio other than 1, Morphic's retina support replaces the canvas
  * width/height getters with backing store size / pixel ratio, which can be fractional (987 at 1.5 reads
  * as 986.67). scala-js-dom types them as Int, so reading them directly fails with a ClassCastException.
  */
private[SnapEditor] object CanvasSize:
  def width(canvas: dom.HTMLCanvasElement): Double = canvas.asInstanceOf[js.Dynamic].width.asInstanceOf[Double]
  def height(canvas: dom.HTMLCanvasElement): Double = canvas.asInstanceOf[js.Dynamic].height.asInstanceOf[Double]

  /** Whole pixels for the Int setters. */
  def roundedWidth(canvas: dom.HTMLCanvasElement): Int = math.round(width(canvas)).toInt
  def roundedHeight(canvas: dom.HTMLCanvasElement): Int = math.round(height(canvas)).toInt

  /** Whether the bitmap differs from `width` x `height`; retina rounding stays below one pixel. */
  def differs(canvas: dom.HTMLCanvasElement, width: Int, height: Int): Boolean =
    math.abs(this.width(canvas) - width) >= 1 || math.abs(this.height(canvas) - height) >= 1
