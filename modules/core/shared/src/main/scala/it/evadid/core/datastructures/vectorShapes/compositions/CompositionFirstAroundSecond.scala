package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.{CompositeDimensionControl, CompositePositionControl}
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeAtomar, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.rendering.{AppCompositionDimensioned, AppCompositionRendered}

trait CompositionFirstAroundSecond[T: Fractional](alignment: AlignmentInParent, outerShape: AppShapeAtomar[T], innerShape: AppShapeAtomar[T]) extends AppShapeCompositeControl[T] {

  override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
    override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]]): Dimension[T] = CompositionLayout.maxDimension(minimumDimensionedChildren)
    override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]], myRequestedSize: Dimension[T]): List[AppCompositionDimensioned[T]] = minimumDimensionedChildren
  }

  override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
    override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppCompositionDimensioned[T]]): List[AppCompositionRendered[T]] = {
      val container = CompositionLayout.maxDimension(actualDimensionedChildren)
      actualDimensionedChildren.map(child => CompositionLayout.rendered(child, CompositionLayout.alignedOffset(container, child.compositionDimension, alignment)))
    }
  }
}
