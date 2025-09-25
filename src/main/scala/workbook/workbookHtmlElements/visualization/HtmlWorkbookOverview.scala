package workbook.workbookHtmlElements.visualization

import com.raquo.laminar.api.L.*
import contentmanagement.htmlElements.genericElements.canvas.SvgCanvas
import workbook.model.exercise.ExerciseSection
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlWorkbookOverview(sections: List[ExerciseSection], config: VisualizationConfig = VisualizationConfig()) extends HtmlWorkbookElement {

  private val layout = LayeredLayout.compute(sections, config)

  override def getDomElement(): Element = {
    val canvasWidth = math.max(1, math.ceil(layout.width)).toInt
    val canvasHeight = math.max(1, math.ceil(layout.height)).toInt
    val canvas = new SvgCanvas(canvasWidth, canvasHeight)
    canvas.clear(config.backgroundColor)
    SectionRenderer.drawSections(canvas, layout, config)
    EdgeRenderer.drawEdges(canvas, layout, config)
    canvas.getDomElement()
  }
}
