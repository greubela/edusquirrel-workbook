package todomove.webElementsOld.webElements.svg.shapes.special

import it.evadid.core.datastructures.vectorShapes.blocks.{BlockPaths, FlowDecorationPaths}

import todomove.webElementsOld.webElements.svg.shapes.BeShape.BeShapePathBased
import it.evadid.core.datastructures.geometry.{Bounds, Dimension}
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.config.BeRenderingConfig

case class CommentShape() extends BeShapePathBased derives upickle.default.ReadWriter {

  override protected def getPathBuilder(config: BeRenderingConfig, bounds: Bounds[Double]): SvgPathBuilder[Double] = BlockPaths.buildSpeechBubbleShape(bounds, config.controlSegmentSize)

  override protected def spaceBeforeChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(config.controlSegmentSize * 2 , config.paddingSmall.height)

  override protected def spaceAfterChild(config: BeRenderingConfig, childDim: Dimension[Double]): Dimension[Double] = Dimension(config.controlSegmentSize , config.paddingSmall.height)

}