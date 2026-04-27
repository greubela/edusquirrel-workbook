package contentmanagement.webElements.svg.builder.controlFlow

import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.controlFlow.path.{ControlFlowPathOverlay, ControlFlowOverlayBuilder}
import contentmanagement.webElements.svg.shapes.*
import contentmanagement.webElements.svg.shapes.BeShape.BeShapeContainerable
import datastructures.core.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.RenderingInformation
import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}


trait ControlFlowType {

  def widthInIndentationLevels: Int

  def minHeightInSegments: Int

  def backgroundShape: BeShapeContainerable

  def renderPaths(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): ControlFlowPathOverlay

  def buildParts(renderingConfig: BeRenderingConfig, oldOverlay: ControlFlowPathOverlay): List[ControlFlowPart] = {
    val overlay = renderPaths(renderingConfig, oldOverlay)
    List(ControlFlowBackgroundPart(backgroundShape), overlay)
  }
  
  /*
  def buildBackgroundShape(renderingConfig: BeRenderingConfig, shapeAmends: ShapeAmends): BeShape = {
    backgroundShape.addAmends(renderingConfig.amendFactory.defaultControlFlowBackgroundAmend)
  }

  override def displaySize(renderingConfig: BeRenderingConfig): Dimension[Double] =
    Dimension[Double](renderingConfig.controlSegmentSize * widthInIndentationLevels * 6, renderingConfig.controlSegmentSize * minHeightInSegments)

  override def render(renderingConfig: BeRenderingConfig, bounds: Bounds[Double]): AppSvgElement = shapeRenderBackground(renderingConfig).render(renderingConfig, bounds)

  def renderControlFlow(cf: ControlFlowOverlayBuilder, renderingInfo: RenderingInformation, centerPoint: Point[Double], curLineHeight: Double): ControlFlowOverlayBuilder
  */
  
  
}
