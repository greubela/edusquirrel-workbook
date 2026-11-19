package it.evadid.vm.conversion.pythonexpression

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.vm.BeProgram
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.parsed.SymbolTable
import it.evadid.vm.conversion.parsed.SymbolTable.KnownStructure
import it.evadid.vm.conversion.expressionpython.PythonEntryPoint
import it.evadid.vm.conversion.parsed.{ParsedExpression, ParsedFunctionKind, ParsedParameter, ParsedProgram, ParsedProgramBuilder, ParsedStatement, ParsedType}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType
import it.evadid.vm.parsing.python.clean.model.PythonType.*

/** Python front end: fastparse syntax tree → [[ParsedProgram]] → shared expression tree.
  *
  * A `def main(): …` entry point called at the end is the main sequence, see [[PythonEntryPoint]].
  */
object PythonToExpression {
  private val builder = new ParsedProgramBuilder(Python)

  def program(source: String): BeProgram = BeProgram(PythonEntryPoint.inline(BeStartProgram(builder.build(parse(source)).expression)))

  /** The program with the definitions the builder bound, for analyses that need them. */
  final case class Details(definedClasses: List[BeDefineClass], definedFunctions: List[BeDefineFunction],
      definedVariables: List[BeDefineVariable], currentlyKnownStructures: SymbolTable.CurrentlyKnownStructures, codeExpression: BeSequence)

  def parseWithDetails(source: String, knownStructures: Seq[KnownStructure] = SymbolTable.defaultKnownStructures): Details = {
    val built = new ParsedProgramBuilder(Python, knownStructures).build(parse(source))
    val expression = PythonEntryPoint.inline(BeStartProgram(built.expression)).startSequence.getOrElse(built.expression)
    Details(built.context.definedClasses, built.context.definedFunctions, built.context.definedVariables,
      built.context.currentStructures, expression)
  }

  /** Unreadable source is retained as one unparsable statement rather than discarded. */
  def parse(source: String): ParsedProgram =
    PythonAstParserSimple.parse(source).fold(
      error => ParsedProgram(if source.trim.isEmpty then Nil else Seq(S.Unsupported(source.trim, error.getMessage, unparsable = true))),
      program => ParsedProgram(statements(program.statements.map(_.statement))))

  /** Imports that only resolve annotations and the turtle library emitted by the Python printer. */
  private def isBridgeImport(statement: PythonImportStatement): Boolean = statement match {
    case PyPlainImportStatement("turtle") => true
    case PyImportFromStatement("typing", List(PyTarget("Any", Nil, None, _)), false) => true
    case PyImportFromStatement("datetime", List(PyTarget("date", Nil, None, _)), false) => true
    case _ => false
  }

  private def statements(values: Seq[PyStatement]): Seq[ParsedStatement] = values.flatMap(statement)

  private def statement(value: PyStatement): Seq[ParsedStatement] = value match {
    case PyEmptyStatement => Nil
    case PyPassStatement => Seq(S.Pass)
    case PyCommentStatement(text) => Seq(S.Comment(text))
    case PyUnparsableStatement(source) if source.trim.startsWith("global ") => Seq(S.Unsupported(source.trim, "global declaration", unparsable = false))
    case PyUnparsableStatement(source) => Seq(S.Unsupported(source.trim, unreadable(source.trim), unparsable = true))
    case statement: PythonImportStatement if isBridgeImport(statement) => Nil
    case other: PythonImportStatement => Seq(S.Unsupported(s"import ${other.name}", "Imports are not supported", unparsable = false))
    case PyReturnStatement(expression) => Seq(S.Return(expression.map(this.expression)))
    case PyIfStatement(condition, thenBlock, elseBlock) =>
      Seq(S.If(expression(condition), block(thenBlock), block(elseBlock)))
    case PyWhileStatement(condition, body, None) => Seq(S.While(expression(condition), block(body)))
    case loop: PyForStatement => Seq(forLoop(loop))
    case definition: PyFunctionDef =>
      if definition.decorators.nonEmpty || definition.isAsync then Seq(S.Unsupported(definition.source, "Decorators are not supported outside classes", unparsable = false))
      else Seq(S.FunctionDef(definition.name, parameters(definition.parameters), definition.returnType.map(parsedType),
        block(definition.block), ParsedFunctionKind.Function))
    case definition: PyClassDef => Seq(classDef(definition))
    case statement: PyTryStatement => Seq(S.Unsupported(statement.source, "try/except statements are currently unsupported", unparsable = true))
    case PySimpleAssignment(target, Some(value)) => Seq(assign(target, expression(value)))
    case PySimpleAssignment(target, None) if target.locationString.isEmpty =>
      Seq(S.Declare(target.identifier, target.typeHint.map(parsedType).getOrElse(ParsedType.AnyValue)))
    case PyAugAssignment(target, operator, value) =>
      Seq(assign(target.copy(typeHint = None), E.Binary(operator.stripSuffix("="), targetExpression(target), expression(value))))
    case expression: PyExpression => Seq(S.Evaluate(this.expression(expression)))
    case other => Seq(S.Unsupported(other.toString, s"${other.getClass.getSimpleName.stripPrefix("Py")} is not supported", unparsable = false))
  }

  private def unreadable(source: String): String =
    Seq("if" -> "If statements must end with ':'", "while" -> "While statements must end with ':'", "for" -> "For statements must use range(...)",
        "else" -> "Else statements must end with ':'")
      .collectFirst { case (keyword, message) if source == keyword || source.startsWith(keyword + " ") || source.startsWith(keyword + "(") => message }
      .getOrElse("Python source cannot be read")

  private def block(value: PyExecutionBlock): Seq[ParsedStatement] = statements(value.statements)

  private def targetExpression(target: PyTarget): ParsedExpression =
    if target.locationString.isEmpty then path(Seq(target.identifier))
    else E.Member(path(target.locationString), target.identifier)

  private def path(names: Seq[String]): ParsedExpression =
    names.tail.foldLeft[ParsedExpression](if names.head == "self" then E.Self else E.Name(names.head))(E.Member(_, _))

  private def assign(target: PyTarget, value: ParsedExpression): ParsedStatement =
    if target.sliceExpr.nonEmpty then S.Unsupported(s"${target.name}[...] = ...", "Subscript assignment is not supported", unparsable = false)
    else if target.locationString.isEmpty then S.Assign(target.identifier, target.typeHint.map(parsedType), value)
    else S.AssignMember(path(target.locationString), target.identifier, value)

  private def parameters(values: Seq[PyAssignment]): Seq[ParsedParameter] = values.collect {
    case PySimpleAssignment(target, _) => ParsedParameter(target.identifier, target.typeHint.map(parsedType))
  }

  /** `for _ in range(n)` repeats; a named counter counts with Snap's inclusive end (the step is not modelled). */
  private def forLoop(loop: PyForStatement): ParsedStatement = (loop.elementExpression, loop.inExpression) match {
    case (PyTarget("_", Nil, None, _), PyFunctionCall(PyTarget("range", Nil, None, _), List(PythonLiteral(count, _: PYTHON_INTEGER))))
        if loop.elseBlock.isEmpty && !loop.isAsync && count.toIntOption.nonEmpty =>
      S.Repeat(count.toInt, block(loop.bodyBlock))
    case (PyTarget(name, Nil, None, _), PyFunctionCall(PyTarget("range", Nil, None, _), arguments))
        if loop.elseBlock.isEmpty && !loop.isAsync && arguments.size >= 1 && arguments.size <= 3 =>
      val (start, stop) = if arguments.size == 1 then (E.Literal("0"), arguments.head) else (expression(arguments.head), arguments(1))
      S.For(name, start, inclusiveEnd(stop), block(loop.bodyBlock))
    case _ => S.Unsupported("for ...", "For statements must use range(...)", unparsable = true)
  }

  private def inclusiveEnd(stop: PyExpression): ParsedExpression = stop match {
    case PyOperationBinary(inner, "+", PythonLiteral("1", _)) => expression(inner)
    case PythonLiteral(value, _: PYTHON_INTEGER) if value.toIntOption.nonEmpty => E.Literal((value.toInt - 1).toString)
    case other => E.Binary("-", expression(other), E.Literal("1"))
  }

  // Classes: inheritance, decorators other than @staticmethod and other class-body statements are unsupported.

  private def classDef(definition: PyClassDef): ParsedStatement = {
    def unsupported(reason: String) = S.Unsupported(definition.source, reason, unparsable = false)
    val members = definition.block.statements.filter {
      case PyEmptyStatement | PyPassStatement | _: PyCommentStatement => false
      case _ => true
    }
    val methods = members.collect { case method: PyFunctionDef => method }
    val classFields = members.collect { case assignment: PySimpleAssignment if assignment.target.locationString.isEmpty => assignment }
    if definition.parameters.nonEmpty then unsupported("Inheritance")
    else if members.size != methods.size + classFields.size || methods.exists(method => method.decorators.exists(_ != "staticmethod") || method.isAsync) then
      unsupported("Class-body statements or decorators")
    else {
      val parsedMethods = methods.map { method =>
        val kind = if method.decorators.contains("staticmethod") then ParsedFunctionKind.StaticMethod
          else if method.name == "__init__" then ParsedFunctionKind.Constructor else ParsedFunctionKind.Method
        val all = parameters(method.parameters)
        if kind != ParsedFunctionKind.StaticMethod then
          require(all.headOption.exists(_.name == "self"), s"Method '${definition.name}.${method.name}' requires an explicit self parameter.")
        val explicit = if kind == ParsedFunctionKind.StaticMethod then all else all.tail
        S.FunctionDef(method.name, explicit, method.returnType.map(parsedType), block(method.block), kind)
      }
      val instanceFields = methods.zip(parsedMethods).flatMap { (method, parsed) => selfAssignments(method.block.statements).map { assignment =>
        S.Field(assignment.target.identifier, assignment.target.typeHint.map(parsedType), None,
          assignment.value.map(value => expression(value) -> parsed.parameters))
      } }
      val defaults = classFields.map(field => S.Field(field.target.identifier, field.target.typeHint.map(parsedType), field.value.map(expression)))
      S.ClassDef(definition.name, defaults ++ instanceFields, parsedMethods, definition.source)
    }
  }

  private def selfAssignments(values: Seq[PyStatement]): Seq[PySimpleAssignment] = values.flatMap {
    case assignment @ PySimpleAssignment(PyTarget(_, List("self"), None, _), _) => Seq(assignment)
    case PyIfStatement(_, thenBlock, elseBlock) => selfAssignments(thenBlock.statements) ++ selfAssignments(elseBlock.statements)
    case PyWhileStatement(_, body, _) => selfAssignments(body.statements)
    case loop: PyForStatement => selfAssignments(loop.bodyBlock.statements)
    case _ => Nil
  }

  // Expressions

  private def expression(value: PyExpression): ParsedExpression = value match {
    case PythonLiteral(_, _: PYTHON_NONE) => E.NoneValue
    case PythonLiteral(raw, _: (PYTHON_LIST[?] | PYTHON_SET[?] | PYTHON_DICT[?, ?] | PYTHON_ARRAY[?])) => E.Unsupported(raw)
    case PythonLiteral(raw, _) => E.Literal(raw)
    case target: PyTarget if target.sliceExpr.nonEmpty => E.Unsupported(s"${target.name}[...]")
    case target: PyTarget => targetExpression(target)
    case PyOperationUnary(operator, operand) => E.Unary(operator, expression(operand))
    case PyOperationBinary(left, operator, right) => E.Binary(operator, expression(left), expression(right))
    case PyFunctionCall(target, arguments) => E.Call(target.locationString :+ target.identifier, arguments.map(expression))
    case PyCallExpression(callee, arguments) =>
      names(callee) match {
        case Some(qualified) => E.Call(qualified, arguments.map(expression))
        case None => callee match {
          case PyAttributeAccess(receiver, name) => E.MethodCall(expression(receiver), name, arguments.map(expression))
          case _ => E.Unsupported("(...)(...)")
        }
      }
    case PyAttributeAccess(receiver, name) => E.Member(expression(receiver), name)
    case PyRawExpression(source) => E.Unsupported(source)
    case NamedExpression(name, _) => E.Unsupported(s"$name := ...")
    case PySubscript(_, _) => E.Unsupported("[...]")
    case other => E.Unsupported(other.getClass.getSimpleName)
  }

  /** A dotted name such as `turtle.forward` or `self.walker.walk`. */
  private def names(value: PyExpression): Option[Seq[String]] = value match {
    case PyTarget(identifier, location, None, _) => Some(location :+ identifier)
    case PyAttributeAccess(receiver, name) => names(receiver).map(_ :+ name)
    case _ => None
  }

  private def parsedType(value: PythonType[?]): ParsedType = value match {
    case _: (PYTHON_INTEGER | PYTHON_INTEGER_HEX | PYTHON_INTEGER_OCT | PYTHON_INTEGER_BIN | PYTHON_FLOAT) => ParsedType.Numeric
    case _: PYTHON_BOOL => ParsedType.Boolean
    case _: PYTHON_STRING => ParsedType.Text
    case _: PYTHON_NONE => ParsedType.Unit
    case PYTHON_UNION_TYPE(a, b) => ParsedType.Union(Seq(parsedType(a), parsedType(b)))
    case named: PYTHON_UNPARSABLE_TYPE => named.str.toLowerCase match {
      case "number" | "double" => ParsedType.Numeric
      case "boolean" => ParsedType.Boolean
      case "string" => ParsedType.Text
      case "date" | "datetime" => ParsedType.Date
      case "void" | "unit" => ParsedType.Unit
      case "any" => ParsedType.AnyValue
      case _ => ParsedType.Named(named.str)
    }
    case _ => ParsedType.AnyValue
  }
}
