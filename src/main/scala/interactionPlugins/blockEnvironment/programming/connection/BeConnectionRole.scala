package interactionPlugins.blockEnvironment.programming.connection

import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.programming.BeDataType


sealed trait BeConnectionRole {

}

case object FunctionDefinition extends BeConnectionRole


trait FunctionDefinitionRole extends BeConnectionRole {
}

case class FunctionParameter(dataType: BeDataType) extends FunctionDefinitionRole

case object FunctionReturnValue extends FunctionDefinitionRole

case object FunctionBody extends FunctionDefinitionRole

trait ControlStructureBooleanExpressionRole extends BeConnectionRole {

}

case object ControlStructureBooleanExpression extends ControlStructureBooleanExpressionRole

case object ControlStructureBody extends ControlStructureBooleanExpressionRole

