package workbook.workbookHtmlElements.visualization

import contentmanagement.htmlElements.genericElements.canvas.AppCanvas

object EdgeRenderer {

  def drawEdges(canvas: AppCanvas[?], layout: WorkbookLayout, config: VisualizationConfig): Unit = {
    val edgesBySource = layout.edges.groupBy(_.source)
    layout.edges.foreach { edge =>
      val sourceNode = layout.nodes(edge.source)
      val targetNode = layout.nodes(edge.target)
      val siblings = edgesBySource(edge.source)
      val offsetIndex = siblings.sortBy(e => (layout.nodes(e.target).layer, layout.nodes(e.target).order)).indexOf(edge)
      val offsetTotal = siblings.size
      drawEdge(canvas, sourceNode, targetNode, edge.dependencyType, offsetIndex, offsetTotal, config)
    }
  }

  private def drawEdge(
      canvas: AppCanvas[?],
      source: SectionNode,
      target: SectionNode,
      dependencyType: DependencyType,
      offsetIndex: Int,
      total: Int,
      config: VisualizationConfig
  ): Unit = {
    val color = dependencyType match {
      case DependencyType.Required     => config.requiredColor
      case DependencyType.Recommended => config.recommendedColor
    }
    canvas.setStrokeColor(color)

    val offsets = computeOffsets(total, config.edgeVerticalSpacing)
    val offset = offsets(offsetIndex)

    val startX = source.x + source.width + config.arrowStartOffset
    val startY = source.y + source.height / 2 + offset
    val endX = target.x - config.arrowEndOffset
    val endY = target.y + target.height / 2 + offset

    val distanceX = endX - startX
    val maxControlOffset = math.max(0.0, distanceX / 2 - 1.0)
    val desiredControlOffset = math.max(distanceX * config.edgeCurveControlFraction, config.edgeCurveMinControlOffset)
    val controlOffset = math.min(desiredControlOffset, maxControlOffset)

    val deltaY = endY - startY
    val bend = deltaY * config.edgeCurveVerticalBendFactor

    val control1X = startX + controlOffset
    val control1Y = startY + bend
    val control2X = endX - controlOffset
    val control2Y = endY - bend

    val dashPattern = dependencyType match {
      case DependencyType.Required     => None
      case DependencyType.Recommended => Some(config.recommendedDashPattern)
    }

    canvas.drawCubicBezier(
      startX,
      startY,
      control1X,
      control1Y,
      control2X,
      control2Y,
      endX,
      endY,
      config.edgeStrokeWidth,
      dashPattern
    )

    val tangentX = endX - control2X
    val tangentY = endY - control2Y
    drawArrowHead(canvas, endX, endY, tangentX, tangentY, config)
  }

  private def computeOffsets(total: Int, spacing: Double): IndexedSeq[Double] = {
    if (total <= 1) IndexedSeq(0.0)
    else {
      val start = -spacing * (total - 1) / 2.0
      (0 until total).map(i => start + i * spacing)
    }
  }

  private def drawArrowHead(
      canvas: AppCanvas[?],
      x: Double,
      y: Double,
      tangentX: Double,
      tangentY: Double,
      config: VisualizationConfig
  ): Unit = {
    val length = config.arrowHeadLength
    val width = config.arrowHeadWidth
    val magnitude = math.hypot(tangentX, tangentY)
    val (ux, uy) =
      if (magnitude == 0) (-1.0, 0.0)
      else (-tangentX / magnitude, -tangentY / magnitude)
    val (px, py) = (-uy, ux)

    val baseX = x + ux * length
    val baseY = y + uy * length
    val leftX = baseX + px * (width / 2)
    val leftY = baseY + py * (width / 2)
    val rightX = baseX - px * (width / 2)
    val rightY = baseY - py * (width / 2)

    canvas.drawLine(x, y, leftX, leftY, config.edgeStrokeWidth)
    canvas.drawLine(x, y, rightX, rightY, config.edgeStrokeWidth)
  }
}
