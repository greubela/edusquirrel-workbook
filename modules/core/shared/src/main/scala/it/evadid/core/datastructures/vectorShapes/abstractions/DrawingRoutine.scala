package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{AspectRatio, Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPath, SvgPathBuilder, SvgPathBuilderRelativeCoords}
import it.evadid.util.logging.Logger

trait DrawingRoutine[T: Fractional] {

  def renderPath(logger: Logger, bounds: Bounds[T]): SvgPath = {
    val builder = SvgPathBuilder.immutableBuilder[T](bounds.startPoint)
    val result = appendPathToBuilder(logger, builder, bounds.dimension)
    BuilderBasedSvgPath[T](bounds, result)
  }

  def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[T], targetDimension: Dimension[T]): SvgPathBuilder[T]

  def hasDesiredAspectRatio: Option[AspectRatio]

}

object DrawingRoutine {

  abstract class DrawingRoutineRelativeToMaxDim[T: Fractional] extends DrawingRoutine[T] {

    override def appendPathToBuilder(logger: Logger, builder: SvgPathBuilder[T], targetDimension: Dimension[T]): SvgPathBuilder[T] = {
      draw(logger, SvgPathBuilderRelativeCoords[T](logger, builder, targetDimension)).baseBuilder.closePath()
    }

    def draw(logger: Logger, builder: SvgPathBuilderRelativeCoords[T]): SvgPathBuilderRelativeCoords[T]
  }

}
