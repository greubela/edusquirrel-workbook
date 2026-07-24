package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.*
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementDimensioned, AppElementMeasured, AppElementPositioned}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}

/** Arranges children from left to right, separated by the configured horizontal gap. */
case class CompositionHBox[T: Fractional](alignment: AlignmentInParent) extends AppShapeCompositeControl[T] {
  override def calculateMyMinimumDimension(children: List[AppElementMeasured[T]], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    val N = summon[Fractional[T]]
    val gapWidth = N.times(renderingConfig.gapBetweenConsecutiveShapes.width, N.fromInt(math.max(0, children.size - 1)))
    val raw = Dimension(N.plus(children.map(_.minimumDimension.fullDimension.width).foldLeft(N.fromInt(0))(N.plus), gapWidth),
      children.map(_.minimumDimension.fullDimension.height).foldLeft(N.fromInt(0))(N.max))
    RenderingDimension.fromRawDimensionAndConfig(raw, compositionConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppElementMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementDimensioned[T]] =
    dimensionChildrenAtMinimum(children)

  override def calculateChildrenPositions(children: List[AppElementDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementPositioned[T]] = {
    val N = summon[Fractional[T]]
    var x = N.fromInt(0)
    children.map { child =>
      val aligned = calculateOffset(myRenderingSize.rawDimension, child.adjustedRenderingSize.fullDimension, alignment)
      val positioned = positionChild(child, Point(x, aligned.y))
      x = N.plus(N.plus(x, child.adjustedRenderingSize.fullDimension.width), renderingConfig.gapBetweenConsecutiveShapes.width)
      positioned
    }
  }

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = None
}
