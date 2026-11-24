package it.evadid.vm.conversion.pythonexpression

import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.vm.BeProgram
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.parsed.SymbolTable
import it.evadid.vm.conversion.parsed.SymbolTable.KnownStructure
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.expressionpython.PythonEntryPoint
import it.evadid.vm.conversion.parsed.{ParsedExpression, ParsedFunctionKind, ParsedParameter, ParsedProgram, ParsedProgramBuilder, ParsedStatement, ParsedType}
import it.evadid.vm.conversion.parsed.ParsedExpression as E
import it.evadid.vm.conversion.parsed.ParsedStatement as S
import it.evadid.vm.parsing.generic.abstractions.GenericAST
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
  /** Known dependency definitions are libraries, not user code, and are removed before parsing. */
  def parse(rawSource: String): ParsedProgram = {
    // Library definitions are masked rather than removed, so positions match the original text.
    val source = KnownDependencies.maskDefinitions(rawSource, Python)
    PythonAstParserSimple.parse(source).fold(
      error => ParsedProgram(if source.trim.isEmpty then Nil else Seq(S.Unsupported(source.trim, error.getMessage, unparsable = true))),
      program => {
        val parsed = program.statements.map(_.statement).flatMap(withoutMainGuard)
        given ProgramNames = ProgramNames(parsed.flatMap(usedNames).toSet, assignedNames(parsed),
          parsed.collect { case definition: PyClassDef => definition.name }.toSet)
        ParsedProgram(statements(parsed))
      })
  }

  /** `if __name__ == "__main__":` holds whenever the program runs, so its body runs as module
    * statements; a final `main()` in it is then the usual entry point ([[PythonEntryPoint]]).
    */
  private def withoutMainGuard(statement: PyStatement): Seq[PyStatement] = statement match {
    case PyIfStatement(PyOperationBinary(PyTarget("__name__", Nil, None, _), "==", PythonLiteral(raw, _: PYTHON_STRING)), thenBlock, elseBlock)
        if Set("\"__main__\"", "'__main__'")(raw) && elseBlock.statements.isEmpty => thenBlock.statements
    case other => Seq(other)
  }

  /** Imports that only resolve annotations emitted by the Python printer. */
  private def isBridgeImport(statement: PythonImportStatement): Boolean = statement match {
    case PyImportFromStatement("typing", List(PyTarget("Any", Nil, None, _)), false) => true
    case PyImportFromStatement("datetime", List(PyTarget("date", Nil, None, _)), false) => true
    case _ => false
  }

  private def statements(values: Seq[PyStatement])(using ProgramNames): Seq[ParsedStatement] = values.flatMap(statement)

  private def statement(value: PyStatement)(using ProgramNames): Seq[ParsedStatement] = value match {
    case PyEmptyStatement => Nil
    case PyPassStatement => Seq(S.Pass)
    case PyCommentStatement(text) => Seq(S.Comment(text))
    case PyUnparsableStatement(source) if source.trim.startsWith("global ") => Seq(S.Global(globalNames(source)))
    case PyUnparsableStatement(source) => Seq(S.Unsupported(source.trim, unreadable(source.trim), unparsable = true))
    case statement: PythonImportStatement if isBridgeImport(statement) => Nil
    case PyPlainImportStatement(module) if KnownDependencies.providedByPythonModule(module).nonEmpty => Nil
    case PyImportFromStatement(module, _, _) if KnownDependencies.providedByPythonModule(module).nonEmpty => Nil
    case other: PythonImportStatement => Seq(S.Unsupported(s"import ${other.name}", "Imports are not supported", unparsable = false))
    case PyReturnStatement(expression) => Seq(S.Return(expression.map(this.expression)))
    case PyIfStatement(condition, thenBlock, elseBlock) =>
      Seq(S.If(expression(condition), block(thenBlock), block(elseBlock)))
    case PyWhileStatement(condition, body, None) => Seq(S.While(expression(condition), block(body)))
    case loop: PyForStatement => forLoop(loop)
    case definition: PyFunctionDef =>
      if definition.decorators.nonEmpty || definition.isAsync then Seq(S.Unsupported(definition.source, "Decorators are not supported outside classes", unparsable = false))
      else if hasDefaultValues(definition) then Seq(defaultValuesUnsupported(definition))
      else {
        val parsedParameters = parameters(definition.parameters)
        Seq(S.FunctionDef(definition.name, parsedParameters, definition.returnType.map(parsedType),
          hiddenModuleVariable(definition, parsedParameters).toSeq ++ block(definition.block), ParsedFunctionKind.Function))
      }
    case definition: PyClassDef => Seq(classDef(definition))
    case statement: PyTryStatement => Seq(S.Unsupported(statement.source, "try/except statements are currently unsupported", unparsable = false))
    case PySimpleAssignment(_, Some(value)) if turtleObject(value).nonEmpty => Seq(turtleObjectUnsupported(turtleObject(value).get))
    case value: PyExpression if turtleObject(value).nonEmpty => Seq(turtleObjectUnsupported(turtleObject(value).get))
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

  private def block(value: PyExecutionBlock)(using ProgramNames): Seq[ParsedStatement] = statements(value.statements)

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

  /** Java and Snap have no default parameter values, so dropping them would change calls that omit them. */
  private def hasDefaultValues(definition: PyFunctionDef): Boolean =
    definition.parameters.exists { case PySimpleAssignment(_, Some(_)) => true; case _ => false }

  private def defaultValuesUnsupported(definition: PyFunctionDef): ParsedStatement =
    S.Unsupported(s"def ${definition.name}(", s"Default parameter values in ${definition.name} are not supported; pass every value in the call", unparsable = false)

  /** `turtle.Turtle()` and `turtle.Screen()` create objects the shared turtle commands cannot address. */
  private def turtleObject(value: PyExpression)(using program: ProgramNames): Option[String] = {
    val callee = value match {
      case PyFunctionCall(target, _) => Some(target.locationString :+ target.identifier)
      case PyCallExpression(target, _) => names(target)
      case _ => None
    }
    callee.filter {
      case Seq("turtle", "Turtle" | "Screen") => true
      case Seq(name @ ("Turtle" | "Screen")) => !program.classes(name)
      case _ => false
    }.map(_.mkString("."))
  }

  private def turtleObjectUnsupported(callee: String): ParsedStatement =
    S.Unsupported(s"$callee(", "Turtle objects are not supported; use import turtle and call turtle.forward(...) and the other commands directly",
      unparsable = false)

  /** Names the program reads outside the `for` loops that count with them. A loop counter outside this
    * set is read at most inside its own loops, so a loop whose body does not read it can be a fixed
    * repetition even though Python keeps the counter after it.
    */
  private final case class ProgramNames(names: Set[String], moduleAssigned: Set[String] = Set.empty, classes: Set[String] = Set.empty)

  private def counterName(loop: PyForStatement): Option[String] = loop.elementExpression match {
    case PyTarget(name, Nil, None, _) => Some(name)
    case _ => None
  }

  private def usedNames(node: GenericAST): Set[String] = node match {
    case loop: PyForStatement => loop.getChildren().filterNot(_ eq loop.elementExpression).flatMap(usedNames).toSet -- counterName(loop)
    case target: PyTarget => target.getChildren().flatMap(usedNames).toSet + target.locationString.headOption.getOrElse(target.identifier)
    case other => other.getChildren().flatMap(usedNames).toSet
  }

  /** Names assigned or declared by statements, including in nested blocks but not in nested definitions. */
  private def assignedNames(values: Seq[PyStatement]): Set[String] = values.flatMap {
    case PySimpleAssignment(PyTarget(name, Nil, None, _), _) => Set(name)
    case PyAugAssignment(PyTarget(name, Nil, None, _), _, _) => Set(name)
    case loop: PyForStatement => Set(loop.elementExpression).collect { case PyTarget(name, Nil, None, _) => name } ++ assignedNames(loop.bodyBlock.statements)
    case PyIfStatement(_, thenBlock, elseBlock) => assignedNames(thenBlock.statements) ++ assignedNames(elseBlock.statements)
    case PyWhileStatement(_, body, _) => assignedNames(body.statements)
    case _ => Set.empty
  }.toSet

  private def globalNames(source: String): Seq[String] = source.trim.stripPrefix("global").split(",").map(_.trim).filter(_.nonEmpty).toSeq

  private def declaredGlobals(values: Seq[PyStatement]): Set[String] = values.flatMap {
    case PyUnparsableStatement(source) if source.trim.startsWith("global ") => globalNames(source)
    case loop: PyForStatement => declaredGlobals(loop.bodyBlock.statements)
    case PyIfStatement(_, thenBlock, elseBlock) => declaredGlobals(thenBlock.statements) ++ declaredGlobals(elseBlock.statements)
    case PyWhileStatement(_, body, _) => declaredGlobals(body.statements)
    case _ => Nil
  }.toSet

  /** In the shared tree a function assigns module variables, as in Java and Snap. A Python function
    * that assigns a module variable's name without `global` has a local of the same name instead,
    * which the tree cannot hold apart, so it is reported rather than converted differently.
    */
  private def hiddenModuleVariable(definition: PyFunctionDef, parameters: Seq[ParsedParameter])(using names: ProgramNames): Option[ParsedStatement] = {
    val statements = definition.block.statements
    val hidden = (assignedNames(statements) -- declaredGlobals(statements) -- parameters.map(_.name)).intersect(names.moduleAssigned)
    hidden.toSeq.sorted.headOption.map(name => S.Unsupported(s"def ${definition.name}(",
      s"In ${definition.name}, assigning $name makes it a local variable that hides the module variable $name. " +
        s"Declare global $name to change the module variable, or rename the local variable", unparsable = false))
  }

  /** `for counter in range(…)` with whole-number steps, see [[ParsedStatement.countingLoop]]. */
  private def forLoop(loop: PyForStatement)(using used: ProgramNames): Seq[ParsedStatement] = (loop.elementExpression, loop.inExpression) match {
    case _ if loop.elseBlock.nonEmpty || loop.isAsync => Seq(S.Unsupported("for ... else", "for/else and async for are not supported", unparsable = false))
    case (PyTarget(name, Nil, None, _), PyFunctionCall(PyTarget("range", Nil, None, _), arguments)) if arguments.size >= 1 && arguments.size <= 3 =>
      val (start, stop) = if arguments.size == 1 then (E.Literal("0"), expression(arguments.head)) else (expression(arguments.head), expression(arguments(1)))
      val body = block(loop.bodyBlock)
      // range(...) is evaluated once in Python, while the counting loops of Java compare against the bound on each pass.
      val changedBound = arguments.flatMap(usedNames).distinct.sorted.find(assigns(loop.bodyBlock, _))
      arguments.lift(2).fold(Some(1))(step => E.wholeNumber(expression(step))) match {
        case _ if assigns(loop.bodyBlock, name) =>
          Seq(S.Unsupported(s"for $name in range(...)", s"Assigning the loop counter $name inside its loop is not supported", unparsable = false))
        case _ if changedBound.nonEmpty =>
          Seq(S.Unsupported(s"for $name in range(", s"Assigning ${changedBound.get} inside the loop that uses it in range(...) is not supported; " +
            "store the bound in another variable before the loop", unparsable = false))
        case Some(step) if step != 0 =>
          S.countingLoop(name, start, stop, step, body, counterObserved = used.names(name) || usedNames(loop.bodyBlock)(name))
        case _ => Seq(S.Unsupported("for ... in range(...)", "The step of range(...) must be a nonzero whole number", unparsable = false))
      }
    case _ => Seq(S.Unsupported("for ...", "For statements must use range(...)", unparsable = true))
  }

  private def assigns(node: GenericAST, name: String): Boolean = node match {
    case PySimpleAssignment(PyTarget(`name`, Nil, _, _), _) => true
    case PyAugAssignment(PyTarget(`name`, Nil, _, _), _, _) => true
    case loop: PyForStatement if loop.elementExpression == PyTarget(name) => true
    case other => other.getChildren().exists(assigns(_, name))
  }

  // Classes: inheritance, decorators other than @staticmethod and other class-body statements are unsupported.

  private def classDef(definition: PyClassDef)(using ProgramNames): ParsedStatement = {
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
    else if methods.exists(hasDefaultValues) then defaultValuesUnsupported(methods.find(hasDefaultValues).get)
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
