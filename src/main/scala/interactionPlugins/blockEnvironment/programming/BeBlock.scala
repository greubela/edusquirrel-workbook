package interactionPlugins.blockEnvironment.programming


import contentmanagement.model.AppFont
import contentmanagement.model.geometry.{Dimension, Point}
import contentmanagement.model.language.*
import interactionPlugins.blockEnvironment.programming.BeProgram.*
import interactionPlugins.blockEnvironment.programming.connection.*
import interactionPlugins.blockEnvironment.programming.rendering.*

trait BeBlock {

  def evaluatesTo: BeDataType

  def toCode(language: ProgrammingLanguage, context: BeProgramTreeContext[String]): String

  def getConnections: List[BeConnection]

  def roleInParent: BeConnectionRole

  def layoutManager: BeBlockLayoutManager

}


object BeBlock {


}