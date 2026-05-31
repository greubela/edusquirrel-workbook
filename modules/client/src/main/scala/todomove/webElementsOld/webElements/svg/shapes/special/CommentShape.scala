package todomove.webElementsOld.webElements.svg.shapes.special

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapePathBased
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig
import todomove.webElementsOld.webElements.svg.builder.SvgPathBuilder
import todomove.webElementsOld.webElements.svg.shapes.ShapeFactory

case class CommentShape() extends BeShapePathBased {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = ShapeFactory.buildSpeechBubbleShape(bounds, config.controlSegmentSize)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(config.controlSegmentSize * 2 , config.paddingSmall.height)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(config.controlSegmentSize , config.paddingSmall.height)

}