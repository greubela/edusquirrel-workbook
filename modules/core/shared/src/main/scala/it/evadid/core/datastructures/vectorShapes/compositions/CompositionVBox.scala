package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.{AspectRatio, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.*
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementDimensioned, AppElementMeasured, AppElementPositioned}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}

/** Arranges children from top to bottom, separated by the configured vertical gap. */
case class CompositionVBox[T: Fractional](alignment: AlignmentInParent) extends AppShapeCompositeControl[T] {
  override def calculateMyMinimumDimension(children: List[AppElementMeasured[T]], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    val N = summon[Fractional[T]]
    val gapHeight = N.times(renderingConfig.gapBetweenConsecutiveShapes.height, N.fromInt(math.max(0, children.size - 1)))
    val raw = Dimension(children.map(_.minimumDimension.fullDimension.width).foldLeft(N.fromInt(0))(N.max),
      N.plus(children.map(_.minimumDimension.fullDimension.height).foldLeft(N.fromInt(0))(N.plus), gapHeight))
    RenderingDimension.fromRawDimensionAndConfig(raw, compositionConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppElementMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementDimensioned[T]] =
    dimensionChildrenAtMinimum(children)

  override def calculateChildrenPositions(children: List[AppElementDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementPositioned[T]] = {
    val N = summon[Fractional[T]]
    var y = N.fromInt(0)
    children.map { child =>
      val aligned = calculateOffset(myRenderingSize.rawDimension, child.adjustedRenderingSize.fullDimension, alignment)
      val positioned = positionChild(child, Point(aligned.x, y))
      y = N.plus(N.plus(y, child.adjustedRenderingSize.fullDimension.height), renderingConfig.gapBetweenConsecutiveShapes.height)
      positioned
    }
  }

  override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = None
}
