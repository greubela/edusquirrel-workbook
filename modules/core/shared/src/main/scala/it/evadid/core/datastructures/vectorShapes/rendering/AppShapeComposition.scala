package it.evadid.core.datastructures.vectorShapes.rendering

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point, RelativeBounds}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition.{AppCompositionMeasured, AppCompositionRendered, RenderingDimension}

/** A tree node which lays out its children through a [[AppShapeCompositeControl]].
 *
 * Rendering proceeds recursively through measurement, dimensioning, relative
 * positioning, and finally conversion to absolute bounds.
 */
case class AppShapeComposition[T: Fractional](
                                               compositeControl: AppShapeCompositeControl[T],
                                               compositionConfig: AppShapeConfig[T],
                                               childrenInRenderingOrder: List[AppShapeComposition[T]]
                                             ) {

  def withMinimumDimension(renderingConfig: AppShapeRenderingConfig[T]): AppCompositionMeasured[T] = {
    val childrenMeasured = childrenInRenderingOrder.map(_.withMinimumDimension(renderingConfig))
    val minimumDimension = compositeControl.calculateMyMinimumDimension(childrenMeasured, compositionConfig, renderingConfig)
    AppCompositionMeasured(childrenMeasured, this, minimumDimension, renderingConfig)
  }

  def renderComposition(renderingConfig: AppShapeRenderingConfig[T], targetBounds: Bounds[T]): AppCompositionRendered[T] = {

    val myDimension = RenderingDimension.fromFullDimensionAndConfig(targetBounds.dimension, compositionConfig, renderingConfig)

    this.
      withMinimumDimension(renderingConfig)
      .withTargetDimension(myDimension)
      .withOffset(Point.fromIntPoint(0, 0))
      .asRendered(targetBounds.startPoint)
  }


}

object AppShapeComposition {

  /** A composition tree whose minimum dimensions have been calculated bottom-up. */
  case class AppCompositionMeasured[T: Fractional](children: List[AppCompositionMeasured[T]], composition: AppShapeComposition[T], minimumDimension: RenderingDimension[T], renderingConfig: AppShapeRenderingConfig[T]) {
    def withTargetDimension(renderingSize: RenderingDimension[T]): AppCompositionDimensioned[T] = {

      val relativeBoundsRaw = AppShapeCompositeControl.calculateRelativeBounds(renderingSize.rawDimension, composition.compositeControl.desiredAspectRatioAndAlignment)
      val adjustedRenderingSize = RenderingDimension.fromRawDimensionAndConfig(relativeBoundsRaw.dimension, composition.compositionConfig, renderingConfig)
      val childrenDimensioned = composition.compositeControl.calculateChildrenDimensions(children, adjustedRenderingSize, composition.compositionConfig, renderingConfig)
      AppCompositionDimensioned(childrenDimensioned, this, relativeBoundsRaw)
    }
  }

  /** A measured composition whose node and descendants have concrete dimensions. */
  case class AppCompositionDimensioned[T: Fractional](children: List[AppCompositionDimensioned[T]], compositionMeasurd: AppCompositionMeasured[T], relativeBoundsRaw: RelativeBounds[T]) {

    lazy val adjustedRenderingSize: RenderingDimension[T] = RenderingDimension.fromRawDimensionAndConfig(relativeBoundsRaw.dimension, compositionMeasurd.composition.compositionConfig, compositionMeasurd.renderingConfig)

    def withOffset(offsetCalculatedFromParent: Point[T]): AppCompositionPositioned[T] = {
      lazy val myFullOffset: Point[T] = compositionMeasurd.composition.compositionConfig.paddingToUse.asPoint + relativeBoundsRaw.offsetInParents + offsetCalculatedFromParent
      val childrenPositioned = compositionMeasurd.composition.compositeControl.calculateChildrenPositions(children, adjustedRenderingSize, compositionMeasurd.composition.compositionConfig, compositionMeasurd.renderingConfig)
      AppCompositionPositioned(childrenPositioned, this, adjustedRenderingSize.fullDimension.withOffset(myFullOffset))
    }
  }

  /** A dimensioned composition with bounds relative to its parent. */
  case class AppCompositionPositioned[T: Fractional](children: List[AppCompositionPositioned[T]], compositionDimensioned: AppCompositionDimensioned[T], relativeBounds: RelativeBounds[T]) {
    def asRendered(myAbsoluteStartingPoint: Point[T]): AppCompositionRendered[T] = {
      val childrenRendered = children.map(curChild => curChild.asRendered(myAbsoluteStartingPoint + curChild.relativeBounds.offsetInParents))
      AppCompositionRendered(childrenRendered, this, relativeBounds.toAbsoluteBounds(myAbsoluteStartingPoint))
    }
  }


  /** A fully laid-out composition with absolute bounds ready for drawing. */
  case class AppCompositionRendered[T: Fractional](
                                                    children: List[AppCompositionRendered[T]],
                                                    compositionPositioned: AppCompositionPositioned[T],
                                                    myBounds: Bounds[T],
                                                  ) {

  }


  /** Stores a content dimension and its corresponding padding-inclusive dimension. */
  case class RenderingDimension[T: Fractional](rawDimension: Dimension[T], fullDimension: Dimension[T])

  object RenderingDimension {
    def fromRawDimensionAndConfig[T: Fractional](rawDimension: Dimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
      val padding: Dimension[T] = compositionConfig.useCustomPadding.getOrElse(renderingConfig.defaultPadding)
      // val margin = shapeConfig.useCustomMargin.getOrElse(renderingConfig.defaultMargin)
      val full = rawDimension.increaseSize(padding) //.increaseSize(margin)
      RenderingDimension(rawDimension, full)
    }

    def fromFullDimensionAndConfig[T: Fractional](fullDimension: Dimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
      val padding: Dimension[T] = compositionConfig.useCustomPadding.getOrElse(renderingConfig.defaultPadding)
      RenderingDimension(fullDimension.decreaseSize(padding), fullDimension)
    }
  }
}
