package interactionPlugins.blockEnvironment.programming.blocks.display

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.{BeBlockContext, BeDataType}
import interactionPlugins.blockEnvironment.programming.connection.BeConnectionRole
import interactionPlugins.blockEnvironment.programming.rendering.{BeBlockDisplayManager, BeRendererConfig}

case class BeBlockDummyMultipleTypes(mayEvaluateTo: Set[BeDataType], roleInParent: BeConnectionRole) extends BeBlockDisplay {

  override def layoutManager: BeBlockDisplayManager = new BeBlockDisplayManager {
    override def stroke(config: BeRendererConfig): String = config.colorPalette.reds(2).toWebStyleString

    override def fill(config: BeRendererConfig): String = config.colorPalette.reds(4).toWebStyleString

    override def shapeFactory: Bounds[Double] => AppSvgElement = BeDataType.Unit.shapeFactory

    override def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = new Dimension[Double](50, 10)
  }


}
