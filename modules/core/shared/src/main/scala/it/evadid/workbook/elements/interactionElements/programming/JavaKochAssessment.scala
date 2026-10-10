package it.evadid.workbook.elements.interactionElements.programming
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.core.datastructures.vectorShapes.svg.TurtleTraceComparison
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleInvocationTrace, JavaTurtleRuntime as T}

object JavaKochAssessment {
  case class KochCase(depth: Int, length: Double)

  enum Verdict {
    case Passed, InvalidCase, InvalidMethod, MissingEvidence, InvalidEvidence, WrongDrawing, RecursionMismatch
    case Incomplete(status: T.Status)
    case InvalidDrawing(problem: TurtleTraceComparison.Failure)
  }

  case class Result(verdict: Verdict, comparison: Option[TurtleTraceComparison.Result] = None)

  def assess(program: P.Program, method: R.MethodId, example: KochCase, execution: T.Execution): Result = {
    if !valid(example) then return Result(Verdict.InvalidCase)
    val selected = program.root.methods.find(_.binding.id == method).filter(_ ne program.root.entryPoint)
    if !selected.exists(_.binding.parameters.map(_.variable.valueType) == Vector(R.ValueType.IntValue, R.ValueType.DoubleValue)) then
      return Result(Verdict.InvalidMethod)
    if execution.status != T.Status.Completed then return Result(Verdict.Incomplete(execution.status))
    if execution.callEvidence.isEmpty || execution.drawingEvidence.isEmpty || execution.invocationEvidence.isEmpty then
      return Result(Verdict.MissingEvidence)
    val calls = execution.callEvidence.get
    val drawing = execution.drawingEvidence.get
    val trace = execution.invocationEvidence.get
    if !validEvidence(execution, calls, drawing, program.root.methods.map(_.binding.id).toSet, method) then
      return Result(Verdict.InvalidEvidence)
    if trace.method != method || !JavaTurtleInvocationTrace.valid(trace, calls, execution.commands.size, true) ||
      trace.activations.exists(row => !parameters(row.arguments)) ||
      trace.activations.head.arguments != Vector(E.Value.IntValue(example.depth), E.Value.DoubleValue(example.length)) then
      return Result(Verdict.InvalidEvidence)
    val commands = execution.commands.map(command => motion(
      if command.command == R.TurtleCommand.Forward then "forward" else "right", command.value))
    val tolerance = 0.01 / math.pow(3.0, example.depth)
    TurtleTraceComparison.compare(reference(example), commands, example.length, tolerance) match {
      case Left(problem) => Result(Verdict.InvalidDrawing(problem))
      case Right(comparison) if !comparison.matches => Result(Verdict.WrongDrawing, Some(comparison))
      case Right(comparison) =>
        val methodCalls = calls.methods.find(_.method == method).get
        val methodDrawing = drawing.methods.find(_.method == method)
        val forwards = execution.commands.count(command => command.command == R.TurtleCommand.Forward && command.value != 0.0)
        val expectedCalls = (math.pow(4.0, example.depth + 1).toInt - 1) / 3
        val recursive = methodCalls.calls == expectedCalls && methodCalls.recursiveCalls == expectedCalls - 1 &&
          calls.maxDepth >= example.depth + 1 && methodDrawing.exists(entry => entry.forwardCommands == forwards &&
            entry.recursiveForwardCommands == (if example.depth == 0 then 0 else forwards))
        Result(if recursive && subdivision(trace, execution.commands) then Verdict.Passed else Verdict.RecursionMismatch, Some(comparison))
    }
  }

  private def valid(example: KochCase): Boolean = {
    if example.depth < 0 || example.depth > 4 || !example.length.isFinite || example.length <= 0.0 then false
    else {
      val leaf = (0 until example.depth).foldLeft(example.length)((length, _) => length / 3.0)
      val expected = 1.0 / math.pow(3.0, example.depth)
      leaf > 0.0 && math.abs(leaf / example.length - expected) <= math.ulp(expected) * 64.0
    }
  }

  private def motion(name: String, value: Double): TurtleCommand[Double] = TurtleCommand(name, List(value))

  private def parameters(arguments: Vector[E.Value]): Boolean = arguments match {
    case Vector(E.Value.IntValue(_), E.Value.DoubleValue(_)) => true
    case _ => false
  }

  private def subdivision(trace: T.InvocationEvidence, commands: Vector[T.Command]): Boolean = {
    if trace.truncated then return false
    val rows = trace.activations
    val children = rows.indices.drop(1).groupMap(index => rows(index).parent.get)(identity)
    var index = 0
    while index < rows.size do {
      val row = rows(index)
      val Vector(E.Value.IntValue(depth), E.Value.DoubleValue(length)) = row.arguments: @unchecked
      val descendants = children.getOrElse(index, Vector.empty)
      if depth < 0 || !length.isFinite || length <= 0.0 then return false
      val local = commands.slice(row.firstCommand, row.lastCommand.get)
      var angle = 0.0
      local.foreach { command =>
        if command.command == R.TurtleCommand.TurnRight then angle = (angle + command.value % 360.0) % 360.0
      }
      if math.min(math.abs(angle), 360.0 - math.abs(angle)) > 1e-8 then return false
      if depth == 0 then {
        if descendants.nonEmpty then return false
        val actual = local.map(command => motion(if command.command == R.TurtleCommand.Forward then "forward" else "right", command.value))
        if !TurtleTraceComparison.compare(Vector(motion("forward", length)), actual, length, 0.01).exists(_.matches) then return false
      } else {
        if descendants.size != 4 then return false
        var cursor = row.firstCommand
        var childIndex = 0
        while childIndex < descendants.size do {
          val child = rows(descendants(childIndex))
          val Vector(E.Value.IntValue(childDepth), E.Value.DoubleValue(childLength)) = child.arguments: @unchecked
          val expected = length / 3.0
          if childDepth != depth - 1 || !childLength.isFinite || childLength <= 0.0 ||
            math.abs(childLength - expected) > math.ulp(expected) * 4.0 ||
            commands.slice(cursor, child.firstCommand).exists(nonzeroForward) then return false
          cursor = child.lastCommand.get
          childIndex += 1
        }
        if commands.slice(cursor, row.lastCommand.get).exists(nonzeroForward) then return false
      }
      index += 1
    }
    true
  }

  private def nonzeroForward(command: T.Command): Boolean =
    command.command == R.TurtleCommand.Forward && command.value != 0.0

  private def reference(example: KochCase): Vector[TurtleCommand[Double]] = {
    var commands = Vector(motion("forward", example.length))
    for _ <- 0 until example.depth do commands = commands.flatMap { command =>
      if command.name != "forward" then Vector(command)
      else {
        val part = motion("forward", command.args.head / 3.0)
        Vector(part, motion("right", -60.0), part, motion("right", 120.0), part, motion("right", -60.0), part)
      }
    }
    commands
  }

  private def validEvidence(execution: T.Execution, calls: T.CallEvidence, drawing: T.DrawingEvidence,
      known: Set[R.MethodId], selected: R.MethodId): Boolean = {
    if execution.steps <= 0 || execution.steps > T.Limits.MaxSteps || execution.commands.size > T.Limits.MaxCommands ||
      execution.commands.size > execution.steps || execution.commands.exists(!_.value.isFinite) ||
      calls.methods.isEmpty || calls.methods.size > known.size || calls.maxDepth < 1 || calls.maxDepth > T.Limits.MaxCallDepth then return false
    val callIds = calls.methods.map(_.method.index)
    if callIds != callIds.distinct.sorted || !calls.methods.exists(_.method == selected) || calls.methods.exists(entry =>
      !known.contains(entry.method) || entry.calls < 1 || entry.calls > execution.steps || entry.recursiveCalls < 0 ||
        entry.recursiveCalls >= entry.calls || entry.recursiveCalls > 0 && calls.maxDepth < 2) then return false
    val totalCalls = calls.methods.map(_.calls.toLong).sum
    if totalCalls > execution.steps || calls.maxDepth > totalCalls then return false
    val forwards = execution.commands.count(command => command.command == R.TurtleCommand.Forward && command.value != 0.0)
    val entered = calls.methods.map(entry => entry.method -> entry).toMap
    val drawingIds = drawing.methods.map(_.method.index)
    drawing.methods.size <= calls.methods.size && drawingIds == drawingIds.distinct.sorted && drawing.methods.forall { entry =>
      entered.get(entry.method).exists(method => entry.forwardCommands > 0 && entry.forwardCommands <= forwards &&
        entry.recursiveForwardCommands >= 0 && entry.recursiveForwardCommands <= entry.forwardCommands &&
        (entry.recursiveForwardCommands == 0 || method.recursiveCalls > 0))
    } && (forwards == 0 || drawing.methods.exists(_.forwardCommands == forwards)) &&
      drawing.methods.map(_.forwardCommands.toLong).sum <= forwards.toLong * calls.maxDepth
  }
}
