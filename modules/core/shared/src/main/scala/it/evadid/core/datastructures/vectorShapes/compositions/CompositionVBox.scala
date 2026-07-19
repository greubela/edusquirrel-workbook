package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition

case class CompositionVBox[T: Fractional](alignment: AlignmentInParent) extends AppShapeCompositeControl[T] {
  /*
    override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
      override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppShapeRelativePositioned[T]]): Dimension[T] = {
        val N = summon[Fractional[T]]
        import N.*
        val gapCount = N.fromInt(math.max(0, minimumDimensionedChildren.size - 1))
        Dimension(minimumDimensionedChildren.map(_.compositionDimension.width).foldLeft(N.fromInt(0))(N.max),
          N.plus(minimumDimensionedChildren.map(_.compositionDimension.height).foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.height, gapCount)))
      }
      override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppShapeRelativePositioned[T]], myRequestedSize: Dimension[T]): List[AppShapeRelativePositioned[T]] = minimumDimensionedChildren
    }

    override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
      override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppShapeRelativePositioned[T]]): List[AppCompositionRendered[T]] = {
        val N = summon[Fractional[T]]
        import N.*
        val container = CompositionLayout.maxDimension(actualDimensionedChildren)
        var y = N.fromInt(0)
        actualDimensionedChildren.map { child =>
          val aligned = CompositionLayout.alignedOffset(container, child.compositionDimension, alignment)
          val result = CompositionLayout.rendered(child, Point(aligned.x, y))
          y = N.plus(N.plus(y, child.compositionDimension.height), renderingConfig.gapBetweenConsecutiveShapes.height)
          result
        }
      }
    }*/

  override def calculateMyMinimumDimension(childrenDimensions: List[AppShapeComposition.AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): AppShapeComposition.RenderingDimension[T] = ???

  override def calculateChildrenDimensions(children: List[AppShapeComposition.AppCompositionMeasured[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionDimensioned[T]] = ???

  override def calculateChildrenPositions(children: List[AppShapeComposition.AppCompositionDimensioned[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionPositioned[T]] = ???
}
