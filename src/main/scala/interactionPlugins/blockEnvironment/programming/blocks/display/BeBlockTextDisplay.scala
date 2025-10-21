package interactionPlugins.blockEnvironment.programming.blocks.display

import contentmanagement.model.color.RGBColor
import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.AppLanguage
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeDataType}
import interactionPlugins.blockEnvironment.programming.connection.{BeConnectionRole, TextElement}
import interactionPlugins.blockEnvironment.programming.rendering.{BeBlockDisplayManager, BeRendererConfig, ShapeFactory}

case class BeBlockTextDisplay(displayedText: String) extends BeBlockDisplay {

  override def mayEvaluateTo: Set[BeDataType] = Set()

  override def roleInParent: BeConnectionRole = TextElement

  override def layoutManager: BeBlockDisplayManager = new BeBlockDisplayManager {
    override def stroke(config: BeRendererConfig): String = RGBColor.black.toWebStyleString

    override def fill(config: BeRendererConfig): String = RGBColor.black.toWebStyleString

    override def shapeFactory: Bounds[Double] => AppSvgElement = ShapeFactory.buildRectangle

    override def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = BeBlockDisplayManager.calcMinSizeStringBased(config, context, displayedText)
  }
}
