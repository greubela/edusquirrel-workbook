package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension, Point, RelativeBounds}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementDimensioned, AppElementMeasured, AppElementPositioned}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.AlignmentInParent.{DistortionAlignment, HorizontalAlignment, PositionInParent, VerticalAlignment}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}


trait AppShapeCompositeControl[T: Fractional] {

  def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)]

  def calculateMyMinimumDimension(childrenDimensions: List[AppElementMeasured[T]], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T]

  def calculateChildrenDimensions(children: List[AppElementMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementDimensioned[T]]

  def calculateChildrenPositions(children: List[AppElementDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementPositioned[T]]

}

object AppShapeCompositeControl {

  case class ResizingBehavior()

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

  def minimumRenderingDimension[T: Fractional](children: Iterable[AppElementMeasured[T]]): Dimension[T] =
    maxDimension(children.map(_.minimumDimension.fullDimension))

  def dimensionChildrenAtMinimum[T: Fractional](children: List[AppElementMeasured[T]]): List[AppElementDimensioned[T]] =
    children.map(child => child.withTargetDimension(child.minimumDimension))

  def positionChild[T: Fractional](child: AppElementDimensioned[T], offset: Point[T]): AppElementPositioned[T] =
    child.withOffset(offset)

  def positionAligned[T: Fractional](child: AppElementDimensioned[T], container: Dimension[T], alignment: AlignmentInParent): AppElementPositioned[T] =
    positionChild(child, calculateOffset(container, child.adjustedRenderingSize.fullDimension, alignment))


  def calculateRelativeBounds[T: Fractional](targetDimension: Dimension[T], desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)]): RelativeBounds[T] = {
    val useDimension: Dimension[T] = calculateAdjustedDimension(targetDimension, desiredAspectRatioAndAlignment)
    val offset: Point[T] = calculateOffset(targetDimension, useDimension, desiredAspectRatioAndAlignment.map(_._2).getOrElse(DistortionAlignment))
    RelativeBounds(offset, useDimension)
  }

  def calculateRelativeBounds[T: Fractional](targetDimension: Dimension[T], desiredDimension: Option[Dimension[T]], alignIfMisfit: AlignmentInParent, scaleDesiredToFit: Boolean = false): RelativeBounds[T] = {
    val useDimension: Dimension[T] = calculateAdjustedDimension(targetDimension, desiredDimension, alignIfMisfit, scaleDesiredToFit)
    val offset: Point[T] = calculateOffset(targetDimension, useDimension, alignIfMisfit)
    RelativeBounds(offset, useDimension)
  }

  def calculateAdjustedDimension[T: Fractional](targetDimension: Dimension[T], desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)]): Dimension[T] = {
    if (desiredAspectRatioAndAlignment.isEmpty || desiredAspectRatioAndAlignment.get._2 == AlignmentInParent.DistortionAlignment) targetDimension
    else Dimension.fromRatioAndMaxDimension(desiredAspectRatioAndAlignment.get._1, targetDimension)
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




