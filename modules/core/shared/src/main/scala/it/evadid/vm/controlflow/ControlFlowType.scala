package it.evadid.vm.controlflow

sealed trait ControlFlowType() {

}

object ControlFlowType {

  sealed trait ControlFlowChangingType extends ControlFlowType

  /* If/Else */
  sealed trait IfElseType extends ControlFlowType

  case object IfElseBranch extends IfElseType, ControlFlowChangingType

  case object IfElseCross extends IfElseType, ControlFlowChangingType

  case object IfElseUnion extends IfElseType, ControlFlowChangingType

  case class IfElseBody(truePath: Boolean) extends IfElseType

  /* Repeat/Nr */

  sealed trait RepeatNrType


}
