package it.evadid.vm.controlflow

import upickle.default.*

sealed trait ControlFlowType() derives ReadWriter {

  // def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType]

}

object ControlFlowType {

  case class ControlFlowStart() extends ControlFlowType derives upickle.default.ReadWriter {
    // override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = List()
  }

  sealed trait ControlFlowContinuation extends ControlFlowType derives ReadWriter {
    //override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = myStack.controlFlowParentElements
  }

  case class ControlFlowJump() extends ControlFlowType derives ReadWriter

  case class ControlFlowDown() extends ControlFlowContinuation derives ReadWriter

  case class ControlFlowUp() extends ControlFlowContinuation derives ReadWriter

  // changing templates
  sealed trait ControlFlowChangingType extends ControlFlowType derives ReadWriter

  sealed trait ControlFlowBranchingType(additionalPaths: List[ControlFlowType]) extends ControlFlowChangingType derives ReadWriter {
    //  override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = myStack.controlFlowParentElements ++ additionalPaths
  }

  sealed trait ControlFlowUnionType() extends ControlFlowChangingType derives ReadWriter {
    // override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = myStack.controlFlowParentElements.reverse.tail.reverse
  }

  sealed trait ControlFlowCrossType(replaceLastWith: ControlFlowType) extends ControlFlowChangingType derives ReadWriter {
    /*override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = {
      myStack.controlFlowParentElements.reverse.tail.reverse ++ List(replaceLastWith)
    }*/
  }

  /* If/Else */
  sealed trait IfElseType extends ControlFlowType derives ReadWriter

  case class IfElseBranch() extends IfElseType, ControlFlowBranchingType(List(ControlFlowDown())) derives upickle.default.ReadWriter

  case class IfElseCross() extends IfElseType, ControlFlowCrossType(ControlFlowDown()) derives upickle.default.ReadWriter

  case class IfElseUnion() extends IfElseType, ControlFlowUnionType derives upickle.default.ReadWriter

  /* Repeat/Nr */

  sealed trait RepeatType extends ControlFlowType, ControlFlowChangingType derives ReadWriter

  case class RepeatBranch() extends RepeatType, ControlFlowBranchingType(List(ControlFlowUp())) derives upickle.default.ReadWriter

  case class RepeatUnion() extends RepeatType, ControlFlowUnionType derives upickle.default.ReadWriter

}
