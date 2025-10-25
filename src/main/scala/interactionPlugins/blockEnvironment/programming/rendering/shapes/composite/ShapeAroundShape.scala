package interactionPlugins.blockEnvironment.programming.rendering.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import interactionPlugins.blockEnvironment.programming.rendering.BeRendererConfig
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.rendering.shapes.BeShape.{BeShapeComposite, BeShapeContainerable}
import interactionPlugins.blockEnvironment.programming.rendering.shapes.atomic.TextShape

case class ShapeAroundShape(outerShape: BeShapeContainerable, innerShape: BeShape) extends BeShapeComposite{
  
  override def displaySize(config: BeRendererConfig): Dimension[Double] = {
    val innerDim = innerShape.displaySize(config)
    outerShape.minSizeToContainChild(config, innerDim)    
  }
  
  override lazy val children: List[BeShape] = List(outerShape.asInstanceOf[BeShape], innerShape)
  
  def renderColorless(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
    val childBounds = outerShape.getChildBounds(config, bounds, innerShape.displaySize(config))
    val inner = innerShape.renderColorless(config, childBounds)
    val outer = outerShape.renderColorless(config, bounds)
    AppDecoratedSvgElement(outer, List(inner), List())
  }

  def renderDefaultColoring(config: BeRendererConfig, bounds: Bounds[Double]): AppSvgElement = {
    val childBounds = outerShape.getChildBounds(config, bounds, innerShape.displaySize(config))
    val inner = innerShape.renderDefaultColoring(config, childBounds)
    val outer = outerShape.renderDefaultColoring(config, bounds)
    AppDecoratedSvgElement(outer, List(inner), List())
  }
  
  
}
