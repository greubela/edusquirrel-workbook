package interactionPlugins.blockEnvironment.programming.blocks.logic.parent

import contentmanagement.model.geometry.{Bounds, Dimension, Point}
import contentmanagement.model.language.*
import contentmanagement.model.language.AppLanguage.*
import contentmanagement.webElements.svg.AppSvgElement
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.logic.BeBlockLogic
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*
import util.CodeStringBuilder

object BeMotionBlocks {

  abstract class MotionBlock() extends BeBlockParent {

    override val evaluatesTo: BeDataType = BeDataType.Unit

    override val layoutManager: BeBlockDisplayManager = new BeBlockDisplayManager {
      override def shapeFactory: Bounds[Double] => AppSvgElement = BeDataType.Unit.shapeFactory

      override def calcMinSize(config: BeRendererConfig, context: BeBlockContext[Dimension[Double]]): Dimension[Double] =
        BeBlockParentDisplayManager.calculateMinSizeForHBox(config, context, Dimension[Double](50, 20), new Dimension[Double](20,5), true)

      override def stroke(config: BeRendererConfig): String = config.colorPalette.blues(2).toWebStyleString

      override def fill(config: BeRendererConfig): String = config.colorPalette.blues(3).toWebStyleString

      override def calcDisplaySize(config: BeRendererConfig, minSizeTree: BeDimensionTree, context: BeBlockContext[Dimension[Double]]): Dimension[Double] = {
        val siblingDimensions = context.traversalInfoForSiblingsInParent.map(curSibling => minSizeTree.getData(curSibling.curPosition).get)
        val maxSiblingWidth = siblingDimensions.map(_.width).max
        Dimension[Double](maxSiblingWidth, minSizeTree.getData(context.curPosition).get.height)
      }
    }

    override val parentDisplayManager: BeBlockParentDisplayManager = new BeBlockParentDisplayManager {
      override def calcRelativeChildOffsets(config: BeRendererConfig, childrenBefore: List[(BeBlock, Point[Double], Dimension[Double])], curChild: BeBlock): Point[Double] =
        BeBlockParentDisplayManager.calculateRelativeOffsetsAsHBox(config, childrenBefore, curChild, new Dimension[Double](20, 5), true)
    }
  }

  case class BeBlockForward(roleInParent: BeConnectionRole) extends MotionBlock() {

    def toCode(language: ProgrammingLanguage, context: BeLogicContext[String]): String = language match {
      case Python => CodeStringBuilder("turtle.forward").appendParameters(context.accessChildrenResults).toString
      case Java => ???
      case _ => ???
    }


    override def getConnections: List[BeConnection] = List()


  }

}