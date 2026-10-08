package it.evadid.vm.simulation.java

import it.evadid.vm.parsing.java.turtle.JavaTurtleInputLimits
import JavaTurtleRuntime as T

object JavaTurtleInvocationTrace {
  def valid(trace: T.InvocationEvidence, calls: T.CallEvidence, commandCount: Int, completed: Boolean): Boolean = {
    val entered = calls.methods.find(_.method == trace.method).map(_.calls).getOrElse(0)
    val rows = trace.activations
    if rows.size != entered.min(T.Limits.MaxInvocations) || trace.truncated != (entered > T.Limits.MaxInvocations) then return false
    if rows.isEmpty then return !completed
    if rows.head.parent.nonEmpty || rows.head.firstCommand != 0 || rows.head.lastCommand.isDefined != completed ||
      completed && rows.head.lastCommand != Some(commandCount) then return false
    if !calls.methods.exists(entry => entry.method == trace.method && entry.recursiveCalls == entered - 1) then return false
    val types = rows.head.arguments.map(_.ordinal)
    var active = List.empty[Int]
    var index = 0
    while index < rows.size do {
      val row = rows(index)
      if row.arguments.size > JavaTurtleInputLimits.MaxParameters || row.arguments.map(_.ordinal) != types ||
        row.firstCommand < 0 || row.firstCommand > commandCount ||
        row.lastCommand.exists(end => end < row.firstCommand || end > commandCount) ||
        completed && row.lastCommand.isEmpty then return false
      if index > 0 then {
        if row.parent.isEmpty then return false
        val parent = row.parent.get
        if parent < 0 || parent >= index then return false
        while active.nonEmpty && active.head != parent do {
          if !rows(active.head).lastCommand.exists(_ <= row.firstCommand) then return false
          active = active.tail
        }
        if active.isEmpty then return false
        val enclosing = rows(parent)
        if enclosing.firstCommand > row.firstCommand ||
          enclosing.lastCommand.exists(end => row.lastCommand.isEmpty || row.lastCommand.exists(_ > end)) then return false
      }
      active = index :: active
      if active.size > calls.maxDepth then return false
      index += 1
    }
    val open = rows.indices.filter(rows(_).lastCommand.isEmpty)
    open.forall(active.contains)
  }
}
