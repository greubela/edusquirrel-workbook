package it.evadid.vm.controlflow

sealed trait ControlFlowType() {

 // def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType]

}

object ControlFlowType {

  object ControlFlowStart extends ControlFlowType {
   // override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = List()
  }

  trait ControlFlowContinuation extends ControlFlowType {
    //override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = myStack.controlFlowParentElements
  }

  object ControlFlowJump extends ControlFlowType

  object ControlFlowDown extends ControlFlowContinuation

  object ControlFlowUp extends ControlFlowContinuation

  // changing templates
  trait ControlFlowChangingType extends ControlFlowType

  sealed trait ControlFlowBranchingType(additionalPaths: List[ControlFlowType]) extends ControlFlowChangingType {
  //  override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = myStack.controlFlowParentElements ++ additionalPaths
  }

  sealed trait ControlFlowUnionType() extends ControlFlowChangingType {
   // override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = myStack.controlFlowParentElements.reverse.tail.reverse
  }

  sealed trait ControlFlowCrossType(replaceLastWith: ControlFlowType) extends ControlFlowChangingType {
    /*override def calculateChildrenControlFlowStack(myStack: ControlFlowInfo): List[ControlFlowType] = {
      myStack.controlFlowParentElements.reverse.tail.reverse ++ List(replaceLastWith)
    }*/
  }

  /* If/Else */
  sealed trait IfElseType extends ControlFlowType

  object IfElseBranch extends IfElseType, ControlFlowBranchingType(List(ControlFlowDown))

  case object IfElseCross extends IfElseType, ControlFlowCrossType(ControlFlowDown)

  case object IfElseUnion extends IfElseType, ControlFlowUnionType

  /* Repeat/Nr */

  sealed trait RepeatType extends ControlFlowType, ControlFlowChangingType

  case object RepeatBranch extends RepeatType, ControlFlowBranchingType(List(ControlFlowUp))

  case object RepeatUnion extends RepeatType, ControlFlowUnionType

}
