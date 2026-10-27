package it.evadid.vm.io.stringPrinter.python

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmExpressions as X, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.JavaTurtleRuntime.Limits
import it.evadid.vm.simulation.java.JavaTurtleTrace.SourceNode

object JavaTurtlePythonExport {
  val EntryPoint = "java_turtle_run"

  final case class Export(source: String, methods: Map[String, R.MethodId])
  final case class ObservedExport(source: String, methods: Map[String, R.MethodId])

  def doubleLiteral(value: Double): String =
    if value.isNaN then "float(\"nan\")"
    else if value == Double.PositiveInfinity then "float(\"inf\")"
    else if value == Double.NegativeInfinity then "float(\"-inf\")"
    else if value == 0.0 && 1.0 / value == Double.NegativeInfinity then "-0.0"
    else {
      val literal = java.lang.Double.toString(value)
      if literal.exists(char => char == '.' || char == 'e' || char == 'E') then literal else literal + ".0"
    }

  // Keep Java arithmetic and execution limits out of the existing Python/Snap printers.
  def render(program: P.Program): Export = render(program, None)

  def renderObserved(program: P.Program, maxObservations: Int = 4096): ObservedExport = {
    require(maxObservations >= 0 && maxObservations <= 10000, "Invalid observation budget.")
    val result = render(program, Some(maxObservations))
    ObservedExport(result.source, result.methods)
  }

  private def render(program: P.Program, maxObservations: Option[Int]): Export = {
    val writer = new Writer(maxObservations)
    writer.runtime()
    program.root.methods.foreach(writer.method)
    writer.invocation(program.root)
    Export(writer.result, program.root.methods.filterNot(_ eq program.root.entryPoint)
      .map(method => method.binding.originalName -> method.binding.id).toMap)
  }

  private def variable(value: R.Variable): String = s"_variable_${value.id.method.index}_${value.id.index}"
  private def methodName(id: R.MethodId): String = s"_method_${id.index}"

  private def arithmetic(operator: R.BinaryOperator, left: String, right: String, floating: Boolean): String = operator match {
    case R.BinaryOperator.Add => if floating then s"(float($left) + float($right))" else s"_int32($left + $right)"
    case R.BinaryOperator.Subtract => if floating then s"(float($left) - float($right))" else s"_int32($left - $right)"
    case R.BinaryOperator.Multiply => if floating then s"(float($left) * float($right))" else s"_int32($left * $right)"
    case R.BinaryOperator.Divide => if floating then s"_double_divide($left, $right)" else s"_divide($left, $right)"
    case R.BinaryOperator.Remainder => if floating then s"_double_remainder($left, $right)" else s"_remainder($left, $right)"
    case R.BinaryOperator.Less => s"($left < $right)"
    case R.BinaryOperator.LessEqual => s"($left <= $right)"
    case R.BinaryOperator.Greater => s"($left > $right)"
    case R.BinaryOperator.GreaterEqual => s"($left >= $right)"
    case R.BinaryOperator.Equal => s"($left == $right)"
    case R.BinaryOperator.NotEqual => s"($left != $right)"
  }

  private def expression(value: X.Expression, node: Option[SourceNode]): String = {
    def child(index: Int): Option[SourceNode] = node.map(_.child(index.toString))
    val body = value.node match {
      case X.Node.IntLiteral(number) => number.toString
      case X.Node.DoubleLiteral(number) => doubleLiteral(number)
      case X.Node.BooleanLiteral(flag) => if flag then "True" else "False"
      case X.Node.Read(bound, _) => s"${variable(bound)}[0]"
      case X.Node.Update(bound, _, operator, prefix) =>
        val delta = if operator == R.UpdateOperator.Increment then 1 else -1
        val before = if prefix then "True" else "False"
        val floating = if bound.valueType == R.ValueType.DoubleValue then "True" else "False"
        s"_update(${variable(bound)}, $delta, $before, $floating)"
      case X.Node.Group(inner) => expression(inner, child(0))
      case X.Node.Widen(inner) => s"float(${expression(inner, child(0))})"
      case X.Node.Unary(operator, operand) =>
        val inner = expression(operand, child(0))
        operator match {
          case R.UnaryOperator.Plus => inner
          case R.UnaryOperator.Negate => if value.valueType == R.ValueType.DoubleValue then s"(-$inner)" else s"_int32(-$inner)"
          case R.UnaryOperator.Not => s"(not $inner)"
        }
      case X.Node.Binary(operator, left, right) =>
        arithmetic(operator, expression(left, child(0)), expression(right, child(1)), left.valueType == R.ValueType.DoubleValue || right.valueType == R.ValueType.DoubleValue)
      case X.Node.ShortCircuit(operator, left, right) =>
        val token = if operator == R.ShortCircuitOperator.And then "and" else "or"
        s"(${expression(left, child(0))} $token ${expression(right, child(1))})"
    }
    node match {
      case None => s"_value(lambda: $body)"
      case Some(node) =>
        val path = node.path.map(part => ujson.write(ujson.Str(part))).mkString("(", ", ", ",)")
        val label = s"(${node.method.index}, $path)"
        s"_value(_invocation, $label, lambda: $body)"
    }
  }

  private def assignment(operator: R.AssignmentOperator, left: String, right: String, floating: Boolean): String = operator match {
    case R.AssignmentOperator.Set => right
    case R.AssignmentOperator.Add => arithmetic(R.BinaryOperator.Add, left, right, floating)
    case R.AssignmentOperator.Subtract => arithmetic(R.BinaryOperator.Subtract, left, right, floating)
    case R.AssignmentOperator.Multiply => arithmetic(R.BinaryOperator.Multiply, left, right, floating)
    case R.AssignmentOperator.Divide => arithmetic(R.BinaryOperator.Divide, left, right, floating)
    case R.AssignmentOperator.Remainder => arithmetic(R.BinaryOperator.Remainder, left, right, floating)
  }

  private class Writer(maxObservations: Option[Int]) {
    private val output = new StringBuilder
    private var indent = 0

    def result: String = output.result()

    private def line(text: String): Unit = {
      output.append("    " * indent).append(text).append('\n')
    }

    private def nested(body: => Unit): Unit = {
      indent += 1
      body
      indent -= 1
    }

    def runtime(): Unit = {
      val observationState = maxObservations.fold("")(_ => """    import struct as _struct
    _observations = []
    _observation_truncated = False
    _next_invocation = 0
""")
      val report = "{\"status\": status, \"problem\": problem, \"commands\": _commands, \"steps\": _steps}"
      val result = maxObservations.fold(report)(_ =>
        s"{\"execution\": $report, \"observations\": _observations, \"truncated\": _observation_truncated}")
      val evaluate = maxObservations.fold("""    def _value(evaluate):
        _gate()
        return evaluate()
""") { limit => s"""    def _value(invocation, node, evaluate):
        nonlocal _observation_truncated
        _gate()
        value = evaluate()
        if len(_observations) >= $limit:
            _observation_truncated = True
        else:
            if type(value) is bool:
                encoded = {"kind": "bool", "value": value}
            elif type(value) is int:
                encoded = {"kind": "int", "value": str(value)}
            else:
                encoded = {"kind": "double", "value": "nan" if _math.isnan(value) else _struct.pack(">d", value).hex()}
            _observations.append({"invocation": invocation, "method": node[0], "path": list(node[1]), "value": encoded})
        return value
""" }
      val invocationCounter = maxObservations.fold("")(_ => ", _next_invocation")
      val invocationResult = maxObservations.fold("")(_ => """        invocation = _next_invocation
        _next_invocation += 1
        return invocation
""")
      output.append(s"""def $EntryPoint(method=None, arguments=(), *, max_steps=${Limits.MaxSteps}, max_commands=${Limits.MaxCommands}, max_call_depth=${Limits.MaxCallDepth}, max_block_depth=${Limits.MaxBlockDepth}, is_cancelled=lambda: False):
    import math as _math
    _commands = []
    _steps = 0
    _call_depth = 0
${observationState}
    class _Stop(Exception):
        def __init__(self, status, problem=None):
            self.status = status
            self.problem = problem

    def _result(status, problem=None):
        return $result

    def _gate():
        nonlocal _steps
        if is_cancelled():
            raise _Stop("Cancelled")
        if _steps >= max_steps:
            raise _Stop("LimitExceeded")
        _steps += 1

${evaluate}
    def _int32(value):
        return (value + 2147483648) % 4294967296 - 2147483648

    def _update(cell, delta, prefix, floating):
        old = cell[0]
        updated = old + delta if floating else _int32(old + delta)
        cell[0] = updated
        return updated if prefix else old

    def _divide(left, right):
        if right == 0:
            raise _Stop("Failed", "DivisionByZero")
        quotient = abs(left) // abs(right)
        return _int32(-quotient if (left < 0) != (right < 0) else quotient)

    def _remainder(left, right):
        return _int32(left - _divide(left, right) * right)

    def _double_divide(left, right):
        left, right = float(left), float(right)
        if right == 0.0:
            if left == 0.0 or _math.isnan(left):
                return float("nan")
            return _math.copysign(float("inf"), left) * _math.copysign(1.0, right)
        return left / right

    def _double_remainder(left, right):
        left, right = float(left), float(right)
        if _math.isnan(left) or _math.isnan(right) or _math.isinf(left) or right == 0.0:
            return float("nan")
        return _math.fmod(left, right)

    def _enter():
        nonlocal _call_depth$invocationCounter
        _gate()
        if _call_depth >= max_call_depth:
            raise _Stop("LimitExceeded")
        _call_depth += 1
${invocationResult}
    def _leave():
        nonlocal _call_depth
        _call_depth -= 1

    def _block(depth):
        _gate()
        if depth > max_block_depth:
            raise _Stop("LimitExceeded")

    def _loop():
        _gate()
        return True

    def _command(name, value):
        _gate()
        if len(_commands) >= max_commands:
            raise _Stop("LimitExceeded")
        if not _math.isfinite(value):
            raise _Stop("Failed", "NonFiniteCommand")
        _commands.append([name, value])

    def _matches(value, kind):
        if kind == "boolean":
            return type(value) is bool
        if kind == "double":
            return type(value) is float or type(value) is int and -2147483648 <= value <= 2147483647
        return type(value) is int and -2147483648 <= value <= 2147483647

    _limits = ((max_steps, 1, ${Limits.MaxSteps}), (max_commands, 0, ${Limits.MaxCommands}), (max_call_depth, 1, ${Limits.MaxCallDepth}), (max_block_depth, 1, ${Limits.MaxBlockDepth}))
    if any(type(value) is not int or not lower <= value <= upper for value, lower, upper in _limits):
        return _result("Failed", "InvalidLimits")
""")
      indent = 1
    }

    def method(value: P.Method): Unit = {
      line("")
      val bindings = value.binding.parameters.filterNot(_.variable.valueType == R.ValueType.MainArguments)
      val parameters = bindings.map(parameter => variable(parameter.variable)).mkString(", ")
      val initial = bindings.map { parameter =>
        val name = variable(parameter.variable)
        if parameter.variable.valueType == R.ValueType.DoubleValue then s"float($name)" else name
      }
      line(s"def ${methodName(value.binding.id)}($parameters):")
      nested {
        line(if maxObservations.isDefined then "_invocation = _enter()" else "_enter()")
        bindings.zip(initial).foreach { (parameter, converted) =>
          line(s"${variable(parameter.variable)} = [$converted]")
        }
        line("try:")
        nested { block(value.body, 1, Option.when(maxObservations.isDefined)(SourceNode(value.binding.id, Vector("body")))) }
        line("finally:")
        nested { line("_leave()") }
      }
    }

    private def block(value: P.Block, depth: Int, node: Option[SourceNode]): Unit = {
      line(s"_block($depth)")
      statements(value, depth, node)
    }

    private def statements(value: P.Block, depth: Int, parent: Option[SourceNode]): Unit = value.statements.zipWithIndex.foreach { (statement, index) =>
      val node = parent.map(_.child(index.toString))
      def child(role: String): Option[SourceNode] = node.map(_.child(role))
      line("_gate()")
      statement.node match {
        case P.Node.Empty => ()
        case P.Node.Return => line("return")
        case P.Node.Declare(bound, _, initial) =>
          val value = initial.map(value => expression(value.expression, child("initial"))).getOrElse("None")
          line(s"${variable(bound)} = [$value]")
        case P.Node.Assign(bound, _, operator, value) =>
          val target = s"${variable(bound)}[0]"
          line(s"$target = ${assignment(operator, target, expression(value.expression, child("value")), bound.valueType == R.ValueType.DoubleValue)}")
        case P.Node.Evaluate(value) => line(expression(value.expression, child("expression")))
        case P.Node.Call(target, arguments) =>
          val values = arguments.zipWithIndex.map((value, index) =>
            expression(value.expression, child("argument").map(_.child(index.toString)))).mkString(", ")
          target match {
            case P.CallTarget.Helper(method) => line(s"${methodName(method.id)}($values)")
            case P.CallTarget.Turtle(command) =>
              val name = if command == R.TurtleCommand.Forward then "forward" else "right"
              line(s"_command(\"$name\", $values)")
          }
        case P.Node.If(condition, positive, negative) =>
          line(s"if ${expression(condition.expression, child("condition"))}:")
          nested { block(positive, depth + 1, child("then")) }
          negative.foreach { body =>
            line("else:")
            nested { block(body, depth + 1, child("else")) }
          }
        case P.Node.While(condition, body) =>
          line(s"while _loop() and ${expression(condition.expression, child("condition"))}:")
          nested { block(body, depth + 1, child("body")) }
        case P.Node.For(init, condition, update, body) =>
          line(s"_block(${depth + 1})")
          statements(init, depth + 1, child("init"))
          val test = condition.map(value => s" and ${expression(value.expression, child("condition"))}").getOrElse("")
          line(s"while _loop()$test:")
          nested {
            block(body, depth + 2, child("body"))
            statements(update, depth + 1, child("update"))
          }
      }
    }

    def invocation(root: P.Root): Unit = {
      line("")
      line("_methods = {")
      nested {
        root.methods.filterNot(_ eq root.entryPoint).foreach { method =>
          val types = method.binding.parameters.map(parameter =>
            parameter.variable.valueType match {
              case R.ValueType.BooleanValue => "\"boolean\""
              case R.ValueType.DoubleValue => "\"double\""
              case _ => "\"int\""
            })
          val tuple = if types.isEmpty then "()" else types.mkString("(", ", ", ",)")
          line(s"${method.binding.id.index}: (${methodName(method.binding.id)}, $tuple),")
        }
      }
      line("}")
      line("if type(arguments) not in (tuple, list):")
      nested { line("return _result(\"Failed\", \"InvalidInvocation\")") }
      line("if method is None:")
      nested {
        line("if arguments:")
        nested { line("return _result(\"Failed\", \"InvalidInvocation\")") }
        line(s"_target = ${methodName(root.entryPoint.binding.id)}")
      }
      line("else:")
      nested {
        line("if type(method) is not int or method not in _methods:")
        nested { line("return _result(\"Failed\", \"InvalidInvocation\")") }
        line("_target, _types = _methods[method]")
        line("if len(arguments) != len(_types) or not all(_matches(value, kind) for value, kind in zip(arguments, _types)):")
        nested { line("return _result(\"Failed\", \"InvalidInvocation\")") }
        line("arguments = tuple(float(value) if kind == \"double\" else value for value, kind in zip(arguments, _types))")
      }
      line("try:")
      nested {
        line("_target(*arguments)")
        line("return _result(\"Completed\")")
      }
      line("except _Stop as problem:")
      nested { line("return _result(problem.status, problem.problem)") }
    }
  }
}
