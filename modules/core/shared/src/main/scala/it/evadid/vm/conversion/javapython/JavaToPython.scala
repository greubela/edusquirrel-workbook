package it.evadid.vm.conversion.javapython

import it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport
import it.evadid.vm.conversion.pythonjava.PythonToJava

import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.workbook.elements.interactionElements.programming.state.*

import it.evadid.vm.conversion.javaediting.JavaEditingMetadata.*

object JavaToPython {
  def toPython(program: P.Program): Either[String, ProgrammingStatePythonString] = attempt {
    val (meta, names) = metadata(program)
    def expression(value: R.Expression): String = value match {
      case R.IntLiteral(number) => number.toString
      case R.DoubleLiteral(number) => it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport.doubleLiteral(number)
      case R.BooleanLiteral(flag) => if flag then "True" else "False"
      case R.Read(variable) => s"${names(variable.id)}[0]"
      case R.Update(variable, operator, prefix) =>
        s"java_turtle.update(${names(variable.id)}, ${if operator == R.UpdateOperator.Increment then 1 else -1}, ${if prefix then "True" else "False"}, ${if variable.valueType == R.ValueType.DoubleValue then "True" else "False"})"
      case R.Widen(inner) => s"float(${expression(inner)})"
      case R.Group(inner) => s"(${expression(inner)})"
      case R.Unary(R.UnaryOperator.Not, inner) => s"(not ${expression(inner)})"
      case R.Unary(operator, inner) =>
        s"java_turtle.${if value.valueType == R.ValueType.DoubleValue then "double_op" else "int_op"}(\"${if operator == R.UnaryOperator.Negate then "neg" else "pos"}\", ${expression(inner)}, 0)"
      case R.Binary(operator, left, right) if arithmetic(operator) =>
        s"java_turtle.${if value.valueType == R.ValueType.DoubleValue then "double_op" else "int_op"}(\"${operation(operator)}\", ${expression(left)}, ${expression(right)})"
      case R.Binary(operator, left, right) if numeric(left.valueType) && numeric(right.valueType) =>
        s"java_turtle.compare(\"${comparison(operator)}\", ${expression(left)}, ${expression(right)}, ${if left.valueType == R.ValueType.DoubleValue || right.valueType == R.ValueType.DoubleValue then "True" else "False"})"
      case R.Binary(operator, left, right) => s"(${expression(left)} ${BinaryTokens(operator)} ${expression(right)})"
      case R.ShortCircuit(operator, left, right) => s"(${expression(left)} ${if operator == R.ShortCircuitOperator.And then "and" else "or"} ${expression(right)})"
    }
    def lines(body: R.Block, level: Int): Vector[String] = {
      val prefix = "    " * level
      def blockLines(block: R.Block): Vector[String] = {
        val content = lines(block, level + 1)
        if content.isEmpty then Vector(prefix + "    pass") else content
      }
      body.statements.flatMap {
        case R.Empty => Vector.empty
        case R.Return => Vector(prefix + "return")
        case R.Declare(variable, initial) => Vector(prefix + s"${names(variable.id)} = java_turtle.cell(${initial.fold("None")(value => expression(widened(value, variable.valueType)))})")
        case R.Assign(variable, operator, value) => Vector(prefix + s"${names(variable.id)}[0] = ${expression(assignment(variable, operator, value))}")
        case R.Evaluate(value) => Vector(prefix + expression(value))
        case R.Call(target, args) =>
          val name = target match {
            case R.CallTarget.Helper(id) => program.bindings.source.methods.find(_.id == id).get.name
            case R.CallTarget.Turtle(R.TurtleCommand.Forward) => "turtle.forward"
            case R.CallTarget.Turtle(R.TurtleCommand.TurnRight) => "turtle.right"
          }
          Vector(prefix + s"$name(${args.map(expression).mkString(", ")})")
        case R.If(condition, positive, negative) =>
          Vector(prefix + s"if ${expression(condition)}:") ++ blockLines(positive) ++ negative.toVector.flatMap(block =>
            Vector(prefix + "else:") ++ blockLines(block))
        case R.While(condition, body) => Vector(prefix + s"while ${expression(condition)}:") ++ blockLines(body)
        case R.For(init, condition, update, body) =>
          lines(init, level) ++ Vector(prefix + s"while ${condition.fold("True")(expression)}:") ++
            blockLines(R.Block(body.statements ++ (if completesNormally(body) then update.statements else Vector.empty)))
      }
    }
    val definitions = program.bindings.source.methods.map { method =>
      val description = meta.method(method.name)
      val parameters = description.parameters.map(variable => s"${variable.name}: ${pythonType(variable.kind)}").mkString(", ")
      val copies = description.parameters.map(variable => s"    ${variable.name} = java_turtle.cell(${variable.name})")
      val body = copies ++ lines(method.body, 1)
      s"def ${method.name}($parameters) -> None:\n" + (if body.isEmpty then "    pass" else body.mkString("\n"))
    }
    val source = "import turtle\nimport java_turtle\n" + MetadataVariable + " = " + ujson.Str(meta.json).render() + "\n\n" +
      definitions.mkString("\n\n") + "\n\nmain()\n"
    bounded(source)
    ensure(source.linesIterator.forall(line => line.takeWhile(_ == ' ').length <= 256), "This Python editing program is too deeply indented.")
    PythonToJava.fromPython(source).fold(reject, _ => ProgrammingStatePythonString(source))
  }

}
