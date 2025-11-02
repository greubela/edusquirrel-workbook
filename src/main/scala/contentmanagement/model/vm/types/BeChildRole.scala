package contentmanagement.model.vm.types

import contentmanagement.model.vm.code.defining.BeDefineVariable


sealed trait BeChildRole {

}

object BeChildRole {
  case class FunctionParameter(nr: Int) extends BeChildRole

  case class FunctionReturnValue(nr: Int) extends BeChildRole

  case class BodySequence(bodyNr: Int) extends BeChildRole
  
  case class ExpressionInSequence(nr: Int) extends BeChildRole

  case object NoRole extends BeChildRole

  case class ValueForVariable(associatedVariable: BeDefineVariable) extends BeChildRole

  case class RecentlyInsertedInto(intoRole: BeChildRole)

  case object ConditionInControlStructure extends BeChildRole
}