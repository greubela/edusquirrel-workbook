package it.evadid.core.datastructures.vectorShapes.abstractions

import it.evadid.core.datastructures.geometry.*
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.{AppElementMeasured, AppElementRendered}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeElementConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.helper.{AlignmentInParent, RenderingDimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath
import it.evadid.util.logging.Logger

sealed trait AppShapeElement[T: Fractional] {

  def compositeControl: AppShapeCompositeControl[T]

  def childrenInRenderingOrder: List[AppShapeElement[T]]

  private def withMinimumDimension(renderingConfig: AppShapeRenderingConfig[T]): AppElementMeasured[T] = {
    val childrenMeasured = childrenInRenderingOrder.map(_.withMinimumDimension(renderingConfig))
    val minimumDimension: RenderingDimension[T] = compositeControl.calculateMyMinimumDimension(childrenMeasured, elementConfig, renderingConfig)
    AppElementMeasured(childrenMeasured, this, minimumDimension, renderingConfig)
  }

  def renderComposition(renderingConfig: AppShapeRenderingConfig[T], targetBounds: Bounds[T]): AppElementRendered[T] = {
    val myDimension = RenderingDimension.fromFullDimensionAndConfig(targetBounds.dimension, elementConfig, renderingConfig)
    this.
      withMinimumDimension(renderingConfig)
      .withTargetDimension(myDimension)
      .withOffset(Point.fromIntPoint(0, 0))
      .asRendered(targetBounds.startPoint)
  }

  def elementConfig: AppShapeElementConfig[T]

}

object AppShapeElement {

  trait AppShapeAtomar[T: Fractional] extends AppShapeElement[T] {

    def renderPath(logger: Logger, bounds: Bounds[T]): SvgPath

    def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)]

    def calculateMyRawDimension(): Dimension[T]

    override def compositeControl: AppShapeCompositeControl[T] = new AppShapeCompositeControl[T] {
      override def desiredAspectRatioAndAlignment: Option[(AspectRatio, AlignmentInParent)] = {
        AppShapeAtomar.this.desiredAspectRatioAndAlignment
      }

      override def calculateMyMinimumDimension(childrenDimensions: List[AppElementMeasured[T]], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
        val rawDimension = calculateMyRawDimension()
        RenderingDimension.fromRawDimensionAndConfig(rawDimension, compositionConfig, renderingConfig)
      }

      override def calculateChildrenDimensions(children: List[AppElementMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementDimensioned[T]] = List()

      override def calculateChildrenPositions(children: List[AppElementDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeElementConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppElementPositioned[T]] = List()
    }

    override def childrenInRenderingOrder: List[AppShapeElement[T]] = List()


  }

  trait AppShapeComposition[T: Fractional](
                                            compositeControl: AppShapeCompositeControl[T],
                                            compositionConfig: AppShapeElementConfig[T],
                                            childrenInRenderingOrder: List[AppShapeElement[T]]
                                          ) extends AppShapeElement[T] {


  }

  /** A composition tree whose minimum dimensions have been calculated bottom-up. */
  case class AppElementMeasured[T: Fractional](children: List[AppElementMeasured[T]], baseElement: AppShapeElement[T], minimumDimension: RenderingDimension[T], renderingConfig: AppShapeRenderingConfig[T]) {
    def withTargetDimension(renderingSize: RenderingDimension[T]): AppElementDimensioned[T] = {
      val relativeBoundsRaw = AppShapeCompositeControl.calculateRelativeBounds(renderingSize.rawDimension, baseElement.compositeControl.desiredAspectRatioAndAlignment)
      val adjustedRenderingSize = RenderingDimension.fromRawDimensionAndConfig(relativeBoundsRaw.dimension, baseElement.elementConfig, renderingConfig)
      val childrenDimensioned = baseElement.compositeControl.calculateChildrenDimensions(children, adjustedRenderingSize, baseElement.elementConfig, renderingConfig)
      AppElementDimensioned(childrenDimensioned, this, relativeBoundsRaw)
    }
  }

  /** A measured composition whose node and descendants have concrete dimensions. */
  case class AppElementDimensioned[T: Fractional](children: List[AppElementDimensioned[T]], compositionMeasurd: AppElementMeasured[T], relativeBoundsRaw: RelativeBounds[T]) {

    lazy val adjustedRenderingSize: RenderingDimension[T] = RenderingDimension.fromRawDimensionAndConfig(relativeBoundsRaw.dimension, compositionMeasurd.baseElement.elementConfig, compositionMeasurd.renderingConfig)

    def withOffset(offsetCalculatedFromParent: Point[T]): AppElementPositioned[T] = {
      val paddingToUse = compositionMeasurd.baseElement.elementConfig.useCustomPadding.getOrElse(compositionMeasurd.renderingConfig.defaultPadding)
      lazy val myFullOffset: Point[T] = paddingToUse.asPoint + relativeBoundsRaw.offsetInParents + offsetCalculatedFromParent
      val childrenPositioned = compositionMeasurd.baseElement.compositeControl.calculateChildrenPositions(children, adjustedRenderingSize, compositionMeasurd.baseElement.elementConfig, compositionMeasurd.renderingConfig)
      AppElementPositioned(childrenPositioned, this, adjustedRenderingSize.fullDimension.withOffset(myFullOffset))
    }
  }

  /** A dimensioned composition with bounds relative to its parent. */
  case class AppElementPositioned[T: Fractional](children: List[AppElementPositioned[T]], compositionDimensioned: AppElementDimensioned[T], relativeBounds: RelativeBounds[T]) {
    def asRendered(myAbsoluteStartingPoint: Point[T]): AppElementRendered[T] = {
      val childrenRendered = children.map(curChild => curChild.asRendered(myAbsoluteStartingPoint + curChild.relativeBounds.offsetInParents))
      AppElementRendered(childrenRendered, this, relativeBounds.toAbsoluteBounds(myAbsoluteStartingPoint))
    }
  }


  /** A fully laid-out composition with absolute bounds ready for drawing. */
  case class AppElementRendered[T: Fractional](
                                                children: List[AppElementRendered[T]],
                                                compositionPositioned: AppElementPositioned[T],
                                                myBounds: Bounds[T],
                                              ) {

  }


}

