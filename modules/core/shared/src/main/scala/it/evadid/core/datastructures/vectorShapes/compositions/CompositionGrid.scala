package it.evadid.core.datastructures.vectorShapes.compositions

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.matrix.Matrix
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeCompositeControl.*
import it.evadid.core.datastructures.vectorShapes.abstractions.{AlignmentInParent, AppShapeCompositeControl}
import it.evadid.core.datastructures.vectorShapes.config.{AppShapeConfig, AppShapeRenderingConfig}
import it.evadid.core.datastructures.vectorShapes.rendering.AppShapeComposition.{AppCompositionDimensioned, AppCompositionMeasured, AppCompositionPositioned, RenderingDimension}

/** Arranges one child per row-major grid cell using each cell's alignment. */
case class CompositionGrid[T: Fractional](alignments: Matrix[AlignmentInParent]) extends AppShapeCompositeControl[T] {
  private def requireMatchingChildren(children: Iterable[?]): Unit =
    require(children.size == alignments.elements.size, "a grid needs exactly one child per alignment cell")

  private def tracks(dimensions: Seq[Dimension[T]]): (Seq[T], Seq[T]) = {
    requireMatchingChildren(dimensions)
    val N = summon[Fractional[T]]
    val widths = (0 until alignments.dim.cols).map(col =>
      (0 until alignments.dim.rows).map(row => dimensions(row * alignments.dim.cols + col).width).foldLeft(N.fromInt(0))(N.max))
    val heights = (0 until alignments.dim.rows).map(row =>
      dimensions.slice(row * alignments.dim.cols, (row + 1) * alignments.dim.cols).map(_.height).foldLeft(N.fromInt(0))(N.max))
    (widths, heights)
  }

  override def calculateMyMinimumDimension(children: List[AppCompositionMeasured[T]], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): RenderingDimension[T] = {
    val N = summon[Fractional[T]]
    val (widths, heights) = tracks(children.map(_.minimumDimension.fullDimension))
    val gaps = renderingConfig.gapBetweenConsecutiveShapes
    val raw = Dimension(
      N.plus(widths.foldLeft(N.fromInt(0))(N.plus), N.times(gaps.width, N.fromInt(math.max(0, alignments.dim.cols - 1)))),
      N.plus(heights.foldLeft(N.fromInt(0))(N.plus), N.times(gaps.height, N.fromInt(math.max(0, alignments.dim.rows - 1)))))
    RenderingDimension.fromRawDimensionAndConfig(raw, compositionConfig, renderingConfig)
  }

  override def calculateChildrenDimensions(children: List[AppCompositionMeasured[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppCompositionDimensioned[T]] =
    dimensionChildrenAtMinimum(children)

  override def calculateChildrenPositions(children: List[AppCompositionDimensioned[T]], myRenderingSize: RenderingDimension[T], compositionConfig: AppShapeConfig[T], renderingConfig: AppShapeRenderingConfig[T]): List[AppCompositionPositioned[T]] = {
    val N = summon[Fractional[T]]
    val (widths, heights) = tracks(children.map(_.renderingDimension.fullDimension))
    children.zipWithIndex.map { case (child, index) =>
      val row = index / alignments.dim.cols
      val col = index % alignments.dim.cols
      val cellOffset = calculateOffset(Dimension(widths(col), heights(row)), child.renderingDimension.fullDimension, alignments.elements(index))
      val x = N.plus(widths.take(col).foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.width, N.fromInt(col)))
      val y = N.plus(heights.take(row).foldLeft(N.fromInt(0))(N.plus), N.times(renderingConfig.gapBetweenConsecutiveShapes.height, N.fromInt(row)))
      positionChild(child, Point(N.plus(x, cellOffset.x), N.plus(y, cellOffset.y)))
    }
  }
}
