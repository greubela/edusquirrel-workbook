package interactionPlugins.blockEnvironment.programming.connection

import interactionPlugins.blockEnvironment.programming.BeDataType
import interactionPlugins.blockEnvironment.programming.blocks.defineStructure.BeBlockDefineVariable


sealed trait BeValueRole {
}

case class FunctionParameter(nr: Int, evaluatesTo: BeDataType) extends BeValueRole

case class FunctionReturnValue(nr: Int, evaluatesTo: BeDataType) extends BeValueRole

/*
case object FunctionDefinition extends BeConnectionRole


trait FunctionDefinitionRole extends BeConnectionRole {
}


case object FunctionReturnValue extends FunctionDefinitionRole

case object FunctionBody extends FunctionDefinitionRole

trait ControlStructureBooleanExpressionRole extends BeConnectionRole {

}

case object ControlStructureBooleanExpression extends ControlStructureBooleanExpressionRole

case object ControlStructureBody extends ControlStructureBooleanExpressionRole


sealed trait NoneRole extends BeConnectionRole

case object TextElement extends NoneRole

case object RootElement extends NoneRole
*/