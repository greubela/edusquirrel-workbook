package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.{CompositeDimensionControl, CompositePositionControl}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.rendering.{AppShapeComposition, AppCompositionDimensioned, AppCompositionRendered}
import it.evadid.core.datastructures.geometry.Point

case class CompositionSingleElement[T: Fractional]() extends AppShapeCompositeControl[T] {

  override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
    override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]]): Dimension[T] =
      minimumDimensionedChildren.headOption.map(_.compositionDimension).getOrElse(CompositionLayout.zeroDimension[T])
    override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]], myRequestedSize: Dimension[T]): List[AppCompositionDimensioned[T]] =
      minimumDimensionedChildren
  }

  override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
    override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppCompositionDimensioned[T]]): List[AppCompositionRendered[T]] = {
      val zero = summon[Fractional[T]].fromInt(0)
      actualDimensionedChildren.map(CompositionLayout.rendered(_, Point(zero, zero)))
    }
  }


}
