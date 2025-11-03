package contentmanagement.webElements.svg.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.webElements.svg.shapes.BeShape
import interactionPlugins.blockEnvironment.config.BeRenderingConfig

import BeShape.*

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

// todo fix as not working... :(
// todo add method children -> getRow(nr) and getColumn(nr)
case class TableShape(
    override val children: List[BeShape],
    columnAlignments: List[HorizontalAlignment],
    rowAlignments: List[VerticalAlignment] = List(),
    usePadding: Boolean = true
) extends BeShapeBox {
  val columnCount: Int = columnAlignments.length

  require(columnCount > 0, "columnCount must be positive")

  private val resolvedColumnAlignments: Vector[HorizontalAlignment] =
    if (columnAlignments.nonEmpty) {
      require(
        columnAlignments.length == columnCount,
        "columnAlignments must contain exactly one entry per column"
      )
      columnAlignments.toVector
    } else Vector.fill(columnCount)(HorizontalAlignment.Left)

  private val expectedRowCount =
    if (children.isEmpty) 0 else ((children.length - 1) / columnCount) + 1

  private val resolvedRowAlignments: Vector[VerticalAlignment] =
    if (rowAlignments.nonEmpty) {
      require(
        rowAlignments.length == expectedRowCount,
        "rowAlignments must contain exactly one entry per row"
      )
      rowAlignments.toVector
    } else Vector.fill(expectedRowCount)(VerticalAlignment.Top)

  private def childDimensions(config: BeRenderingConfig): List[Dimension[Double]] =
    children.map(_.displaySize(config))

  private def computeLayout(dimensions: List[Dimension[Double]]): (Vector[Double], Vector[Double]) = {
    val columnWidths = Array.fill(columnCount)(0.0)
    val rowHeights = ArrayBuffer.empty[Double]

    for ((dim, index) <- dimensions.zipWithIndex) {
      val column = index % columnCount
      val row = index / columnCount
      if (row >= rowHeights.length) {
        rowHeights += 0.0
      }
      columnWidths(column) = math.max(columnWidths(column), dim.width)
      rowHeights(row) = math.max(rowHeights(row), dim.height)
    }

    (columnWidths.toVector, rowHeights.toVector)
  }

  private def effectiveColumnCount(columnWidths: Vector[Double]): Int = {
    val lastIndexWithContent = columnWidths.lastIndexWhere(_ > 0.0)
    if (lastIndexWithContent >= 0) lastIndexWithContent + 1
    else if (children.nonEmpty) math.min(columnCount, children.size) else 0
  }

  override def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val dims = childDimensions(config)
    val (columnWidths, rowHeights) = computeLayout(dims)

    val usedColumnCount = effectiveColumnCount(columnWidths)
    val horizontalPaddingValue =
      if (usePadding && usedColumnCount > 1) config.paddingSmall.width else 0.0
    val verticalPaddingValue =
      if (usePadding && rowHeights.size > 1) config.paddingSmall.height else 0.0

    val horizontalPadding = horizontalPaddingValue * math.max(usedColumnCount - 1, 0)
    val verticalPadding = verticalPaddingValue * math.max(rowHeights.size - 1, 0)

    val widthSum = columnWidths.take(usedColumnCount).sum
    val heightSum = rowHeights.sum

    Dimension[Double](widthSum + horizontalPadding, heightSum + verticalPadding)
      .ensureAtLeastAsBigAs(config.paddingSmall)
  }

  override def calcChildrenBounds(config: BeRenderingConfig, bounds: Bounds[Double]): Map[BeShape, Bounds[Double]] = {
    val dims = childDimensions(config)
    val (columnWidths, rowHeights) = computeLayout(dims)
    val res = mutable.HashMap[BeShape, Bounds[Double]]()

    val usedColumnCount = effectiveColumnCount(columnWidths)
    val horizontalPaddingValue =
      if (usePadding && usedColumnCount > 1) config.paddingSmall.width else 0.0
    val verticalPaddingValue =
      if (usePadding && rowHeights.size > 1) config.paddingSmall.height else 0.0

    var currentY = bounds.startPoint.y
    var childIndex = 0

    for (row <- rowHeights.indices) {
      var currentX = bounds.startPoint.x
      val rowHeight = rowHeights(row)

      for (column <- 0 until usedColumnCount) {
        if (childIndex < children.length) {
          val child = children(childIndex)
          val minDim = dims(childIndex)
          val childDim = minDim
          val extraSpaceX = math.max(0.0, columnWidths(column) - childDim.width)
          val offsetX = resolvedColumnAlignments(column) match
            case HorizontalAlignment.Left   => 0.0
            case HorizontalAlignment.Center => extraSpaceX / 2
            case HorizontalAlignment.Right  => extraSpaceX
          val extraSpaceY = math.max(0.0, rowHeight - childDim.height)
          val offsetY = resolvedRowAlignments(row) match
            case VerticalAlignment.Top    => 0.0
            case VerticalAlignment.Center => extraSpaceY / 2
            case VerticalAlignment.Bottom => extraSpaceY
          val childStart = Point[Double](currentX + offsetX, currentY + offsetY)
          val childBounds = childStart.withDimension(childDim)
          res.put(child, childBounds)
          childIndex += 1
        }
        currentX += columnWidths(column)
        if (horizontalPaddingValue > 0.0 && column < usedColumnCount - 1) {
          currentX += horizontalPaddingValue
        }
      }
      currentY += rowHeight
      if (verticalPaddingValue > 0.0 && row < rowHeights.size - 1) {
        currentY += verticalPaddingValue
      }
    }

    res.toMap
  }
}
