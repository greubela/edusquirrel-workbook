package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.matrix.Matrix
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.{CompositeDimensionControl, CompositePositionControl}
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.rendering.{AppCompositionDimensioned, AppCompositionRendered}

case class CompositionGrid[T: Fractional](alignments: Matrix[AlignmentInParent]) extends AppShapeCompositeControl[T] {

  override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
    override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]]): Dimension[T] = ???
    override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]], myRequestedSize: Dimension[T]): List[AppCompositionDimensioned[T]] = ???
  }

  override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
    override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppCompositionDimensioned[T]]): List[AppCompositionRendered[T]] = ???
  }

}
