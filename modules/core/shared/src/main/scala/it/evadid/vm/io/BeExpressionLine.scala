package it.evadid.vm.io

import it.evadid.vm.code.BeExpression
import it.evadid.vm.controlflow.ControlFlowType
import it.evadid.vm.types.{BeChildRole, BeScope}

trait BeCodeLine {
  val lineNr: Int
  val controlFlowStack: List[ControlFlowType]
  val scope: BeScope
  val lineExpression: Option[BeExpression]

  def changeLineNr(func: Int => Int): BeCodeLine


}

case class ControlFlowLine(
                            override val lineNr: Int,
                            override val scope: BeScope,
                            override val controlFlowStack: List[ControlFlowType]
                          ) extends BeCodeLine {
  override val lineExpression: Option[BeExpression] = None


  override def changeLineNr(func: Int => Int): BeCodeLine = {
    this.copy(lineNr = func(lineNr))
  }
}

case class BeExpressionLine(
                             override val lineNr: Int,
                             private val expr: BeExpression,
                             val lineRole: BeChildRole,
                             override val scope: BeScope,
                             override val controlFlowStack: List[ControlFlowType]
                           ) extends BeCodeLine {

  override val lineExpression: Option[BeExpression] = Some(expr)

  private def changeScope(func: BeScope => BeScope): BeCodeLine = {
    this.copy(scope = func(scope))
  }

  override def changeLineNr(func: Int => Int): BeCodeLine = {
    this.copy(lineNr = func(lineNr))
  }

  private def changeStackToBeWithin(cft: ControlFlowType): BeCodeLine = {
    this.copy(controlFlowStack = List(cft) ++ controlFlowStack)
  }
  
}

