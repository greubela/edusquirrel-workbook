package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.{CompositeDimensionControl, CompositePositionControl}
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.rendering.{AppCompositionDimensioned, AppCompositionRendered}

case class CompositionHBox[T: Fractional](alignment: AlignmentInParent) extends AppShapeCompositeControl[T] {

  override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
    override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]]): Dimension[T] = {
      val N = summon[Fractional[T]]
      import N.*
      val gapCount = N.fromInt(math.max(0, minimumDimensionedChildren.size - 1))
      Dimension(N.plus(minimumDimensionedChildren.map(_.compositionDimension.width).foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.width, gapCount)),
        minimumDimensionedChildren.map(_.compositionDimension.height).foldLeft(N.fromInt(0))(N.max))
    }
    override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]], myRequestedSize: Dimension[T]): List[AppCompositionDimensioned[T]] = minimumDimensionedChildren
  }

  override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
    override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppCompositionDimensioned[T]]): List[AppCompositionRendered[T]] = {
      val N = summon[Fractional[T]]
      import N.*
      val container = CompositionLayout.maxDimension(actualDimensionedChildren)
      var x = N.fromInt(0)
      actualDimensionedChildren.map { child =>
        val aligned = CompositionLayout.alignedOffset(container, child.compositionDimension, alignment)
        val result = CompositionLayout.rendered(child, Point(x, aligned.y))
        x = N.plus(N.plus(x, child.compositionDimension.width), renderingConfig.gapBetweenConsecutiveShapes.width)
        result
      }
    }
  }
}
