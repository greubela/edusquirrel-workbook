package it.evadid.vm.io.stringPrinter.python

import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R, JavaTurtleVmExpressions as X, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.JavaTurtleRuntime.Limits

object JavaTurtlePythonExport {
  val EntryPoint = "java_turtle_run"

  final case class Export(source: String, methods: Map[String, R.MethodId])

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
  def render(program: P.Program): Export = {
    val writer = new Writer
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

  private def expression(value: X.Expression): String = {
    val body = value.node match {
      case X.Node.IntLiteral(number) => number.toString
      case X.Node.DoubleLiteral(number) => doubleLiteral(number)
      case X.Node.BooleanLiteral(flag) => if flag then "True" else "False"
      case X.Node.Read(bound, _) => variable(bound)
      case X.Node.Group(inner) => expression(inner)
      case X.Node.Widen(inner) => s"float(${expression(inner)})"
      case X.Node.Unary(operator, operand) =>
        val inner = expression(operand)
        operator match {
          case R.UnaryOperator.Plus => inner
          case R.UnaryOperator.Negate => if value.valueType == R.ValueType.DoubleValue then s"(-$inner)" else s"_int32(-$inner)"
          case R.UnaryOperator.Not => s"(not $inner)"
        }
      case X.Node.Binary(operator, left, right) =>
        arithmetic(operator, expression(left), expression(right), left.valueType == R.ValueType.DoubleValue || right.valueType == R.ValueType.DoubleValue)
      case X.Node.ShortCircuit(operator, left, right) =>
        val token = if operator == R.ShortCircuitOperator.And then "and" else "or"
        s"(${expression(left)} $token ${expression(right)})"
    }
    s"_value(lambda: $body)"
  }

  private def assignment(operator: R.AssignmentOperator, left: String, right: String, floating: Boolean): String = operator match {
    case R.AssignmentOperator.Set => right
    case R.AssignmentOperator.Add => arithmetic(R.BinaryOperator.Add, left, right, floating)
    case R.AssignmentOperator.Subtract => arithmetic(R.BinaryOperator.Subtract, left, right, floating)
    case R.AssignmentOperator.Multiply => arithmetic(R.BinaryOperator.Multiply, left, right, floating)
    case R.AssignmentOperator.Divide => arithmetic(R.BinaryOperator.Divide, left, right, floating)
    case R.AssignmentOperator.Remainder => arithmetic(R.BinaryOperator.Remainder, left, right, floating)
  }

  private class Writer {
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
      output.append(s"""def $EntryPoint(method=None, arguments=(), *, max_steps=${Limits.MaxSteps}, max_commands=${Limits.MaxCommands}, max_call_depth=${Limits.MaxCallDepth}, max_block_depth=${Limits.MaxBlockDepth}, is_cancelled=lambda: False, trace_invocations=False):
    import math as _math
    import struct as _struct
    _commands = []
    _steps = 0
    _call_depth = 0
    _active = []
    _calls = {}
    _drawing = {}
    _max_depth = 0
    _observed_method = None
    _observed_active = []
    _invocations = []
    _truncated = False

    class _Stop(Exception):
        def __init__(self, status, problem=None):
            self.status = status
            self.problem = problem

    def _result(status, problem=None):
        calls = {"methods": [[method, *_calls[method]] for method in sorted(_calls)], "maxDepth": _max_depth}
        drawing = {"methods": [[method, *_drawing[method]] for method in sorted(_drawing)]}
        result = {"status": status, "problem": problem, "commands": _commands, "steps": _steps, "calls": calls, "drawing": drawing}
        if _observed_method is not None:
            result["invocations"] = {"method": _observed_method, "truncated": _truncated, "activations": _invocations}
        return result

    def _gate():
        nonlocal _steps
        if is_cancelled():
            raise _Stop("Cancelled")
        if _steps >= max_steps:
            raise _Stop("LimitExceeded")
        _steps += 1

    def _value(evaluate):
        _gate()
        return evaluate()

    def _int32(value):
        return (value + 2147483648) % 4294967296 - 2147483648

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

    def _typed(value):
        if type(value) is bool:
            return ["b", value]
        if type(value) is int:
            return ["i", value]
        return ["d", _struct.pack(">d", value).hex()]

    def _enter(method, arguments):
        nonlocal _call_depth, _max_depth, _truncated
        _gate()
        if _call_depth >= max_call_depth:
            raise _Stop("LimitExceeded")
        _call_depth += 1
        counts = _calls.setdefault(method, [0, 0])
        counts[0] += 1
        counts[1] += int(method in _active)
        _active.append(method)
        _max_depth = max(_max_depth, _call_depth)
        invocation = None
        if method == _observed_method:
            if len(_invocations) >= ${Limits.MaxInvocations}:
                _truncated = True
            else:
                invocation = len(_invocations)
                parent = _observed_active[-1] if _observed_active else -1
                _invocations.append([parent, [_typed(value) for value in arguments], len(_commands), -1])
            _observed_active.append(invocation)
        return invocation

    def _leave(method, invocation, completed):
        nonlocal _call_depth
        if invocation is not None and completed:
            _invocations[invocation][3] = len(_commands)
        if method == _observed_method:
            _observed_active.pop()
        _active.pop()
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
        if name == "forward" and value != 0.0:
            active = {}
            for method in _active:
                active[method] = active.get(method, 0) + 1
            for method, count in active.items():
                drawing = _drawing.setdefault(method, [0, 0])
                drawing[0] += 1
                drawing[1] += int(count > 1)

    def _matches(value, kind):
        if kind == "boolean":
            return type(value) is bool
        if kind == "double":
            return type(value) is float or type(value) is int and -2147483648 <= value <= 2147483647
        return type(value) is int and -2147483648 <= value <= 2147483647

    _limits = ((max_steps, 1, ${Limits.MaxSteps}), (max_commands, 0, ${Limits.MaxCommands}), (max_call_depth, 1, ${Limits.MaxCallDepth}), (max_block_depth, 1, ${Limits.MaxBlockDepth}))
    if any(type(value) is not int or not lower <= value <= upper for value, lower, upper in _limits):
        return _result("Failed", "InvalidLimits")
    if type(trace_invocations) is not bool:
        return _result("Failed", "InvalidInvocation")
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
      val arguments = if initial.isEmpty then "()" else initial.mkString("(", ", ", ",)")
      line(s"def ${methodName(value.binding.id)}($parameters):")
      nested {
        line(s"_invocation = _enter(${value.binding.id.index}, $arguments if trace_invocations else ())")
        line("_completed = True")
        line("try:")
        nested { block(value.body, 1) }
        line("except BaseException:")
        nested {
          line("_completed = False")
          line("raise")
        }
        line("finally:")
        nested { line(s"_leave(${value.binding.id.index}, _invocation, _completed)") }
      }
    }

    private def block(value: P.Block, depth: Int): Unit = {
      line(s"_block($depth)")
      statements(value, depth)
    }

    private def statements(value: P.Block, depth: Int): Unit = value.statements.foreach { statement =>
      line("_gate()")
      statement.node match {
        case P.Node.Empty => ()
        case P.Node.Return => line("return")
        case P.Node.Declare(bound, _, initial) =>
          val value = initial.map(value => expression(value.expression)).getOrElse("None")
          line(s"${variable(bound)} = $value")
        case P.Node.Assign(bound, _, operator, value) =>
          val target = variable(bound)
          line(s"$target = ${assignment(operator, target, expression(value.expression), bound.valueType == R.ValueType.DoubleValue)}")
        case P.Node.Call(target, arguments) =>
          val values = arguments.map(value => expression(value.expression)).mkString(", ")
          target match {
            case P.CallTarget.Helper(method) => line(s"${methodName(method.id)}($values)")
            case P.CallTarget.Turtle(command) =>
              val name = if command == R.TurtleCommand.Forward then "forward" else "right"
              line(s"_command(\"$name\", $values)")
          }
        case P.Node.If(condition, positive, negative) =>
          line(s"if ${expression(condition.expression)}:")
          nested { block(positive, depth + 1) }
          negative.foreach { body =>
            line("else:")
            nested { block(body, depth + 1) }
          }
        case P.Node.While(condition, body) =>
          line(s"while _loop() and ${expression(condition.expression)}:")
          nested { block(body, depth + 1) }
        case P.Node.For(init, condition, update, body) =>
          line(s"_block(${depth + 1})")
          statements(init, depth + 1)
          val test = condition.map(value => s" and ${expression(value.expression)}").getOrElse("")
          line(s"while _loop()$test:")
          nested {
            block(body, depth + 2)
            statements(update, depth + 1)
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
      line("if trace_invocations:")
      nested { line(s"_observed_method = ${root.entryPoint.binding.id.index} if method is None else method") }
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
