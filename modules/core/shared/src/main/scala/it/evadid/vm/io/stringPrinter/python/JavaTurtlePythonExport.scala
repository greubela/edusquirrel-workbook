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
      output.append(s"""def $EntryPoint(method=None, arguments=(), *, max_steps=${Limits.MaxSteps}, max_commands=${Limits.MaxCommands}, max_call_depth=${Limits.MaxCallDepth}, max_block_depth=${Limits.MaxBlockDepth}, is_cancelled=lambda: False):
    import math as _math
    _commands = []
    _steps = 0
    _call_depth = 0

    class _Stop(Exception):
        def __init__(self, status, problem=None):
            self.status = status
            self.problem = problem

    def _result(status, problem=None):
        return {"status": status, "problem": problem, "commands": _commands, "steps": _steps}

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

    def _enter():
        nonlocal _call_depth
        _gate()
        if _call_depth >= max_call_depth:
            raise _Stop("LimitExceeded")
        _call_depth += 1

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
      line(s"def ${methodName(value.binding.id)}($parameters):")
      nested {
        line("_enter()")
        line("try:")
        nested { block(value.body, 1) }
        line("finally:")
        nested { line("_leave()") }
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
