package interactionPlugins.blockEnvironment.programming.blocks.logic.parent

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.*
import contentmanagement.model.language.AppLanguage.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.blocks.*
import interactionPlugins.blockEnvironment.programming.blocks.logic.*
import interactionPlugins.blockEnvironment.programming.blocks.logic.parent.BeBlockParent
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import interactionPlugins.blockEnvironment.programming.*
import util.CodeStringBuilder

case class BeBlockFunctionDefinition(name: LanguageMap[ProgrammingLanguage], parameter: List[FunctionParameter], evaluatesTo: BeDataType, layoutManager: BeBlockDisplayManager) extends BeBlockParent {

  def toCode(language: ProgrammingLanguage, context: BeLogicContext[String]): String = {
    language match {
      case Python => {
        val builder = new CodeStringBuilder("def " + name.getInLanguage(Python) + "():").changeIntLevel(1)
        context.accessChildrenResults.foreach(builder.appendNextLine)
        builder.changeIntLevel(-1)
        builder.appendNextLine("main()")
        builder.toString
      }
      case _ => {
        println("BeBlockFunctionDefinition::toCode not implemented")
        ???
      }
    }
  }


  override def getConnections: List[BeConnection] = List(new BeConnection {
    override def connectionRole: BeConnectionRole = FunctionBody

    override def connectionMayEvaluateTo: Set[BeDataType] = Set(BeDataType.Unit)

    override def connectionCardinality: BeConnectionCardinality = BeConnectionCardinality.anyAmount()
  })


  override def roleInParent: BeConnectionRole = FunctionDefinition

  override val parentDisplayManager: BeBlockParentDisplayManager = new BeBlockParentDisplayManager {
    override def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] = {
      BeBlockParentDisplayManager.calculateRelativeOffsetsAsVBox(config, childrenBefore, curChild, new Dimension[Double](0, 100), false)
    }
  }

}

object BeBlockFunctionDefinition {

  def starterBlock(): BeBlockFunctionDefinition = BeBlockFunctionDefinition(LanguageMap.universalMap("main"), List(), BeDataType.Unit, new BeBlockDisplayManager {
    override def shapeFactory: Bounds[Double] => AppSvgElement = ShapeFactory.buildStarterShape

    override def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = new Dimension[Double](150, 100)

    override def stroke(config: BeRendererConfig): String = config.colorPalette.yellows(2).toWebStyleString

    override def fill(config: BeRendererConfig): String = config.colorPalette.yellows(3).toWebStyleString

    override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = {
      val childrenDimension = context.traversalInfoForChildren.map(curChild => minSizeTree.getData(curChild.curPosition).get)
      val maxWidth = childrenDimension.map(_.width).max
      new Dimension[Double](maxWidth, minSizeTree.getData(context.curPosition).get.height)
    }

  })

}