package workbook.workbookHtmlElements.visualization

import contentmanagement.htmlElements.genericElements.canvas.AppCanvas
import contentmanagement.model.AppFont

object SectionRenderer {

  def drawSections(canvas: AppCanvas[?], layout: WorkbookLayout, config: VisualizationConfig): Unit = {
    layout.nodes.foreach { node =>
      drawSection(canvas, node, config)
    }
  }

  private def drawSection(canvas: AppCanvas[?], node: SectionNode, config: VisualizationConfig): Unit = {
    canvas.setFillColor(config.sectionFillColor)
    canvas.fillRect(node.x, node.y, node.width, node.height)
    canvas.setStrokeColor(config.sectionBorderColor)
    canvas.drawRect(node.x, node.y, node.width, node.height, 2)

    drawTitle(canvas, node, config)
    drawExercises(canvas, node, config)
  }

  private def drawTitle(canvas: AppCanvas[?], node: SectionNode, config: VisualizationConfig): Unit = {
    val titleCenterX = node.x + node.width / 2
    val titleCenterY = node.y + config.sectionPaddingY + config.titleHeight / 2
    canvas.setFillColor(config.titleColor)
    canvas.setFont(AppFont.aptos.copy(size = 14))
    canvas.drawStringCentered(titleCenterX, titleCenterY, node.section.title)
  }

  private def drawExercises(canvas: AppCanvas[?], node: SectionNode, config: VisualizationConfig): Unit = {
    if (node.bubbleLayouts.isEmpty) {
      val centerX = node.x + node.width / 2
      val centerY = node.y + node.bubbleAreaTop + node.bubbleAreaHeight / 2
      canvas.setFillColor(config.labelColor)
      canvas.setFont(AppFont.aptos.copy(size = 12))
      canvas.drawStringCentered(centerX, centerY, "No exercises")
      return
    }

    node.bubbleLayouts.foreach { bubble =>
      val x = node.x + bubble.relativeX
      val y = node.y + bubble.relativeY
      canvas.setFillColor(config.bubbleFillColor)
      canvas.fillRect(x, y, bubble.width, bubble.height)
      canvas.setStrokeColor(config.bubbleBorderColor)
      canvas.drawRect(x, y, bubble.width, bubble.height, 1.5)
    }
  }
}
