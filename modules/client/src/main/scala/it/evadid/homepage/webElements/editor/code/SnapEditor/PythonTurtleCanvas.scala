package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.BeExpressionToTurtleCommands
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import org.scalajs.dom
import org.scalajs.dom.{CanvasRenderingContext2D, HTMLCanvasElement}
import scala.scalajs.js

/** Draws a finished turtle path onto the Python editor's canvas. */
object PythonTurtleCanvas {
  val Width = 480
  val Height = 360

  /** Draws the path one command at a time. `stepMs` 0 paints the finished picture at once. Returns a cancel handle. */
  def play(canvas: HTMLCanvasElement, commands: List[TurtleCommand[Double]], stepMs: Double): () => Unit =
    if stepMs <= 0 || commands.length <= 1 then
      paint(canvas, commands)
      () => ()
    else
      var shown = 0
      var timer = 0
      def frame(): Unit =
        shown = math.min(shown + 1, commands.length)
        paint(canvas, commands.take(shown))
        if shown < commands.length then
          timer = dom.window.setTimeout(() => frame(), stepMs)
      frame()
      () => if timer != 0 then dom.window.clearTimeout(timer)

  def paint(canvas: HTMLCanvasElement, commands: List[TurtleCommand[Double]]): Unit =
    paint(
      canvas,
      TurtlePathBuilder[Double](Point(0.0, 0.0), commands, BeExpressionToTurtleCommands.SnapHeadingDeg)
    )

  def paint(canvas: HTMLCanvasElement, built: TurtlePathBuilder[Double]): Unit = {
    val segments = built.completedStyledSegments
    val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
    canvas.width = Width
    canvas.height = Height
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.fillStyle = "#ffffff"
    ctx.fillRect(0, 0, Width, Height)
    val points = segments.flatMap(_.pathBuilder.pathPoints)
    if points.isEmpty then return
    val minX = points.map(_.x).min
    val maxX = points.map(_.x).max
    val minY = points.map(_.y).min
    val maxY = points.map(_.y).max
    val pad = 24.0
    val worldW = math.max(maxX - minX, 1.0)
    val worldH = math.max(maxY - minY, 1.0)
    val scale = math.min((Width - pad * 2) / worldW, (Height - pad * 2) / worldH)
    val tx = pad - minX * scale + ((Width - pad * 2) - worldW * scale) / 2.0
    val ty = pad - minY * scale + ((Height - pad * 2) - worldH * scale) / 2.0
    ctx.setTransform(scale, 0, 0, scale, tx, ty)
    ctx.lineCap = "round"
    ctx.lineJoin = "round"
    segments.foreach { segment =>
      val path = js.Dynamic.newInstance(js.Dynamic.global.Path2D)(segment.pathBuilder.toSvgPathD)
      val color = segment.style.color.trim
      ctx.strokeStyle = if color.isEmpty then "#000000" else color
      ctx.lineWidth = math.max(segment.style.size.toDouble, 1.0)
      ctx.asInstanceOf[js.Dynamic].stroke(path)
    }
  }
}
