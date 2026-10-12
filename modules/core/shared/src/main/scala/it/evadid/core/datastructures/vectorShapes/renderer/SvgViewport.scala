package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppElementRendered
import it.evadid.core.datastructures.vectorShapes.helper.RenderingDimension

/** Includes allocated padding and the strokes of every visible descendant. */
object SvgViewport {
  def boundsFor(shape: AppElementRendered[Double]): Bounds[Double] = {
    def paintedBounds(node: AppElementRendered[Double]): List[Bounds[Double]] = {
      val stroke = node.elementConfig.strokeWidth
      require(stroke.isFinite && stroke >= 0, "stroke width must be finite and non-negative")
      if node.children.nonEmpty then node.children.flatMap(paintedBounds)
      else {
        // SVG's default miter limit is four times the half-stroke width. Reserve
        // that maximum extent so sharp joins are contained as well as straight edges.
        val margin = stroke * 2
        List(Bounds(Point(node.myBounds.startX - margin, node.myBounds.startY - margin),
          node.myBounds.dimension.increaseSize(2 * margin, 2 * margin)))
      }
    }

    val bounds = shape.outerBounds :: paintedBounds(shape)
    val minX = bounds.map(_.startX).min
    val minY = bounds.map(_.startY).min
    val maxX = bounds.map(_.endX).max
    val maxY = bounds.map(_.endY).max
    val dimension = Dimension(maxX - minX, maxY - minY)
    RenderingDimension.validateDimension(dimension, "SVG viewport dimensions")
    require(minX.isFinite && minY.isFinite, "SVG viewport origin must be finite")
    Bounds(Point(minX, minY), dimension)
  }
}
