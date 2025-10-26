package contentmanagement.model.vm.types

import contentmanagement.model.vm.expressions.defining.BeDefineVariable


sealed trait BeChildRole {

}

object BeChildRole {
  case class FunctionParameter(nr: Int) extends BeChildRole

  case class FunctionReturnValue(nr: Int) extends BeChildRole

  case class BodySequence() extends BeChildRole
  
  case class ExpressionInBody(nr: Int) extends BeChildRole

  case object NoRole extends BeChildRole

  case class ValueForVariable(associatedVariable: BeDefineVariable) extends BeChildRole

  case class RecentlyInsertedInto(intoRole: BeChildRole)

}