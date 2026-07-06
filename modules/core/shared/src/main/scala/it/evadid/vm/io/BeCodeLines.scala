package it.evadid.vm.io

import it.evadid.vm.code.BeExpression
import it.evadid.vm.controlflow.ControlFlowType
import it.evadid.vm.types.*

case class BeCodeLines(lines: List[BeCodeLine]) extends Iterable[BeCodeLine] {

  def map(func: BeCodeLine => BeCodeLine): BeCodeLines = {
    BeCodeLines(lines.map(func))
  }

  def appendNewLines(newLines: BeCodeLines): BeCodeLines = {
    BeCodeLines(lines ++ newLines.lines.map(_.changeLineNr(_ + lines.size)))
  }

  def appendNewLines(newLines: List[BeCodeLine]): BeCodeLines = {
    appendNewLines(BeCodeLines(newLines))
  }

  def appendNewLine(newLine: BeCodeLine): BeCodeLines = {
    appendNewLines(List(newLine))
  }

  def appendNewLine(lineExpression: BeExpression, lineRole: BeChildRole, scope: BeScope, controlFlowStack: List[ControlFlowType]): BeCodeLines = {
    val newLine = BeExpressionLine(lines.size, lineExpression, lineRole, scope, controlFlowStack)
    appendNewLine(newLine)
  }

  def tryContinueWithLine(lineExpression: BeExpression, newRole: BeChildRole): BeCodeLines = {
    lines.lastOption.match {
      case Some(last) => {
        val newLine = BeExpressionLine(lines.size, lineExpression, newRole, last.scope, last.controlFlowStack)
        appendNewLine(newLine)
      }
      case None => {
        println("[WARN] try Continue line on empty BeCodeLines. Ignoring expression: " + lineExpression)
        this
      }
    }

  }

  override def iterator: Iterator[BeCodeLine] = lines.iterator
}
