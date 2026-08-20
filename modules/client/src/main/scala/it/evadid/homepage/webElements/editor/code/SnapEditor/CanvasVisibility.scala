package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.homepage.control.info.HomepageLoggerInfo
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.SnapCodeEditorImpl
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

import scala.collection.mutable


/** Observes renderer output without modifying it. */
private[SnapEditor] object CanvasVisibility:

  private val EmptyCanvasColorLimit = 3

  def warnIfUnexpectedlyEmpty(renderer: SnapCodeEditorImpl, snapXml: String, canvas: Canvas): Unit =
    if looksLikeProgramWithBlocks(snapXml) && uniqueColorCount(canvas, EmptyCanvasColorLimit + 1) <= EmptyCanvasColorLimit then
      HomepageLoggerInfo.singleton.uiAndDomLogger.logWarn(
        s"${renderer.getClass.getSimpleName} rendered a Snap project with blocks, " +
          s"but the resulting canvas has at most $EmptyCanvasColorLimit unique colors and appears empty."
      )

  private def looksLikeProgramWithBlocks(snapXml: String): Boolean =
    snapXml.contains("<block")

  private def uniqueColorCount(canvas: Canvas, stopAfter: Int): Int =
    val context = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
    val pixels = context.getImageData(0, 0, canvas.width, canvas.height).data
    val colors = mutable.HashSet.empty[Int]
    var index = 0
    while index < pixels.length && colors.size < stopAfter do
      colors +=
        (pixels(index).toInt << 24) |
          (pixels(index + 1).toInt << 16) |
          (pixels(index + 2).toInt << 8) |
          pixels(index + 3).toInt
      index += 4
    colors.size
