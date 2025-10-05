package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.model.language.*
import contentmanagement.model.language.AppLanguage.*
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.BeProgram.BeProgramTreeContext
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.BeBlockLayoutManager
import util.CodeStringBuilder

object BeMotionBlocks {

  abstract class MotionBlock() extends BeBlock {

    override val evaluatesTo: BeDataType = BeDataType.Unit
  }

  case class BeBlockForward(roleInParent: BeConnectionRole) extends MotionBlock() {

    def toCode(language: ProgrammingLanguage, context: BeProgramTreeContext[String]): String = language match {
      case Python => CodeStringBuilder("turtle.forward").appendParameters(context.accessChildrenResults).toString
      case Java => ???
      case _ => ???
    }


    override def getConnections: List[BeConnection] = List()


    override val layoutManager: BeBlockLayoutManager = BeBlockLayoutManager.SimpleHBoxChildrenLayoutManager(Point[Double](10, 10), new Dimension[Double](50, 30), None)

  }

}