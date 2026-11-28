package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.homepage.control.info.HomepageLoggerInfo
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor.*
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas



/** Observes renderer output without modifying it. */
private[SnapEditor] object CanvasVisibility:

  private val EmptyCanvasColorLimit = 3

  def warnIfUnexpectedlyEmpty(rendererName: String, snapXml: String, canvas: Canvas): Unit =
    if looksLikeProgramWithBlocks(snapXml) && uniqueColorCount(canvas, EmptyCanvasColorLimit + 1) <= EmptyCanvasColorLimit then
      HomepageLoggerInfo.singleton.uiAndDomLogger.logWarn(
        s"${rendererName} rendered a Snap project with blocks, " +
          s"but the resulting canvas has at most $EmptyCanvasColorLimit unique colors and appears empty."
      )

  private[SnapEditor] def looksLikeProgramWithBlocks(snapXml: String): Boolean =
    // Every project has a <blocks> container, including an empty project.
    // Only actual primitive/custom block instances should trigger this warning.
    "<(?:block|custom-block)(?=[\\s/>])".r.findFirstIn(snapXml).nonEmpty

  private def uniqueColorCount(canvas: Canvas, stopAfter: Int): Int =
    val context = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
    val pixels = context.getImageData(0, 0, canvas.width, canvas.height).data
    def color(index: Int): Int =
      (pixels(index).toInt << 24) | (pixels(index + 1).toInt << 16) | (pixels(index + 2).toInt << 8) | pixels(index + 3).toInt
    // Distinct colors seen so far, stopping once `stopAfter` are known.
    Iterator.range(0, pixels.length, 4).map(color).scanLeft(Set.empty[Int])(_ + _)
      .takeWhile(_.size <= stopAfter).foldLeft(0)((_, seen) => seen.size)
