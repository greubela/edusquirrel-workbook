package contentmanagement.model.vm.types

import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineFunction}

sealed trait BeScope {

}

object BeScope {

  case class GlobalScope() extends BeScope

  case class InFunctionScope(funcDef: BeDefineFunction) extends BeScope

  case class InClassScope(classDef: BeDefineClass) extends BeScope

  case class InExpressionScope(expr: BeExpression) extends BeScope

}


