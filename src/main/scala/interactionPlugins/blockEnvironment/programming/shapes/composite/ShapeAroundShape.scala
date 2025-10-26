package interactionPlugins.blockEnvironment.programming.shapes.composite

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.compositeElements.AppDecoratedSvgElement
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.shapes.BeShape.{BeShapeComposite, BeShapeContainerable}
import interactionPlugins.blockEnvironment.programming.shapes.BeShape
import interactionPlugins.blockEnvironment.programming.shapes.atomic.TextShape

case class ShapeAroundShape(outerShape: BeShapeContainerable, innerShape: BeShape) extends BeShapeComposite{
  
  override def displaySize(config: BeRenderingConfig): Dimension[Double] = {
    val innerDim = innerShape.displaySize(config)
    outerShape.minSizeToContainChild(config, innerDim)    
  }
  
  override lazy val children: List[BeShape] = List(outerShape.asInstanceOf[BeShape], innerShape)
  
  def render(config: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = {
    val childBounds = outerShape.getChildBounds(config, bounds, innerShape.displaySize(config))
    val inner = innerShape.render(config, childBounds)
    val outer = outerShape.render(config, bounds)
    AppDecoratedSvgElement(outer, List(inner), List())
  }

  
  
}
