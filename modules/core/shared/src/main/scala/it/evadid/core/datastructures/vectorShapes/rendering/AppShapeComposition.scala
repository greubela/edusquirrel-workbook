package it.evadid.core.datastructures.vectorShapes.rendering

import it.evadid.core.datastructures.geometry.Dimension
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}

case class AppShapeComposition[T: Fractional](compositeControl: AppShapeCompositeControl[T], compositionConfig: AppShapeConfig[T], childrenInRenderingOrder: List[AppShapeComposition[T]]) {

  private def applyPaddingAndMargins(renderingConfig: AppShapeRenderingConfig[T], rawDimension: Dimension[T]): Dimension[T] = {
    val padding: Dimension[T] = compositionConfig.useCustomPadding.getOrElse(renderingConfig.defaultPadding)
    // val margin = shapeConfig.useCustomMargin.getOrElse(renderingConfig.defaultMargin)
    rawDimension.increaseSize(padding) //.increaseSize(margin)
  }

  private def minimumDimensions(renderingConfig: AppShapeRenderingConfig[T]): (List[AppCompositionDimensioned[T]], Dimension[T]) = {
    val childrenDimensioned = childrenInRenderingOrder.map(_.withMinimumDimension(renderingConfig))
    val myRawDimension = compositeControl.dimensionControl.calculateRawMinimumDimension(renderingConfig, childrenDimensioned)
    (childrenDimensioned, myRawDimension)
  }

  def withMinimumDimension(renderingConfig: AppShapeRenderingConfig[T]): AppCompositionDimensioned[T] = {
    val minDimRaw = minimumDimensions(renderingConfig)._2
    val adjusted = applyPaddingAndMargins(renderingConfig, minDimRaw)
    AppCompositionDimensioned[T](this, compositionConfig, renderingConfig, adjusted)
  }

  def withGivenDimension(renderingConfig: AppShapeRenderingConfig[T], fitInto: Dimension[T]): AppCompositionDimensioned[T] = ???

}

