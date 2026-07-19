package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AlignmentInParent
import it.evadid.core.datastructures.vectorShapes.abstractions.AlignmentInParent.{HorizontalAlignment, PositionInParent, VerticalAlignment}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.Offset
import it.evadid.core.datastructures.vectorShapes.rendering.{AppCompositionDimensioned, AppCompositionRendered}

private[compositions] object CompositionLayout {
  def zeroDimension[T: Fractional]: Dimension[T] = {
    val zero = summon[Fractional[T]].fromInt(0)
    Dimension(zero, zero)
  }

  def maxDimension[T: Fractional](children: List[AppCompositionDimensioned[T]]): Dimension[T] = {
    val N = summon[Fractional[T]]
    children.foldLeft(zeroDimension[T]) { (current, child) =>
      Dimension(N.max(current.width, child.compositionDimension.width), N.max(current.height, child.compositionDimension.height))
    }
  }

  def rendered[T: Fractional](child: AppCompositionDimensioned[T], point: Point[T]): AppCompositionRendered[T] =
    AppCompositionRendered(child.composition, child.shapeConfig, child.renderingConfig, child.compositionDimension, Offset(point))

  def alignedOffset[T: Fractional](container: Dimension[T], child: Dimension[T], alignment: AlignmentInParent): Point[T] = {
    val N = summon[Fractional[T]]
    import N.*
    val zero = fromInt(0)
    val two = fromInt(2)
    alignment match {
      case position: PositionInParent =>
        val x = position.horizontal match {
          case HorizontalAlignment.Left => zero
          case HorizontalAlignment.Center => (container.width - child.width) / two
          case HorizontalAlignment.Right => container.width - child.width
        }
        val y = position.vertical match {
          case VerticalAlignment.Top => zero
          case VerticalAlignment.Middle => (container.height - child.height) / two
          case VerticalAlignment.Bottom => container.height - child.height
        }
        Point(N.max(zero, x), N.max(zero, y))
      case AlignmentInParent.DistortionAlignment => Point(zero, zero)
    }
  }
}
