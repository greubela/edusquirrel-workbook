package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.model.language.*
import contentmanagement.model.language.AppLanguage.*
import interactionPlugins.blockEnvironment.programming.BeProgram.BeProgramTreeContext
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.{BeBlockLayoutManager, ShapeFactory}
import interactionPlugins.blockEnvironment.programming.{BeBlock, BeDataType}
import util.CodeStringBuilder

case class BeBlockFunctionDefinition(name: LanguageMap[ProgrammingLanguage], parameter: List[FunctionParameter], returnVale: BeDataType) extends BeBlock {

  override def evaluatesTo: BeDataType = BeDataType.Unit

  def toCode(language: ProgrammingLanguage, context: BeProgramTreeContext[String]): String = {
    language match {
      case Python => {
        val builder = new CodeStringBuilder("def " + name.getInLanguage(Python) + "():").changeIntLevel(1)
        context.accessChildrenResults.foreach(builder.appendNextLine)
        builder.changeIntLevel(-1)
        builder.appendNextLine("main()")
        builder.toString
      }
      case Java => {
        ???
      }
      case BeStorageLanguage => {
        ???
      }
    }
  }


  override def getConnections: List[BeConnection] = List()

  override val layoutManager: BeBlockLayoutManager = BeBlockLayoutManager.SimpleVBoxChildrenLayoutManager(Point[Double](0, 30), new Dimension[Double](75, 30), Some(ShapeFactory.buildStarterShape))

  override def roleInParent: BeConnectionRole = FunctionDefinition


}

object BeBlockFunctionDefinition {

  def starterBlock(): BeBlockFunctionDefinition = BeBlockFunctionDefinition(LanguageMap.universalMap("main"), List(), BeDataType.Unit)

}
