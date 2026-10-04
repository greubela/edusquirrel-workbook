package it.evadid.vm.controlflow
import upickle.default.*
case class ControlFlowInfo
(
  controlFlowParentElements: List[ControlFlowType], controlFlowThisElement: ControlFlowType,
) derives ReadWriter{
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
