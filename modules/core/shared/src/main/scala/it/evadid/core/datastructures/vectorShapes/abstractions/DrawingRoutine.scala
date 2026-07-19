package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPath, SvgPathBuilder, SvgPathBuilderRelativeCoords}
import it.evadid.util.logging.Logger

trait DrawingRoutine[T: Fractional] {


  def renderPath(logger: Logger, absolutePosition: Point[T], dimension: Dimension[T], alignIfMisfit: AlignmentInParent): SvgPath = {
    val targetDimension: Dimension[T] =
      if (shouldConformToDrawingRatio.isEmpty || alignIfMisfit != AlignmentInParent.DistortionAlignment) dimension
      else shouldConformToDrawingRatio.get.withSameRatioAndMaxSizeWithin(dimension)

    val builder = SvgPathBuilder.immutableBuilder[T](absolutePosition)
    val res = appendPathToBuilder(logger, builder, targetDimension)
    val svg = BuilderBasedSvgPath[T](dimension, builder)
    svg
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