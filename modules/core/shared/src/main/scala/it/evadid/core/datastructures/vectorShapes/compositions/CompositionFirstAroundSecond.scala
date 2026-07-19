package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeAtomar, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition

case class CompositionFirstAroundSecond[T: Fractional](alignment: AlignmentInParent, outerShape: AppShapeAtomar[T], innerShape: AppShapeAtomar[T]) extends AppShapeCompositeControl[T] {
  /*
    override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
      override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppShapeRelativePositioned[T]]): Dimension[T] = CompositionLayout.maxDimension(minimumDimensionedChildren)
      override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppShapeRelativePositioned[T]], myRequestedSize: Dimension[T]): List[AppShapeRelativePositioned[T]] = minimumDimensionedChildren
    }

    override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
      override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppShapeRelativePositioned[T]]): List[AppCompositionRendered[T]] = {
        val container = CompositionLayout.maxDimension(actualDimensionedChildren)
        actualDimensionedChildren.map(child => CompositionLayout.rendered(child, CompositionLayout.alignedOffset(container, child.compositionDimension, alignment)))
      }
    }

   */

  override def calculateMyMinimumDimension(childrenDimensions: List[AppShapeComposition.AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): AppShapeComposition.RenderingDimension[T] = ???

  override def calculateChildrenDimensions(children: List[AppShapeComposition.AppCompositionMeasured[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionDimensioned[T]] = ???

  override def calculateChildrenPositions(children: List[AppShapeComposition.AppCompositionDimensioned[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionPositioned[T]] = ???
}
