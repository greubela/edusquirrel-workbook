package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{Dimension, Point, RelativeBounds}
import it.evadid.core.datastructures.vectorShapes.abstractions.AlignmentInParent.{HorizontalAlignment, PositionInParent, VerticalAlignment}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition.{AppCompositionDimensioned, AppCompositionMeasured, AppCompositionPositioned, RenderingDimension}


trait AppShapeCompositeControl[T: Fractional] {
  def calculateMyMinimumDimension(childrenDimensions: List[AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T]

  def calculateChildrenDimensions(children: List[AppCompositionMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppCompositionDimensioned[T]]

  def calculateChildrenPositions(children: List[AppCompositionDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppCompositionPositioned[T]]

}

object AppShapeCompositeControl {

  def zeroDimension[T: Fractional]: Dimension[T] = {
    val zero = summon[Fractional[T]].fromInt(0)
    Dimension(zero, zero)
  }

  def maxDimension[T: Fractional](dimensions: Iterable[Dimension[T]]): Dimension[T] = {
    val N = summon[Fractional[T]]
    dimensions.foldLeft(zeroDimension[T]) { (current, dimension) =>
      Dimension(N.max(current.width, dimension.width), N.max(current.height, dimension.height))
    }
  }

  def minimumRenderingDimension[T: Fractional](children: Iterable[AppCompositionMeasured[T]]): Dimension[T] =
    maxDimension(children.map(_.minimumDimension.fullDimension))

  def dimensionChildrenAtMinimum[T: Fractional](children: List[AppCompositionMeasured[T]]): List[AppCompositionDimensioned[T]] =
    children.map(child => child.withTargetDimension(child.minimumDimension))

  def positionChild[T: Fractional](child: AppCompositionDimensioned[T], offset: Point[T]): AppCompositionPositioned[T] =
    child.withOffsets(offset)

  def positionAligned[T: Fractional](child: AppCompositionDimensioned[T], container: Dimension[T], alignment: AlignmentInParent): AppCompositionPositioned[T] =
    positionChild(child, calculateOffset(container, child.renderingDimension.fullDimension, alignment))

  def calculateRelativeBounds[T: Fractional](targetDimension: Dimension[T], desiredDimension: Option[Dimension[T]], alignIfMisfit: AlignmentInParent, scaleDesiredToFit: Boolean = false): RelativeBounds[T] = {
    val useDimension: Dimension[T] = calculateAdjustedDimension(targetDimension, desiredDimension, alignIfMisfit, scaleDesiredToFit)
    val offset: Point[T] = calculateOffset(targetDimension, useDimension, alignIfMisfit)
    RelativeBounds(offset, useDimension)
  }

  def calculateAdjustedDimension[T: Fractional](targetDimension: Dimension[T], desiredDimension: Option[Dimension[T]], alignIfMisfit: AlignmentInParent, scaleDesiredToFit: Boolean = false): Dimension[T] = {
    if (desiredDimension.isEmpty || alignIfMisfit == AlignmentInParent.DistortionAlignment) targetDimension
    else if (scaleDesiredToFit) desiredDimension.get.scaledToFitInto(targetDimension)
    else desiredDimension.get
  }

  def calculateOffset[T: Fractional](container: Dimension[T], child: Dimension[T], alignment: AlignmentInParent): Point[T] = {
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




