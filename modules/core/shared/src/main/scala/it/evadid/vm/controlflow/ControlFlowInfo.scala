package it.evadid.vm.controlflow

case class ControlFlowInfo
(
  controlFlowParentElements: List[ControlFlowType], controlFlowThisElement: ControlFlowType,
) {
/*
  def createInfoForNextLine(controlFlowNextLineExpression: ControlFlowType): ControlFlowInfo = {
    val newParents = controlFlowThisElement.calculateChildrenControlFlowStack(this)
    ControlFlowInfo(newParents, controlFlowNextLineExpression)
  }

  def createInfoForNextLine(controlFlowInfoNextLine: ControlFlowInfo): ControlFlowInfo = {
    val newParents = controlFlowThisElement.calculateChildrenControlFlowStack(this)
    ControlFlowInfo(newParents ++ controlFlowInfoNextLine.controlFlowParentElements, controlFlowInfoNextLine.controlFlowThisElement)
  }

 */
}
