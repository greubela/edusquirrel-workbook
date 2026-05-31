package todomove.webElementsOld.webElements.svg.shapes.composite

import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.shapes.BeShape

import scala.collection.mutable

case class TableShape(columns: List[List[BeShape]]) extends BoxManualPositioning {

  private def fullRows: List[List[BeShape]] = columns.transpose

  override def calcOffsetsAndDimensions(config: BeRenderingConfig): List[ManualPositionElement] = {

    val maxColumnWidths = columns.map(_.map(_.displaySize(config).width).max)
    val maxRowHeights = fullRows.map(_.map(_.displaySize(config).height).max)

    val res = mutable.ListBuffer[ManualPositionElement]()

    var offsetX: Double = 0

    for ((curColumn, curColumnWidth) <- columns.zip(maxColumnWidths)) {
      var offsetY: Double = 0
      for ((rowCell, rowHeight) <- curColumn.zip(maxRowHeights)) {
        res.addOne(ManualPositionElement(rowCell, Point[Double](offsetX, offsetY), Dimension[Double](curColumnWidth, rowHeight)))
        offsetY += rowHeight
      }
      offsetX += curColumnWidth
    }

    res.toList

  }
}