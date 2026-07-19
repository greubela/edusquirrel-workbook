package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.{CompositeDimensionControl, CompositePositionControl}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.rendering.{AppCompositionDimensioned, AppCompositionRendered}


trait AppShapeCompositeControl[T: Fractional] {

  def dimensionControl: CompositeDimensionControl[T]

  def positionControl: CompositePositionControl[T]

}

object AppShapeCompositeControl {

  case class Offset[T: Fractional](relativeOffset: Point[T])

  case class AppShapeChildrenDimensionInfo[T: Fractional](childShape: AppShapeAtomar[T], dimension: Dimension[T])

  case class AppShapeChildrenRenderingInfo[T: Fractional](childShape: AppShapeAtomar[T], dimension: Dimension[T], offset: Offset[T])

  trait CompositeDimensionControl[T: Fractional] {
    def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]]): Dimension[T]

    def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppCompositionDimensioned[T]], myRequestedSize: Dimension[T]): List[AppCompositionDimensioned[T]]
  }

  trait CompositePositionControl[T: Fractional] {
    def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppCompositionDimensioned[T]]): List[AppCompositionRendered[T]]
  }

}


