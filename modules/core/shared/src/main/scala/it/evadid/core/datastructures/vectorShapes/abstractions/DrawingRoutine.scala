package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPath, SvgPathBuilder, SvgPathBuilderRelativeCoords}
import it.evadid.util.logging.Logger

trait DrawingRoutine[T: Fractional] {


  def renderPath(logger: Logger, bounds: Bounds[T], alignIfMisfit: AlignmentInParent): SvgPath = {
    val relativeBounds = AppShapeCompositeControl.calculateRelativeBounds(bounds.dimension, shouldConformToDrawingRatio, alignIfMisfit, true)
    val builder = SvgPathBuilder.immutableBuilder[T](bounds.startPoint + relativeBounds.offsetInParents)
    val result = appendPathToBuilder(logger, builder, relativeBounds.dimension)
    BuilderBasedSvgPath[T](bounds, relativeBounds, result)
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
