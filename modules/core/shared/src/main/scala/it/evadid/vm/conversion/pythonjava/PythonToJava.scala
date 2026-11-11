package it.evadid.vm.conversion.pythonjava

import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleResolution as R
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType.*
import it.evadid.workbook.elements.interactionElements.programming.state.*

import it.evadid.vm.conversion.javaediting.JavaEditingMetadata.*

object PythonToJava {
  private def string(value: PyExpression): String = value match {
    case literal: PythonLiteral[?] if literal.literalType.isInstanceOf[PYTHON_STRING] =>
      val raw = literal.literalValue
      if raw.startsWith("\"") then ujson.read(raw).str
      else {
        ensure(raw.startsWith("'") && raw.endsWith("'"), "Use an ordinary quoted string literal.")
        val decoded = new StringBuilder
        var offset = 1
        while offset < raw.length - 1 do {
          if raw.charAt(offset) != '\\' then decoded.append(raw.charAt(offset))
          else {
            offset += 1
            ensure(offset < raw.length - 1, "Close the string escape.")
            decoded.append(raw.charAt(offset) match {
              case 'n' => '\n'
              case 'r' => '\r'
              case 't' => '\t'
              case '\\' => '\\'
              case '\'' => '\''
              case '"' => '"'
              case _ => reject("This string escape is not part of the Java editing subset.")
            })
          }
          offset += 1
        }
        decoded.result()
      }
    case _ => reject("Expected a text literal in editing metadata or a typed operation.")
  }
  private def pyCall(value: PyExpression): (String, Vector[PyExpression]) = value match {
    case PyFunctionCall(target, args) if target.sliceExpr.isEmpty => target.name -> args.toVector
    case PyCallExpression(PyTarget(name, location, None, _), args) => (location :+ name).mkString(".") -> args.toVector
    case PyCallExpression(PyAttributeAccess(PyTarget(receiver, Nil, None, _), name), args) => s"$receiver.$name" -> args.toVector
    case _ => reject("Use a direct Java method, Turtle command or typed arithmetic helper.")
  }
  private def boolean(value: PyExpression): Boolean = value match {
    case PythonLiteral("True", _: PYTHON_BOOL) => true
    case PythonLiteral("False", _: PYTHON_BOOL) => false
    case _ => reject("Expected a boolean update flag.")
  }

  def fromPython(source: String): Either[String, ProgrammingStateJavaString] = attempt {
    bounded(source)
    ensure(source.linesIterator.forall(line => line.takeWhile(_ == ' ').length <= 256), "This Python editing program is too deeply indented.")
    val ast = PythonAstParserSimple.parse(source).fold(error => reject(error.getMessage), identity)
    val top = ast.statements.map(_.statement).filterNot(_ == PyEmptyStatement)
    val metadataRows = top.collect { case PySimpleAssignment(PyTarget(MetadataVariable, Nil, None, _), Some(value)) => value }
    ensure(metadataRows.size == 1, "Exactly one Java editing metadata declaration is required.")
    val meta = readMetadata(string(metadataRows.head))
    val definitions = top.collect { case definition: PyFunctionDef => definition }
    ensure(definitions.size == meta.methods.size && definitions.map(_.name).distinct.size == definitions.size,
      "Each Java method must have exactly one Python definition.")
    val imports = top.collect { case PyPlainImportStatement(name) => name }
    ensure(imports.toSet == Set("turtle", "java_turtle") && imports.size == 2, "Keep the turtle and java_turtle imports.")
    val other = top.filterNot(statement => statement.isInstanceOf[PyFunctionDef] || statement.isInstanceOf[PyPlainImportStatement] ||
      (statement match { case PySimpleAssignment(PyTarget(MetadataVariable, Nil, None, _), _) => true; case _ => false }))
    ensure(other.size == 1 && pyCall(other.head.asInstanceOf[PyExpression]) == ("main" -> Vector.empty), "Keep exactly one top-level main() call.")
    def expression(node: PyExpression, method: Method): Value = node match {
      case PythonLiteral("True", _: PYTHON_BOOL) => Value("true", R.ValueType.BooleanValue)
      case PythonLiteral("False", _: PYTHON_BOOL) => Value("false", R.ValueType.BooleanValue)
      case literal: PythonLiteral[?] if literal.literalType.isInstanceOf[PYTHON_INTEGER] || literal.literalType.isInstanceOf[PYTHON_FLOAT] => number(literal.literalValue)
      case PyOperationUnary("-", literal: PythonLiteral[?]) if literal.literalValue == "2147483648" => number("-2147483648")
      case PySubscript(PyTarget(name, Nil, None, _), List(PythonLiteral("0", _: PYTHON_INTEGER))) =>
        Value(name, method.variable(name).kind)
      case PyTarget(name, Nil, Some(PythonLiteral("0", _: PYTHON_INTEGER)), _) => Value(name, method.variable(name).kind)
      case PyOperationUnary("not", value) => logical("!", expression(value, method), None)
      case PyOperationUnary(op @ ("+" | "-"), value) =>
        val inner = expression(value, method)
        ensure(inner.kind == R.ValueType.DoubleValue || value.isInstanceOf[PythonLiteral[?]], "Use the typed int helper for integer negation.")
        Value(s"($op ${inner.code})", inner.kind)
      case PyOperationBinary(left, op, right) if Set("and", "or", "<", "<=", ">", ">=", "==", "!=")(op) =>
        logical(if op == "and" then "&&" else if op == "or" then "||" else op, expression(left, method), Some(expression(right, method)))
      case PyOperationBinary(left, op @ ("+" | "-" | "*"), right) =>
        val a = expression(left, method); val b = expression(right, method)
        ensure(a.kind == R.ValueType.DoubleValue || b.kind == R.ValueType.DoubleValue, "Use the typed helper for int arithmetic so overflow is preserved.")
        ensure(numeric(a.kind) && numeric(b.kind), "Use numeric arithmetic operands.")
        Value(s"(${a.code} $op ${b.code})", R.ValueType.DoubleValue)
      case other =>
        val (name, args) = pyCall(other)
        name match {
          case "float" =>
            ensure(args.size == 1, "float needs one argument.")
            val value = expression(args.head, method)
            ensure(numeric(value.kind), "Widen only a numeric Java value.")
            widen(value)
          case "java_turtle.int_op" | "java_turtle.double_op" =>
            ensure(args.size == 3, "A typed operation needs an operation name and two operands.")
            typedOperation(string(args(0)), expression(args(1), method), expression(args(2), method), name.endsWith("double_op"))
          case "java_turtle.compare" =>
            ensure(args.size == 4, "A typed comparison needs an operation, two operands and a floating-point flag.")
            typedComparison(string(args(0)), expression(args(1), method), expression(args(2), method), boolean(args(3)))
          case "java_turtle.update" =>
            ensure(args.size == 4, "An update needs a local cell, delta and two flags.")
            val target = args.head match { case PyTarget(name, Nil, None, _) => method.variable(name); case _ => reject("Update a local Java cell.") }
            val delta = args(1) match {
              case PythonLiteral("1", _: PYTHON_INTEGER) => 1
              case PyOperationUnary("-", PythonLiteral("1", _: PYTHON_INTEGER)) => -1
              case _ => reject("Use an increment or decrement of one.")
            }
            val prefix = boolean(args(2)); val floating = boolean(args(3))
            ensure(numeric(target.kind) && floating == (target.kind == R.ValueType.DoubleValue), "The update type does not match its variable.")
            val token = if delta == 1 then "++" else "--"
            Value(if prefix then s"($token${target.name})" else s"(${target.name}$token)", target.kind)
          case _ => reject(s"Unsupported Python expression '$name'.")
        }
    }
    def body(block: PyExecutionBlock, method: Method, level: Int): Vector[String] = {
      val prefix = "    " * level
      block.statements.toVector.flatMap {
        case PyPassStatement | PyEmptyStatement => Vector.empty
        case PyReturnStatement(None) => Vector(prefix + "return;")
        case PySimpleAssignment(target @ PyTarget(name, Nil, None, _), Some(value)) =>
          val variable = method.variable(name)
          ensure(!variable.parameter, "Do not replace a parameter cell after its initialization.")
          val (called, args) = pyCall(value)
          ensure(called == "java_turtle.cell" && args.size == 1, "Declare Java locals with java_turtle.cell(value).")
          val init = args.head match {
            case PythonLiteral("None", _: PYTHON_NONE) => ""
            case inner =>
              val initial = expression(inner, method)
              ensure(assignable(initial.kind, variable.kind), "The local initializer has the wrong Java type.")
              " = " + initial.code
          }
          Vector(prefix + s"${kindName(variable.kind)} $name$init;")
        case PySimpleAssignment(PyTarget(name, Nil, Some(PythonLiteral("0", _: PYTHON_INTEGER)), _), Some(value)) =>
          val variable = method.variable(name); val assigned = expression(value, method)
          ensure(assignable(assigned.kind, variable.kind), "The assignment has the wrong Java type.")
          Vector(prefix + s"$name = ${assigned.code};")
        case PyIfStatement(condition, positive, negative) =>
          val checked = expression(condition, method); ensure(checked.kind == R.ValueType.BooleanValue, "Use a boolean if condition.")
          Vector(prefix + s"if (${checked.code}) {") ++ body(positive, method, level + 1) ++ Vector(prefix + "}") ++
            (if negative.statements.isEmpty then Vector.empty else Vector(prefix + "else {") ++ body(negative, method, level + 1) ++ Vector(prefix + "}"))
        case PyWhileStatement(condition, repeated, None) =>
          val checked = expression(condition, method); ensure(checked.kind == R.ValueType.BooleanValue, "Use a boolean while condition.")
          Vector(prefix + s"while (${checked.code}) {") ++ body(repeated, method, level + 1) ++ Vector(prefix + "}")
        case node: PyExpression =>
          val (name, args) = pyCall(node)
          if name == "java_turtle.update" then Vector(prefix + expression(node, method).code.stripPrefix("(").stripSuffix(")") + ";")
          else Vector(prefix + call(meta, name match { case "turtle.forward" => "Turtle.forward"; case "turtle.right" => "Turtle.turnRight"; case other => other }, args.map(expression(_, method))))
        case _ => reject("This Python statement cannot be converted without changing its meaning.")
      }
    }
    val bodies = definitions.map { definition =>
      val method = meta.method(definition.name)
      ensure(!definition.isAsync && definition.parameters.size == method.parameters.size, "The method parameter count changed; update its typed declaration first.")
      definition.parameters.zip(method.parameters).foreach { (parameter, expected) =>
        ensure(parameter.target.identifier == expected.name && parameter.target.locationString.isEmpty && parameter.target.sliceExpr.isEmpty && parameter.value.isEmpty &&
          parameter.target.typeHint.exists(_.typenameInCode == pythonType(expected.kind)), "Keep the typed Java parameter declaration.")
      }
      val nonempty = definition.block.statements.filterNot(_ == PyEmptyStatement)
      ensure(nonempty.size >= method.parameters.size, "Keep the parameter cell initialization.")
      nonempty.take(method.parameters.size).zip(method.parameters).foreach { (statement, variable) =>
        statement match {
          case PySimpleAssignment(PyTarget(name, Nil, None, _), Some(value)) if name == variable.name =>
            val (called, args) = pyCall(value)
            ensure(called == "java_turtle.cell" && args == Vector(PyTarget(name)), "Keep each parameter's independent Java cell.")
          case _ => reject("Keep each parameter's independent Java cell.")
        }
      }
      method.name -> body(PyExecutionBlock(nonempty.drop(method.parameters.size)), method, 0)
    }.toMap
    finish(meta, bodies)
  }

}
