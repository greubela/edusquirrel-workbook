package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.color.RGBColor
import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppShapeComposition
import it.evadid.core.datastructures.vectorShapes.abstractions.DrawingRoutine
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.core.datastructures.vectorShapes.compositions.CompositionBlockStack
import it.evadid.core.datastructures.vectorShapes.config.AppShapeElementConfig
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.svg.{BeExpressionToTurtleCommands, SvgPathBuilder, SvgPathBuilderImmutable, TurtlePathBuilder}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtlePenStyle
import it.evadid.util.logging.Logger
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapInputCodec

object VmToSvg {

  def renderBeExpression(logger: Logger, expression: BeExpression): AppShapeElement[Double] =
    renderTurtlePathBuilder(
      TurtlePathBuilder(
        Point(0.0, 0.0),
        BeExpressionToTurtleCommands(expression),
        BeExpressionToTurtleCommands.SnapHeadingDeg
      )
    )

  /** Same visual pipeline as [[renderBeExpression]], from an already built turtle path. */
  def renderTurtlePathBuilder(built: TurtlePathBuilder[Double]): AppShapeElement[Double] =
    compose(List(built), colors = None)

  /** Target in blue, student drawing in red. `resultInFront` chooses which one is painted last. */
  def renderOverlay(
      target: TurtlePathBuilder[Double],
      actual: TurtlePathBuilder[Double],
      resultInFront: Boolean = true
  ): AppShapeElement[Double] = {
    val blue = RGBColor(30, 100, 220)
    val red = RGBColor.red
    val (drawings, colors) =
      if resultInFront then (List(target, actual), List(blue, red))
      else (List(actual, target), List(red, blue))
    compose(drawings, colors = Some(colors))
  }

  private def compose(
      drawings: List[TurtlePathBuilder[Double]],
      colors: Option[List[RGBColor]]
  ): AppShapeElement[Double] = {
    val pad = 16.0
    val points = drawings.flatMap(_.svgPathBuilder.pathPoints)
    val minX = points.map(_.x).minOption.getOrElse(0.0)
    val maxX = points.map(_.x).maxOption.getOrElse(0.0)
    val minY = points.map(_.y).minOption.getOrElse(0.0)
    val maxY = points.map(_.y).maxOption.getOrElse(0.0)
    val width = math.max(50.0, maxX - minX + 2 * pad)
    val height = math.max(50.0, maxY - minY + 2 * pad)
    val minSize = Some(Dimension[Double](width, height))
    val delta = Dimension(pad - minX, pad - minY)

    val children = drawings.zipWithIndex.flatMap { (built, index) =>
      val forced = colors.flatMap(_.lift(index))
      built.completedStyledSegments.map { segment =>
        val shifted = segment.pathBuilder.moveWholePath(delta).asInstanceOf[SvgPathBuilderImmutable[Double]]
        val config = forced match
          case Some(color) => AppShapeElementConfig.turtleSegment(color, 2.0)
          case None => turtleConfig(segment.style)
        AppShapeDrawingRoutineElement[Double](PathPreviewRoutine(shifted), config, minSize)
      }
    }

    if children.isEmpty then emptyPreview(minSize)
    else
      AppShapeComposition(
        CompositionBlockStack(AlignmentInParent.DistortionAlignment),
        AppShapeElementConfig.turtleSegment(RGBColor.black, 1.0),
        children
      )
  }

  private def emptyPreview(minSize: Option[Dimension[Double]]): AppShapeElement[Double] =
    AppShapeDrawingRoutineElement[Double](
      PathPreviewRoutine(SvgPathBuilderImmutable[Double](Point(0.0, 0.0))),
      AppShapeElementConfig.EvaShapeConfigDefault[Double],
      minSize.orElse(Some(Dimension[Double](50.0, 50.0)))
    )

  private def turtleConfig(style: TurtlePenStyle[Double]): AppShapeElementConfig[Double] = {
    val stroke = SnapInputCodec.parseColor(style.color).getOrElse(RGBColor.black)
    AppShapeElementConfig.turtleSegment(stroke, style.size)
  }

  private final case class PathPreviewRoutine(path: SvgPathBuilderImmutable[Double]) extends DrawingRoutine[Double] derives upickle.default.ReadWriter {
    override def hasDesiredAspectRatio: Option[AspectRatio] = None

    override def appendPathToBuilder(
        logger: Logger,
        builder: SvgPathBuilder[Double],
        targetDimension: Dimension[Double]
    ): SvgPathBuilder[Double] = path
  }

}
