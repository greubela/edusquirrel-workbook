package it.evadid.vm.types

import it.evadid.vm.code.defining.BeDefineVariable


sealed trait BeChildRole {

  def withIncrementedNrOrThis: BeChildRole = this

}

object BeChildRole {

  case object NoRole extends BeChildRole

  /* CONTROL FLOW ROLES */
  sealed trait BeChildControlFlowRole extends BeChildRole

  case object IfElseBranch extends BeChildControlFlowRole

  /* SEQUENCE ROLES */
  sealed trait BeChildSequenceRole extends BeChildRole

  case class BodySequence(nr: Int) extends BeChildSequenceRole {
    override def withIncrementedNrOrThis: BeChildRole = BodySequence(nr + 1)
  }

  /* EXPRESSION ROLES */
  sealed trait BeChildExpressionRole extends BeChildRole

  case class ExpressionInSequence(nr: Int) extends BeChildExpressionRole {
    override def withIncrementedNrOrThis: BeChildRole = ExpressionInSequence(nr + 1)

  }

  case object ConditionInControlStructure extends BeChildExpressionRole


  /* DATA ROLES */
  sealed trait BeChildDataRole extends BeChildRole

  case class FunctionParameter(nr: Int) extends BeChildDataRole {
    override def withIncrementedNrOrThis: BeChildRole = FunctionParameter(nr + 1)
  }

  case class FunctionReturnValue(nr: Int) extends BeChildDataRole {
    override def withIncrementedNrOrThis: BeChildRole = FunctionReturnValue(nr + 1)
  }

  case class ValueForVariable(associatedVariable: BeDefineVariable) extends BeChildDataRole

  case object ValueInAssignment extends BeChildDataRole

  /* EDITOR ROLES */
  sealed trait BeChildEditorRole extends BeChildRole

  case class RecentlyInsertedInto(intoRole: BeChildRole) extends BeChildEditorRole


}