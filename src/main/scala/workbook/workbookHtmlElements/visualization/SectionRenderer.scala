package workbook.workbookHtmlElements.visualization

import contentmanagement.webElements.genericHtmlElements.canvas.AppCanvas
import contentmanagement.model.AppFont
import contentmanagement.model.language.AppLanguage

object SectionRenderer {

  def drawSections(canvas: AppCanvas[?], layout: WorkbookLayout, config: VisualizationConfig): Unit = {
    layout.nodes.foreach { node =>
      drawSection(canvas, node, config)
    }
  }

  private def drawSection(canvas: AppCanvas[?], node: SectionNode, config: VisualizationConfig): Unit = {
    canvas.setFillColor(config.sectionFillColor)
    fillRoundedRect(canvas, node.x, node.y, node.width, node.height, config.sectionCornerRadius)
    canvas.setStrokeColor(config.sectionBorderColor)
    strokeRoundedRect(canvas, node.x, node.y, node.width, node.height, config.sectionCornerRadius, config.sectionBorderWidth)

    drawTitle(canvas, node, config)
    drawExercises(canvas, node, config)
  }

  private def drawTitle(canvas: AppCanvas[?], node: SectionNode, config: VisualizationConfig): Unit = {
    val titleCenterX = node.x + node.width / 2
    val titleCenterY = node.y + config.sectionPaddingY + config.titleHeight / 2
    canvas.setFillColor(config.titleColor)
    canvas.setFont(AppFont.Aptos.copy(sizeInPx = config.titleFontSize))
    canvas.drawStringCentered(titleCenterX, titleCenterY, node.section.title)
  }

  private def drawExercises(canvas: AppCanvas[?], node: SectionNode, config: VisualizationConfig): Unit = {
    if (node.bubbleLayouts.isEmpty) {
      val width = config.bubbleMinWidth
      val x = node.x + (node.width - width) / 2
      val y = node.y + node.bubbleAreaTop
      canvas.setFillColor(config.sectionBorderColor)
      fillRoundedRect(canvas, x, y, width, config.bubbleHeight, config.exerciseCornerRadius)
      canvas.setStrokeColor(config.sectionBorderColor)
      strokeRoundedRect(canvas, x, y, width, config.bubbleHeight, config.exerciseCornerRadius, config.exerciseBorderWidth)
      canvas.setFillColor(config.labelColor)
      canvas.setFont(AppFont.Aptos.copy(sizeInPx = config.exerciseFontSize))
      canvas.drawStringCentered(x + width / 2, y + config.bubbleHeight / 2, "No exercises")
      return
    }

    node.bubbleLayouts.foreach { bubble =>
      val x = node.x + bubble.relativeX
      val y = node.y + bubble.relativeY
      canvas.setFillColor(config.bubbleFillColor)
      fillRoundedRect(canvas, x, y, bubble.width, bubble.height, config.exerciseCornerRadius)
      canvas.setStrokeColor(config.bubbleBorderColor)
      strokeRoundedRect(canvas, x, y, bubble.width, bubble.height, config.exerciseCornerRadius, config.exerciseBorderWidth)
      val label = truncateLabel(bubble.exercise.titleMap(AppLanguage.English).trim, bubble.width, config)
      canvas.setFillColor(config.labelColor)
      canvas.setFont(AppFont.Aptos.copy(sizeInPx = config.exerciseFontSize))
      canvas.drawStringCentered(x + bubble.width / 2, y + bubble.height / 2, label)
    }
  }

  private def truncateLabel(label: String, width: Double, config: VisualizationConfig): String = {
    val available = math.max(1.0, width - config.exerciseLabelPadding * 2)
    val averageGlyphWidth = math.max(3.0, config.exerciseFontSize * 0.55)
    val maxChars = math.max(1, math.floor(available / averageGlyphWidth).toInt)
    if (label.length <= maxChars) label
    else {
      val ellipsis = "…"
      if (maxChars <= 1) ellipsis else label.take(maxChars - 1).trim + ellipsis
    }
  }

  private def fillRoundedRect(
      canvas: AppCanvas[?],
      x: Double,
      y: Double,
      width: Double,
      height: Double,
      radius: Double
  ): Unit = {
    val clampedRadius = math.max(0.0, math.min(radius, math.min(width, height) / 2))
    if (clampedRadius == 0) {
      canvas.fillRect(x, y, width, height)
      return
    }

    val diameter = clampedRadius * 2
    val centerY = y + height / 2

    if (diameter >= width) {
      canvas.fillCircle(x + width / 2, centerY, math.min(width, height))
    } else {
      canvas.fillRect(x + clampedRadius, y, width - diameter, height)
      canvas.fillCircle(x + clampedRadius, centerY, diameter)
      canvas.fillCircle(x + width - clampedRadius, centerY, diameter)
    }
  }

  private def strokeRoundedRect(
      canvas: AppCanvas[?],
      x: Double,
      y: Double,
      width: Double,
      height: Double,
      radius: Double,
      strokeWidth: Double
  ): Unit = {
    val clampedRadius = math.max(0.0, math.min(radius, math.min(width, height) / 2))
    if (clampedRadius == 0) {
      canvas.drawRect(x, y, width, height, strokeWidth)
      return
    }

    val diameter = clampedRadius * 2
    val top = y
    val bottom = y + height
    val left = x
    val right = x + width
    val centerY = y + height / 2

    if (diameter >= width) {
      canvas.drawCircle(left + width / 2, centerY, math.min(width, height), strokeWidth)
    } else {
      val topStart = left + clampedRadius
      val topEnd = right - clampedRadius
      canvas.drawLine(topStart, top, topEnd, top, strokeWidth)
      canvas.drawLine(topStart, bottom, topEnd, bottom, strokeWidth)
      canvas.drawArc(left + clampedRadius, centerY, diameter, 90, 180, strokeWidth)
      canvas.drawArc(right - clampedRadius, centerY, diameter, 270, 180, strokeWidth)
    }
  }
}
