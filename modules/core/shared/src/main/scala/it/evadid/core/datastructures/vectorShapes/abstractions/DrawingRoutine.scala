package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPath, SvgPathBuilder, SvgPathBuilderRelativeCoords}
import it.evadid.util.logging.Logger

trait DrawingRoutine[T: Fractional] {


  def renderPath(logger: Logger, absolutePosition: Point[T], dimension: Dimension[T], alignIfMisfit: AlignmentInParent): SvgPath = {
    val targetDimension: Dimension[T] =
      if (shouldConformToDrawingRatio.isEmpty || alignIfMisfit == AlignmentInParent.DistortionAlignment) dimension
      else shouldConformToDrawingRatio.get.withSameRatioAndMaxSizeWithin(dimension)

    val N = summon[Fractional[T]]
    import N.*
    val offset = alignIfMisfit match {
      case position: AlignmentInParent.PositionInParent =>
        val x = position.horizontal match {
          case AlignmentInParent.HorizontalAlignment.Left => fromInt(0)
          case AlignmentInParent.HorizontalAlignment.Center => (dimension.width - targetDimension.width) / fromInt(2)
          case AlignmentInParent.HorizontalAlignment.Right => dimension.width - targetDimension.width
        }
        val y = position.vertical match {
          case AlignmentInParent.VerticalAlignment.Top => fromInt(0)
          case AlignmentInParent.VerticalAlignment.Middle => (dimension.height - targetDimension.height) / fromInt(2)
          case AlignmentInParent.VerticalAlignment.Bottom => dimension.height - targetDimension.height
        }
        Dimension(x, y)
      case AlignmentInParent.DistortionAlignment => Dimension(fromInt(0), fromInt(0))
    }
    val builder = SvgPathBuilder.immutableBuilder[T](absolutePosition.moveWithDimension(offset))
    val result = appendPathToBuilder(logger, builder, targetDimension)
    BuilderBasedSvgPath[T](targetDimension, result)
  }

  def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[T], targetDimension: Dimension[T]): SvgPathBuilder[T]

  def shouldConformToDrawingRatio: Option[Dimension[T]]

}

object DrawingRoutine {

  abstract class DrawingRoutineRelativeToMaxDim[T: Fractional] extends DrawingRoutine[T] {

    override def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[T], targetDimension: Dimension[T]): SvgPathBuilder[T] = {
      draw(SvgPathBuilderRelativeCoords[T](logger, builder, targetDimension)).baseBuilder.closePath()
    }

    def draw(builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T]

    def shouldConformToDrawingRatio: Option[Dimension[T]] = onlyNonDistortedIfDimensionRatio.map(Dimension.fromDouble(_))

    def onlyNonDistortedIfDimensionRatio: Option[Dimension[Double]]

  }

}
