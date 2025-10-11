package interactionPlugins.blockEnvironment.programming.blocks.logic

import contentmanagement.model.geometry.{Bounds, Dimension}
import contentmanagement.model.language.AppLanguage.{Java, Python}
import contentmanagement.model.language.ProgrammingLanguage
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*

case class BeBlockValue(override val evaluatesTo: BeDataType, override val roleInParent: BeConnectionRole, associatedValue: String) extends BeBlockLogic {

  def toCode(language: ProgrammingLanguage, context: BeLogicContext[String]): String =  language match {
    case Python => associatedValue
    case Java => ???
    case _ => ???
  }

  override val layoutManager: BeBlockDisplayManager = new BeBlockDisplayManager {
    override def shapeFactory: Bounds[Double] => AppSvgElement = evaluatesTo.shapeFactory

    def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = BeBlockDisplayManager.calcMinSizeStringBased(config, context, associatedValue.toString)


    override def stroke(config: BeRendererConfig): String = config.colorPalette.grayscale(2).toWebStyleString

    override def fill(config: BeRendererConfig): String = config.colorPalette.grayscale(3).toWebStyleString
  }
  

}
