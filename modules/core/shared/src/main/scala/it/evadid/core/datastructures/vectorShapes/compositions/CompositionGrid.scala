package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.matrix.Matrix
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition

case class CompositionGrid[T: Fractional](alignments: Matrix[AlignmentInParent]) extends AppShapeCompositeControl[T] {
  /*
    override def dimensionControl: CompositeDimensionControl[T] = new CompositeDimensionControl[T]() {
      override def calculateRawMinimumDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppShapeRelativePositioned[T]]): Dimension[T] = {
        require(minimumDimensionedChildren.size == alignments.elements.size, "a grid needs exactly one child per alignment cell")
        val N = summon[Fractional[T]]
        import N.*
        val widths = (0 until alignments.dim.cols).map(col => (0 until alignments.dim.rows).map(row => minimumDimensionedChildren(row * alignments.dim.cols + col).compositionDimension.width).foldLeft(N.fromInt(0))(N.max))
        val heights = (0 until alignments.dim.rows).map(row => minimumDimensionedChildren.slice(row * alignments.dim.cols, (row + 1) * alignments.dim.cols).map(_.compositionDimension.height).foldLeft(N.fromInt(0))(N.max))
        Dimension(N.plus(widths.foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.width, N.fromInt(math.max(0, alignments.dim.cols - 1)))),
          N.plus(heights.foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.height, N.fromInt(math.max(0, alignments.dim.rows - 1)))))
      }
      override def resizeChildrenBasedOnRequestedDimension(renderingConfig: AppShapeRenderingConfig[T], minimumDimensionedChildren: List[AppShapeRelativePositioned[T]], myRequestedSize: Dimension[T]): List[AppShapeRelativePositioned[T]] = minimumDimensionedChildren
    }

    override def positionControl: CompositePositionControl[T] = new CompositePositionControl[T]() {
      override def calculateChildrenOffsets(renderingConfig: AppShapeRenderingConfig[T], actualDimensionedChildren: List[AppShapeRelativePositioned[T]]): List[AppCompositionRendered[T]] = {
        require(actualDimensionedChildren.size == alignments.elements.size, "a grid needs exactly one child per alignment cell")
        val N = summon[Fractional[T]]
        import N.*
        val widths = (0 until alignments.dim.cols).map(col => (0 until alignments.dim.rows).map(row => actualDimensionedChildren(row * alignments.dim.cols + col).compositionDimension.width).foldLeft(N.fromInt(0))(N.max))
        val heights = (0 until alignments.dim.rows).map(row => actualDimensionedChildren.slice(row * alignments.dim.cols, (row + 1) * alignments.dim.cols).map(_.compositionDimension.height).foldLeft(N.fromInt(0))(N.max))
        actualDimensionedChildren.zipWithIndex.map { case (child, index) =>
          val row = index / alignments.dim.cols
          val col = index % alignments.dim.cols
          val x = N.plus(widths.take(col).foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.width, N.fromInt(col)))
          val y = N.plus(heights.take(row).foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.height, N.fromInt(row)))
          val withinCell = CompositionLayout.alignedOffset(Dimension(widths(col), heights(row)), child.compositionDimension, alignments.elements(index))
          CompositionLayout.rendered(child, Point(N.plus(x, withinCell.x), N.plus(y, withinCell.y)))
        }
      }
    }
  */

  override def calculateMyMinimumDimension(childrenDimensions: List[AppShapeComposition.AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): AppShapeComposition.RenderingDimension[T] = ???

  override def calculateChildrenDimensions(children: List[AppShapeComposition.AppCompositionMeasured[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionDimensioned[T]] = ???

  override def calculateChildrenPositions(children: List[AppShapeComposition.AppCompositionDimensioned[T]], myRenderingSize: AppShapeComposition.RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppShapeComposition.AppCompositionPositioned[T]] = ???
}
