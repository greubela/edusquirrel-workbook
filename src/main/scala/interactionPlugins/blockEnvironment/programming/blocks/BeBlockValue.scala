package interactionPlugins.blockEnvironment.programming.blocks

import contentmanagement.model.AppFont
import contentmanagement.model.geometry.Dimension
import contentmanagement.model.language.AppLanguage.{Java, Python}
import contentmanagement.model.language.ProgrammingLanguage
import interactionPlugins.blockEnvironment.programming.*
import interactionPlugins.blockEnvironment.programming.BeProgram.BeProgramTreeContext
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*

case class BeBlockValue(evaluatesTo: BeDataType, roleInParent: BeConnectionRole, associatedValue: Option[String] = None) extends BeBlock {

  override def getConnections: List[BeConnection] = List()

  def toCode(language: ProgrammingLanguage, context: BeProgramTreeContext[String]): String = language match {
    case Python => associatedValue.getOrElse("None")
    case Java => ???
    case _ => ???
  }

  override val layoutManager: BeBlockLayoutManager = BeBlockLayoutManager.simpleStringNodeLayoutManager(associatedValue.getOrElse("[   ]"))


}
