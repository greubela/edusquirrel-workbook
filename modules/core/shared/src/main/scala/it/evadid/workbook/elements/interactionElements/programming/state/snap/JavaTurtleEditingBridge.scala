package it.evadid.workbook.elements.interactionElements.programming.state.snap

import it.evadid.vm.parsing.java.turtle.{JavaTurtleInputLimits, JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PythonType.*
import it.evadid.vm.simulation.java.JavaTurtleEvaluation as E
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaString, ProgrammingStatePythonString, ProgrammingStateSnapXml}
import scala.collection.mutable
import scala.util.control.NonFatal

object JavaTurtleEditingBridge {
  val MetadataVariable = "__java_turtle_metadata"
  val NotesPrefix = "EduSquirrel Java Turtle 1\n"
  val MaxRepresentationCharacters = 1048576

  private case class Variable(name: String, kind: R.ValueType, parameter: Boolean)
  private case class Method(name: String, variables: Vector[Variable]) {
    def parameters: Vector[Variable] = variables.filter(_.parameter)
    def locals: Vector[Variable] = variables.filterNot(_.parameter)
    def variable(name: String): Variable = variables.find(_.name == name).getOrElse(reject(s"Unknown Java variable '$name'."))
    def spec: String = SnapTurtleCatalog.customBlockSemanticSpec(name, parameters.map(_.name).toList)
    def callSpec: String = SnapCustomBlockRules.blockSpec(spec, name =>
      if variable(name).kind == R.ValueType.BooleanValue then "%b" else "%n")
  }
  private case class Metadata(className: String, methods: Vector[Method]) {
    def method(name: String): Method = methods.find(_.name == name).getOrElse(reject(s"Unknown Java method '$name'."))
    def entry: Method = method("main")
    def json: String = ujson.Obj("version" -> 1, "class" -> className,
      "methods" -> ujson.Arr.from(methods.map(method => ujson.Obj("name" -> method.name,
        "variables" -> ujson.Arr.from(method.variables.map(variable => ujson.Arr(variable.name,
          kindName(variable.kind), variable.parameter))))))).render()
  }
  private case class Value(code: String, kind: R.ValueType)

  private val PythonKeywords = Set("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
    "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield")
  private val ReservedNames = PythonKeywords ++ Set("java_turtle", "turtle", MetadataVariable, "float")
  private def identifier(name: String): Boolean = name.matches("[A-Za-z_][A-Za-z0-9_]*") && !ReservedNames(name)
  private def reject(message: String): Nothing = throw IllegalArgumentException(message)
  private def ensure(condition: Boolean, message: String): Unit = if !condition then reject(message)
  private def attempt[A](body: => A): Either[String, A] =
    try Right(body) catch { case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)) }
  private def kindName(kind: R.ValueType): String = kind match {
    case R.ValueType.IntValue => "int"
    case R.ValueType.DoubleValue => "double"
    case R.ValueType.BooleanValue => "boolean"
    case _ => reject("The main argument array is not an editable value.")
  }
  private def readKind(name: String): R.ValueType = name match {
    case "int" => R.ValueType.IntValue
    case "double" => R.ValueType.DoubleValue
    case "boolean" => R.ValueType.BooleanValue
    case _ => reject("Unknown Java value type in editing metadata.")
  }
  private def pythonType(kind: R.ValueType): String = if kind == R.ValueType.DoubleValue then "float" else if kind == R.ValueType.BooleanValue then "bool" else "int"
  private def assignable(actual: R.ValueType, expected: R.ValueType): Boolean =
    actual == expected || actual == R.ValueType.IntValue && expected == R.ValueType.DoubleValue
  private def numeric(kind: R.ValueType): Boolean = R.isNumeric(kind)
  private def bounded(source: String): Unit = {
    ensure(source.length <= MaxRepresentationCharacters, "This editing representation is too large.")
    var depth = 0
    var quote = Option.empty[Char]
    var escaped = false
    var unary = 0
    source.foreach { char => quote match {
      case Some(delimiter) =>
        if escaped then escaped = false
        else if char == '\\' then escaped = true
        else if char == delimiter then quote = None
      case None =>
        if char == '\'' || char == '"' then quote = Some(char)
        else if "([{".contains(char) then { depth += 1; ensure(depth <= 64, "This editing expression is too deeply nested.") }
        else if ")]}".contains(char) then depth -= 1
        if char == '-' || char == '+' || char == '!' then {
          unary += 1
          ensure(unary <= 64, "This editing expression has too many consecutive unary operators.")
        } else if !char.isWhitespace then unary = 0
    }}
  }

  private val PythonMetadataHeader = ("^(?:" + MetadataVariable + "\\s*(?::[^=\\r\\n]+)?=|import\\s+java_turtle(?:\\s|$))").r
  private val SnapMetadataHeader = """<notes(?:\s[^>]*)?>\s*EduSquirrel Java Turtle 1(?:\r\n|\r|\n|&#x[Dd];|&#13;|&#x[Aa];|&#10;|$)""".r
  private def noteContent(raw: String): String =
    SnapXmlParser.unescape(raw.replace("&#xD;", "\r").replace("&#xd;", "\r").replace("&#13;", "\r")
      .replace("&#xA;", "\n").replace("&#xa;", "\n").replace("&#10;", "\n"))
      .replace("\r\n", "\n").replace('\r', '\n')
  def hasPythonMetadata(source: String): Boolean = source.linesIterator.exists(line => PythonMetadataHeader.findFirstIn(line).nonEmpty)
  def hasSnapMetadata(xml: String): Boolean =
    SnapXmlParser.elements(xml, "notes").exists(node => noteContent(node.inner).startsWith(NotesPrefix)) ||
      SnapMetadataHeader.findFirstIn(xml).nonEmpty
  def recognizesPython(source: String): Boolean = hasPythonMetadata(source)
  def recognizesSnap(xml: String): Boolean = hasSnapMetadata(xml)

  private def metadata(program: P.Program): (Metadata, Map[R.VariableId, String]) = {
    val names = mutable.Map.empty[R.VariableId, String]
    val methodNames = program.bindings.source.methods.map(_.name).toSet
    val methods = program.bindings.source.methods.map { method =>
      ensure(identifier(method.name), s"The method name '${method.name}' is not available in the shared editing subset.")
      val variables = program.bindings.variables.filter(_.variable.id.method == method.id)
        .map(_.variable).filterNot(_.valueType == R.ValueType.MainArguments)
      val counts = variables.groupBy(_.name).view.mapValues(_.size).toMap
      val used = mutable.Set.from(methodNames)
      val parameters = method.parameters.map(_.id).toSet
      val converted = variables.map { variable =>
        ensure(identifier(variable.name), s"The variable name '${variable.name}' is not available in the shared editing subset.")
        val first = if counts(variable.name) == 1 && !methodNames(variable.name) then variable.name else s"${variable.name}_java_${variable.id.index}"
        var name = first
        var suffix = 2
        while used(name) do { name = s"${first}_$suffix"; suffix += 1 }
        used += name
        names += variable.id -> name
        Variable(name, variable.valueType, parameters(variable.id))
      }
      val parameterVariables = method.parameters.filterNot(_.valueType == R.ValueType.MainArguments).map(variable =>
        converted.find(_.name == names(variable.id)).get)
      Method(method.name, parameterVariables ++ converted.filterNot(_.parameter))
    }
    Metadata(program.bindings.source.className, methods) -> names.toMap
  }

  private def readMetadata(json: String): Metadata = {
    ensure(json.length <= 262144, "Java editing metadata is too large.")
    bounded(json)
    val root = ujson.read(json)
    ensure(root.obj.keySet.toSet == Set("version", "class", "methods") && root("version") == ujson.Num(1), "Unknown Java editing metadata schema.")
    val methods = root("methods").arr.toVector.map { row =>
      ensure(row.obj.keySet.toSet == Set("name", "variables"), "Unknown method metadata.")
      val variables = row("variables").arr.toVector.map { variable =>
        ensure(variable.arr.size == 3, "Malformed variable metadata.")
        val name = variable(0).str
        ensure(identifier(name), "Invalid editing variable name.")
        Variable(name, readKind(variable(1).str), variable(2).bool)
      }
      ensure(variables.size <= JavaTurtleInputLimits.MaxLocalsPerMethod + JavaTurtleInputLimits.MaxParameters &&
        variables.map(_.name).distinct.size == variables.size, "Duplicate or excessive editing variables.")
      ensure(variables.count(_.parameter) <= JavaTurtleInputLimits.MaxParameters, "Too many editing parameters.")
      val name = row("name").str
      ensure(identifier(name), "Invalid editing method name.")
      Method(name, variables)
    }
    ensure(methods.nonEmpty && methods.size <= JavaTurtleInputLimits.MaxMethods && methods.map(_.name).distinct.size == methods.size,
      "Duplicate or excessive editing methods.")
    val result = Metadata(root("class").str, methods)
    val methodNames = methods.map(_.name).toSet
    ensure(methods.forall(_.variables.forall(variable => !methodNames(variable.name))),
      "An editing variable cannot shadow a Java method in Python.")
    ensure(methods.map(_.variables.size).sum <= JavaTurtleInputLimits.MaxVariables, "Too many Java editing variables.")
    ensure(result.entry.parameters.isEmpty, "The editing main method must have no parameters.")
    result
  }

  private val BinaryTokens: Map[R.BinaryOperator, String] = Map(
    R.BinaryOperator.Add -> "+", R.BinaryOperator.Subtract -> "-", R.BinaryOperator.Multiply -> "*", R.BinaryOperator.Divide -> "/",
    R.BinaryOperator.Remainder -> "%", R.BinaryOperator.Less -> "<", R.BinaryOperator.LessEqual -> "<=", R.BinaryOperator.Greater -> ">",
    R.BinaryOperator.GreaterEqual -> ">=", R.BinaryOperator.Equal -> "==", R.BinaryOperator.NotEqual -> "!=")
  private val OperationTokens = Map("add" -> "+", "sub" -> "-", "mul" -> "*", "div" -> "/", "rem" -> "%", "neg" -> "-", "pos" -> "+")
  private val ComparisonTokens = Map("lt" -> "<", "le" -> "<=", "gt" -> ">", "ge" -> ">=", "eq" -> "==", "ne" -> "!=")
  private def comparison(operator: R.BinaryOperator): String = ComparisonTokens.find(_._2 == BinaryTokens(operator)).map(_._1)
    .getOrElse(reject("Expected a numeric Java comparison."))
  private def operation(operator: R.BinaryOperator): String = operator match {
    case R.BinaryOperator.Add => "add"
    case R.BinaryOperator.Subtract => "sub"
    case R.BinaryOperator.Multiply => "mul"
    case R.BinaryOperator.Divide => "div"
    case R.BinaryOperator.Remainder => "rem"
    case _ => reject("Expected a Java arithmetic operation.")
  }
  private def arithmetic(operator: R.BinaryOperator): Boolean = Set(R.BinaryOperator.Add, R.BinaryOperator.Subtract,
    R.BinaryOperator.Multiply, R.BinaryOperator.Divide, R.BinaryOperator.Remainder)(operator)
  private def assigned(operator: R.AssignmentOperator): R.BinaryOperator = operator match {
    case R.AssignmentOperator.Add => R.BinaryOperator.Add
    case R.AssignmentOperator.Subtract => R.BinaryOperator.Subtract
    case R.AssignmentOperator.Multiply => R.BinaryOperator.Multiply
    case R.AssignmentOperator.Divide => R.BinaryOperator.Divide
    case R.AssignmentOperator.Remainder => R.BinaryOperator.Remainder
    case _ => reject("Expected a compound assignment.")
  }
  private def widened(expression: R.Expression, kind: R.ValueType): R.Expression =
    if expression.valueType == R.ValueType.IntValue && kind == R.ValueType.DoubleValue then R.Widen(expression) else expression
  private def assignment(variable: R.Variable, operator: R.AssignmentOperator, value: R.Expression): R.Expression =
    if operator == R.AssignmentOperator.Set then widened(value, variable.valueType)
    else R.Binary(assigned(operator), R.Read(variable), widened(value, variable.valueType))
  private def alwaysTrue(value: R.Expression): Boolean =
    E.evaluate(value, variable => Left(E.Failure.MissingValue(variable.id))).toOption.contains(E.Value.BooleanValue(true))
  private def completesNormally(body: R.Block): Boolean = body.statements.forall {
    case R.Return => false
    case R.If(_, positive, Some(negative)) => completesNormally(positive) || completesNormally(negative)
    case R.While(condition, _) => !alwaysTrue(condition)
    case R.For(init, condition, _, _) => completesNormally(init) && condition.exists(value => !alwaysTrue(value))
    case _ => true
  }

  def toPython(program: P.Program): Either[String, ProgrammingStatePythonString] = attempt {
    val (meta, names) = metadata(program)
    def expression(value: R.Expression): String = value match {
      case R.IntLiteral(number) => number.toString
      case R.DoubleLiteral(number) => it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport.doubleLiteral(number)
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
    fromPython(source).fold(reject, _ => ProgrammingStatePythonString(source))
  }

  def toSnap(program: P.Program): Either[String, ProgrammingStateSnapXml] = attempt {
    val (meta, names) = metadata(program)
    def literal(value: String): String = s"<l>${SnapInputCodec.escapeXml(value)}</l>"
    def block(selector: String, inputs: String): String = s"<block s=\"$selector\">$inputs</block>"
    def boolean(flag: Boolean): String = block("reportBoolean", s"<l><bool>$flag</bool></l>")
    def stored(value: String, kind: R.ValueType): String = if numeric(kind) then block("reportJavaCell", value) else value
    def expression(value: R.Expression): String = value match {
      case R.IntLiteral(number) => literal(number.toString)
      case R.DoubleLiteral(number) => literal(it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport.doubleLiteral(number))
      case R.BooleanLiteral(flag) => boolean(flag)
      case R.Read(variable) =>
        if numeric(variable.valueType) then block("reportJavaRead", literal(names(variable.id)))
        else s"<block var=\"${SnapInputCodec.escapeXml(names(variable.id))}\"/>"
      case R.Update(variable, operator, prefix) => block("reportJavaUpdate", literal(names(variable.id)) +
        literal(if operator == R.UpdateOperator.Increment then "1" else "-1") + boolean(prefix) + boolean(variable.valueType == R.ValueType.DoubleValue))
      case R.Widen(inner) => block("reportJavaDouble", literal("pos") + expression(inner) + literal("0"))
      case R.Group(inner) => expression(inner)
      case R.Unary(R.UnaryOperator.Not, inner) => block("reportNot", expression(inner))
      case R.Unary(operator, inner) => block(if value.valueType == R.ValueType.DoubleValue then "reportJavaDouble" else "reportJavaInt",
        literal(if operator == R.UnaryOperator.Negate then "neg" else "pos") + expression(inner) + literal("0"))
      case R.Binary(operator, left, right) if arithmetic(operator) => block(if value.valueType == R.ValueType.DoubleValue then "reportJavaDouble" else "reportJavaInt",
        literal(operation(operator)) + expression(left) + expression(right))
      case R.Binary(operator, left, right) if numeric(left.valueType) && numeric(right.valueType) => block("reportJavaCompare",
        literal(comparison(operator)) + expression(left) + expression(right) + boolean(left.valueType == R.ValueType.DoubleValue || right.valueType == R.ValueType.DoubleValue))
      case R.Binary(operator, left, right) => block(SnapControlFlow.OperatorToSnapReporter(BinaryTokens(operator)).selector,
        "<list>" + expression(left) + expression(right) + "</list>")
      case R.ShortCircuit(operator, left, right) => block(if operator == R.ShortCircuitOperator.And then "reportVariadicAnd" else "reportVariadicOr",
        "<list>" + expression(left) + expression(right) + "</list>")
    }
    def statements(body: R.Block): String = body.statements.map {
      case R.Empty => ""
      case R.Return => block("doStopThis", "<l><option>this block</option></l>")
      case R.Declare(variable, initial) => initial.fold("")(value => block("doSetVar", literal(names(variable.id)) + stored(expression(widened(value, variable.valueType)), variable.valueType)))
      case R.Assign(variable, operator, value) => block("doSetVar", literal(names(variable.id)) + stored(expression(assignment(variable, operator, value)), variable.valueType))
      case R.Evaluate(R.Update(variable, operator, _)) => block("doSetVar", literal(names(variable.id)) + stored(expression(R.Update(variable, operator, true)), variable.valueType))
      case R.Evaluate(_) => reject("This discarded expression cannot be represented as a Snap command.")
      case R.Call(R.CallTarget.Turtle(command), args) => block(if command == R.TurtleCommand.Forward then "doJavaForward" else "doJavaTurnRight", args.map(expression).mkString)
      case R.Call(R.CallTarget.Helper(id), args) =>
        val method = meta.method(program.bindings.source.methods.find(_.id == id).get.name)
        s"<custom-block s=\"${SnapInputCodec.escapeXml(method.callSpec)}\">${args.zip(method.parameters).map((value, parameter) => stored(expression(value), parameter.kind)).mkString}</custom-block>"
      case R.If(condition, positive, negative) => block(if negative.isDefined then "doIfElse" else "doIf",
        expression(condition) + "<script>" + statements(positive) + "</script>" + negative.fold("<list></list>")(body => "<script>" + statements(body) + "</script>"))
      case R.While(condition, body) => block("doUntil", block("reportNot", expression(condition)) + "<script>" + statements(body) + "</script>")
      case R.For(init, condition, update, body) =>
        val updates = R.Block(update.statements.map {
          case R.Evaluate(R.Update(variable, operator, _)) => R.Assign(variable, if operator == R.UpdateOperator.Increment then R.AssignmentOperator.Add else R.AssignmentOperator.Subtract, R.IntLiteral(1))
          case other => other
        })
        statements(init) + block("doUntil", block("reportNot", condition.fold(boolean(true))(expression)) +
          "<script>" + statements(body) + (if completesNormally(body) then statements(updates) else "") + "</script>")
    }.mkString
    val definitions = program.bindings.source.methods.map { method =>
      val description = meta.method(method.name)
      val declarations = if description.locals.isEmpty then "" else block("doDeclareVariables",
        "<list>" + description.locals.map(variable => literal(variable.name)).mkString + "</list>")
      val inputs = description.parameters.map(variable => s"<input type=\"${if variable.kind == R.ValueType.BooleanValue then "%b" else "%n"}\"></input>").mkString
      s"<block-definition s=\"${SnapInputCodec.escapeXml(description.spec)}\" type=\"command\" category=\"variables\"><inputs>$inputs</inputs><script>$declarations${statements(method.body)}</script></block-definition>"
    }.mkString
    val base = SnapProjectXml.empty
    val withNotes = base.replace("<notes></notes>", "<notes>" + SnapInputCodec.escapeXml(NotesPrefix + meta.json) + "</notes>")
    val scene = SnapXmlParser.elements(withNotes, "scene").head
    val blocks = SnapXmlParser.child(scene.inner, "blocks").get
    val blocksAt = scene.start + scene.outer.indexOf('>') + 1 + blocks.start
    val withDefinitions = withNotes.substring(0, blocksAt) + "<blocks>" + definitions + "</blocks>" + withNotes.substring(blocksAt + blocks.outer.length)
    val sprite = SnapXmlParser.elements(withDefinitions, "sprite").head
    val scripts = SnapXmlParser.child(sprite.inner, "scripts").get
    val scriptsAt = sprite.start + sprite.outer.indexOf('>') + 1 + scripts.start
    val start = "<scripts><script x=\"156\" y=\"66\"><block s=\"receiveGo\"></block><block s=\"doJavaReset\"></block><custom-block s=\"main\"></custom-block></script></scripts>"
    val xml = withDefinitions.substring(0, scriptsAt) + start + withDefinitions.substring(scriptsAt + scripts.outer.length)
    bounded(xml)
    val result = ProgrammingStateSnapXml(xml)
    fromSnap(result).fold(reject, _ => result)
  }

  private def finish(meta: Metadata, bodies: Map[String, Vector[String]]): ProgrammingStateJavaString = {
    ensure(bodies.keySet == meta.methods.map(_.name).toSet, "Every declared Java method must be represented exactly once.")
    val methods = meta.methods.map { method =>
      val header = if method.name == "main" then "public static void main(String[] args)" else
        s"static void ${method.name}(${method.parameters.map(variable => s"${kindName(variable.kind)} ${variable.name}").mkString(", ")})"
      "    " + header + " {\n" + bodies(method.name).map("        " + _).mkString("\n") + "\n    }"
    }
    val source = s"class ${meta.className} {\n${methods.mkString("\n\n")}\n}\n"
    P.compile(source).fold(diagnostic => reject(diagnostic.message), _ => ProgrammingStateJavaString(source))
  }
  private def number(raw: String, snapLiteral: Boolean = false): Value = {
    if snapLiteral && raw == "-0" then Value("-0.0", R.ValueType.DoubleValue)
    else if raw.matches("-?(0|[1-9][0-9]*)") && raw.toIntOption.nonEmpty then Value(raw, R.ValueType.IntValue)
    else {
      val finite = raw.toDoubleOption.filter(_.isFinite).getOrElse(reject("Use a finite decimal Java number."))
      ensure(raw.matches("-?(?:[0-9]+(?:\\.[0-9]+)?)(?:[eE][+-]?[0-9]+)?"), "Use a finite decimal Java number.")
      val decimal = BigDecimal(raw).bigDecimal.toPlainString
      Value(if finite == 0.0 && raw.startsWith("-") then "-0.0" else if decimal.contains('.') then decimal else decimal + ".0", R.ValueType.DoubleValue)
    }
  }
  private def widen(value: Value): Value = {
    if value.kind == R.ValueType.DoubleValue then value
    else if value.code.toIntOption.nonEmpty then Value(value.code + ".0", R.ValueType.DoubleValue)
    else Value(s"(1.0 * ${value.code})", R.ValueType.DoubleValue)
  }
  private def typedOperation(op: String, left: Value, right: Value, floating: Boolean): Value = {
    ensure(OperationTokens.contains(op), "Unknown typed Java arithmetic operation.")
    ensure(numeric(left.kind) && numeric(right.kind), "Java arithmetic needs numeric values.")
    ensure(floating || left.kind == R.ValueType.IntValue && right.kind == R.ValueType.IntValue, "An int operation cannot use a double value.")
    ensure(!(op == "neg" || op == "pos") || right.code == "0", "Keep the unused unary-operation input at literal zero.")
    val a = if floating && left.kind == R.ValueType.IntValue && right.kind == R.ValueType.IntValue then widen(left).code else left.code
    val code = if op == "neg" || op == "pos" then s"(${OperationTokens(op)} $a)" else s"($a ${OperationTokens(op)} ${right.code})"
    if op == "pos" then if floating then widen(left) else left
    else if floating && op == "mul" && left.code == "1.0" && right.kind == R.ValueType.DoubleValue then right
    else if floating && op == "mul" && right.code == "1.0" && left.kind == R.ValueType.DoubleValue then left
    else Value(code, if floating then R.ValueType.DoubleValue else R.ValueType.IntValue)
  }
  private def logical(op: String, left: Value, right: Option[Value]): Value = {
    if Set("!", "&&", "||")(op) then {
      ensure(left.kind == R.ValueType.BooleanValue && right.forall(_.kind == R.ValueType.BooleanValue), "Use boolean values in a boolean operation.")
    } else ensure(right.exists(value => numeric(left.kind) && numeric(value.kind) ||
      Set("==", "!=")(op) && left.kind == R.ValueType.BooleanValue && value.kind == R.ValueType.BooleanValue), "Compare compatible scalar values.")
    Value(right.fold(s"(!${left.code})")(value => s"(${left.code} $op ${value.code})"), R.ValueType.BooleanValue)
  }
  private def typedComparison(op: String, left: Value, right: Value, floating: Boolean): Value = {
    ensure(ComparisonTokens.contains(op), "Unknown typed Java comparison.")
    ensure(numeric(left.kind) && numeric(right.kind), "A numeric comparison needs numeric Java values.")
    ensure(floating || left.kind == R.ValueType.IntValue && right.kind == R.ValueType.IntValue,
      "An int comparison cannot use a double value.")
    val widenedLeft = if floating && left.kind == R.ValueType.IntValue && right.kind == R.ValueType.IntValue then
      widen(left) else left
    logical(ComparisonTokens(op), widenedLeft, Some(right))
  }
  private def call(meta: Metadata, name: String, args: Vector[Value]): String = {
    if name == "Turtle.forward" || name == "Turtle.turnRight" then {
      ensure(args.size == 1 && numeric(args.head.kind), "A Turtle command needs one numeric argument.")
    } else {
      val method = meta.method(name)
      ensure(name != "main" && args.size == method.parameters.size && args.zip(method.parameters).forall((a, p) => assignable(a.kind, p.kind)), "The method arguments do not match its declaration.")
    }
    s"$name(${args.map(_.code).mkString(", ")});"
  }

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

  def fromSnap(state: ProgrammingStateSnapXml): Either[String, ProgrammingStateJavaString] = attempt {
    ensure(!state.hasLegacyFloatingObjects, "Legacy floating blocks cannot be converted without losing data.")
    bounded(state.snapXml)
    fromCleanSnap(state.withProjectXml(ProgrammingStateSnapXmlHelper.removeGeneratedImages(state.snapXml)))
  }

  private def fromCleanSnap(state: ProgrammingStateSnapXml): ProgrammingStateJavaString = {
    val document = SnapXmlParser.children(state.snapXml.trim)
    ensure(document.size == 1 && document.head.tag == "project" && document.head.outer == state.snapXml.trim,
      "Read a complete Snap project; malformed XML cannot be converted.")
    val notes = SnapXmlParser.elements(state.snapXml, "notes").map(node => noteContent(node.inner)).filter(_.startsWith(NotesPrefix)).distinct
    ensure(notes.size == 1, "Exactly one typed Java metadata note is required.")
    val meta = readMetadata(notes.head.substring(NotesPrefix.length))
    ensure(SnapXmlParser.elements(state.snapXml, "scene").size == 1 && SnapXmlParser.elements(state.snapXml, "sprite").size == 1,
      "Only one scene and one Turtle sprite can be converted.")
    val definitions = SnapCustomBlockRules.globalDefinitions(state.snapXml)
    ensure(definitions.size == meta.methods.size && definitions.map(_.spec).distinct.size == definitions.size &&
      SnapCustomBlockRules.localDefinitions(state.snapXml).isEmpty, "Every Java method must have one global command block.")
    ensure(SnapCustomBlockRules.obsoleteCalls(state.snapXml).isEmpty, "A custom block call has no matching definition.")
    val allowedTags = Set("project", "notes", "scenes", "scene", "palette", "category", "hidden", "headers", "code", "blocks", "primitives",
      "stage", "costumes", "sounds", "list", "variables", "sprites", "sprite", "scripts", "script", "creator", "origCreator", "origName",
      "block-definition", "inputs", "input", "block", "custom-block", "l", "bool", "option", "comment", "header", "translations", "thumbnail", "pentrails", "pentrail")
    var xmlNodes = 0
    def checkXml(inner: String, depth: Int, parentTag: String): Unit = {
      ensure(depth <= 64, "Snap XML is too deeply nested for editing conversion.")
      val children = SnapXmlParser.children(inner)
      var consumed = 0
      val gaps = children.map { element =>
        val gap = inner.substring(consumed, element.start)
        consumed = element.end
        gap
      } :+ inner.substring(consumed)
      ensure(gaps.forall(gap => !gap.contains('<') && !gap.contains('>')), "Malformed Snap markup cannot be discarded.")
      val textTags = Set("l", "bool", "option", "input", "creator", "origCreator", "origName")
      ensure(textTags(parentTag) || gaps.forall(_.trim.isEmpty), s"Unexpected text in Snap '$parentTag' cannot be discarded.")
      children.foreach { element =>
        xmlNodes += 1
        ensure(xmlNodes <= 50000, "Snap XML has too many editing elements.")
        ensure(allowedTags(element.tag), s"Unknown Snap element '${element.tag}' cannot be discarded.")
        val managedAttributes = element.tag match {
          case "block" => Some(Set("s", "var", "id"))
          case "custom-block" => Some(Set("s", "id"))
          case "script" => Some(Set("x", "y", "id"))
          case "project" => Some(Set("name", "app", "version"))
          case "block-definition" => Some(Set("s", "type", "category", "helper", "space", "strict"))
          case "input" => Some(Set("type"))
          case _ => None
        }
        ensure(managedAttributes.forall(element.attributes.keySet.subsetOf), s"Unknown '${element.tag}' metadata cannot be discarded.")
        if Set("code", "headers", "primitives")(element.tag) then ensure(element.inner.trim.isEmpty, "Embedded Snap code is not part of the Java editing subset.")
        if Set("comment", "header", "translations")(element.tag) then ensure(element.inner.trim.isEmpty,
          s"Authored Snap ${element.tag} content cannot be discarded during Java conversion.")
        if element.tag == "notes" then ensure(element.inner.trim.isEmpty || noteContent(element.inner).startsWith(NotesPrefix),
          "Authored Snap notes cannot be discarded during Java conversion.")
        if element.tag == "notes" then ensure(Set("project", "scene")(parentTag), "Keep Java metadata notes on the project or its scene.")
        if element.tag == "input" then ensure(element.inner.trim.isEmpty ||
          element.attrOrEmpty("type") == "%n" && Set("0", "0.0")(element.inner.trim) ||
          element.attrOrEmpty("type") == "%b" && element.inner.trim == "false", "Authored custom input defaults cannot be discarded.")
        if element.tag != "notes" && element.tag != "thumbnail" && element.tag != "pentrail" then checkXml(element.inner, depth + 1, element.tag)
      }
      if inner.contains('<') then ensure(children.nonEmpty, "Malformed Snap XML cannot be converted.")
    }
    checkXml(state.snapXml, 0, "document")
    def children(element: SnapXmlParser.Element, count: Int): List[SnapXmlParser.Element] = {
      val result = SnapXmlParser.children(element.inner)
      ensure(result.size == count, s"The '${element.attrOrEmpty("s")}' block has the wrong input count.")
      result
    }
    def text(element: SnapXmlParser.Element): String = { ensure(element.tag == "l" && !element.inner.contains('<'), "Expected a literal block input."); SnapXmlParser.unescape(element.inner) }
    def expression(element: SnapXmlParser.Element, method: Method): Value = {
      element.attr("var") match {
        case Some(name) =>
          val variable = method.variable(name)
          ensure(element.tag == "block" && element.inner.trim.isEmpty && variable.kind == R.ValueType.BooleanValue,
            "Use a typed Java numeric read block instead of a raw numeric variable reporter.")
          Value(name, variable.kind)
        case None if element.tag == "l" =>
          val literals = SnapXmlParser.children(element.inner)
          if literals.nonEmpty then {
            ensure(literals.size == 1 && literals.head.tag == "bool" && Set("true", "false")(literals.head.inner), "Use a scalar boolean literal.")
            Value(literals.head.inner, R.ValueType.BooleanValue)
          } else number(text(element).trim, snapLiteral = true)
        case None if element.tag == "block" =>
          element.attrOrEmpty("s") match {
            case "reportTrue" | "reportFalse" => ensure(children(element, 0).isEmpty, "A boolean reporter has no inputs."); Value(if element.attrOrEmpty("s") == "reportTrue" then "true" else "false", R.ValueType.BooleanValue)
            case "reportBoolean" =>
              val value = expression(children(element, 1).head, method)
              ensure(value.kind == R.ValueType.BooleanValue, "Use a scalar boolean constant.")
              value
            case "reportJavaRead" =>
              val variable = method.variable(text(children(element, 1).head))
              ensure(numeric(variable.kind), "Read a numeric Java cell with the typed read block.")
              Value(variable.name, variable.kind)
            case "reportJavaInt" | "reportJavaDouble" =>
              val args = children(element, 3)
              typedOperation(text(args.head), expression(args(1), method), expression(args(2), method), element.attrOrEmpty("s") == "reportJavaDouble")
            case "reportJavaCompare" =>
              val args = children(element, 4)
              val floating = expression(args(3), method)
              ensure(floating.kind == R.ValueType.BooleanValue && Set("true", "false")(floating.code), "Use a fixed boolean numeric-comparison flag.")
              typedComparison(text(args.head), expression(args(1), method), expression(args(2), method), floating.code == "true")
            case "reportJavaUpdate" =>
              val args = children(element, 4); val target = method.variable(text(args.head)); val delta = text(args(1)).trim
              ensure(Set("1", "-1")(delta) && numeric(target.kind), "Update a numeric Java variable by one.")
              val prefix = expression(args(2), method); val floating = expression(args(3), method)
              ensure(Set("true", "false")(prefix.code) && floating.code == (if target.kind == R.ValueType.DoubleValue then "true" else "false"), "The update flags do not match its type.")
              val token = if delta == "1" then "++" else "--"
              Value(if prefix.code == "true" then s"($token${target.name})" else s"(${target.name}$token)", target.kind)
            case "reportNot" => logical("!", expression(children(element, 1).head, method), None)
            case selector if SnapControlFlow.ConditionSelectors(selector) =>
              val containers = children(element, 1); ensure(containers.head.tag == "list", "Use a variadic condition input list.")
              val args = SnapXmlParser.children(containers.head.inner); ensure(args.size == 2, "Use exactly two condition operands.")
              val token = SnapControlFlow.OperatorToSnapReporter.find(_._2.selector == selector).map(_._1).getOrElse(reject("Unknown condition reporter."))
              val left = expression(args.head, method); val right = expression(args(1), method)
              ensure(left.kind == R.ValueType.BooleanValue && right.kind == R.ValueType.BooleanValue && Set("and", "or", "==", "!=")(token),
                "Use a typed Java comparison block for numeric comparisons.")
              logical(if token == "and" then "&&" else if token == "or" then "||" else token, left, Some(right))
            case selector => reject(s"Unsupported Snap value block '$selector'. Use a typed Java arithmetic block.")
          }
        case _ => reject("Unsupported Snap value input.")
      }
    }
    def stored(element: SnapXmlParser.Element, expected: R.ValueType, method: Method): Value = {
      if numeric(expected) then {
        ensure(element.tag == "block" && element.attrOrEmpty("s") == "reportJavaCell", "Store and pass numeric Java values in a typed cell block.")
        val value = expression(children(element, 1).head, method)
        ensure(numeric(value.kind), "A numeric Java cell cannot contain a boolean.")
        value
      } else expression(element, method)
    }
    def statements(script: SnapXmlParser.Element, method: Method, level: Int): Vector[String] = {
      ensure(script.tag == "script", "Expected a method script.")
      val prefix = "    " * level
      SnapXmlParser.children(script.inner).toVector.flatMap { element =>
        if element.tag == "custom-block" then {
          val called = meta.methods.find(_.callSpec == element.attrOrEmpty("s")).getOrElse(reject("Unknown Java custom block identity."))
          Vector(prefix + call(meta, called.name, children(element, called.parameters.size).zip(called.parameters)
            .map((value, parameter) => stored(value, parameter.kind, method)).toVector))
        } else {
          ensure(element.tag == "block", "Unknown Snap command element.")
          element.attrOrEmpty("s") match {
            case "doDeclareVariables" =>
              val list = children(element, 1).head; ensure(list.tag == "list", "Use a Java local variable declaration list.")
              val names = SnapXmlParser.children(list.inner).map(text)
              ensure(names.distinct.size == names.size && names.forall(name => !method.variable(name).parameter), "Declare each Java local exactly once.")
              names.toVector.map(name => prefix + s"${kindName(method.variable(name).kind)} $name;")
            case "doSetVar" =>
              val args = children(element, 2); val variable = method.variable(text(args.head)); val value = stored(args(1), variable.kind, method)
              ensure(assignable(value.kind, variable.kind), "The assigned value has the wrong Java type.")
              Vector(prefix + s"${variable.name} = ${value.code};")
            case "doJavaForward" | "doJavaTurnRight" =>
              Vector(prefix + call(meta, if element.attrOrEmpty("s") == "doJavaForward" then "Turtle.forward" else "Turtle.turnRight",
                children(element, 1).map(expression(_, method)).toVector))
            case "doStopThis" =>
              val input = children(element, 1).head
              ensure(input.tag == "l" && SnapXmlParser.child(input.inner, "option").exists(_.inner == "this block"), "Only 'stop this block' represents Java return.")
              Vector(prefix + "return;")
            case "doIf" | "doIfElse" =>
              val args = children(element, 3)
              val isElse = element.attrOrEmpty("s") == "doIfElse"
              if !isElse then ensure(args(2).tag == "list" && args(2).inner.trim.isEmpty, "Additional Snap elseif slots cannot be discarded.")
              val condition = expression(args.head, method); ensure(condition.kind == R.ValueType.BooleanValue, "Use a boolean if condition.")
              Vector(prefix + s"if (${condition.code}) {") ++ statements(args(1), method, level + 1) ++ Vector(prefix + "}") ++
                (if !isElse then Vector.empty else Vector(prefix + "else {") ++ statements(args(2), method, level + 1) ++ Vector(prefix + "}"))
            case "doUntil" =>
              val args = children(element, 2); val condition = expression(args.head, method)
              ensure(condition.kind == R.ValueType.BooleanValue, "Use a boolean loop condition.")
              Vector(prefix + s"while (!(${condition.code})) {") ++ statements(args(1), method, level + 1) ++ Vector(prefix + "}")
            case selector => reject(s"Unsupported Snap command '$selector'.")
          }
        }
      }
    }
    val bodies = definitions.map { definition =>
      val method = meta.methods.find(_.spec == definition.spec).getOrElse(reject("The custom block declaration no longer matches its Java identity."))
      ensure(definition.isGlobal && definition.blockType == "command" && definition.slotNames == method.parameters.map(_.name).toList &&
        method.parameters.forall(parameter => definition.typeOf(parameter.name) == (if parameter.kind == R.ValueType.BooleanValue then "%b" else "%n")),
        "Keep the typed scalar Java block parameters.")
      ensure(definition.rawCategory == "variables" || definition.rawCategory == "Variables" &&
        SnapCustomBlockRules.paletteCategories(state.snapXml).contains("Variables"),
        "A custom block category cannot be discarded during Java conversion.")
      ensure(definition.element.attributes.keySet.subsetOf(Set("s", "type", "category", "helper", "space", "strict")),
        "Unknown or executable custom-block metadata cannot be discarded.")
      val scripts = SnapXmlParser.children(definition.element.inner).filter(_.tag == "script")
      ensure(scripts.size <= 1 && SnapXmlParser.children(definition.element.inner).filter(_.tag == "scripts").forall(_.inner.trim.isEmpty),
        "Loose scripts in a custom block editor cannot be discarded.")
      method.name -> scripts.headOption.fold(Vector.empty[String])(script => statements(script, method, 0))
    }.toMap
    val sprite = SnapXmlParser.elements(state.snapXml, "sprite").head
    ensure(SnapXmlParser.children(sprite.inner).count(_.tag == "scripts") == 1,
      "Multiple sprite script containers cannot be discarded.")
    val startup = SnapXmlParser.child(sprite.inner, "scripts").toList.flatMap(container => SnapXmlParser.children(container.inner))
    ensure(startup.size == 1 && startup.head.tag == "script", "Keep exactly one startup script; loose scripts cannot be discarded.")
    val startBlocks = SnapXmlParser.children(startup.head.inner)
    ensure(startBlocks.size == 3 && startBlocks.head.tag == "block" && startBlocks.head.attrOrEmpty("s") == "receiveGo" && startBlocks.head.inner.trim.isEmpty &&
      startBlocks(1).tag == "block" && startBlocks(1).attrOrEmpty("s") == "doJavaReset" && startBlocks(1).inner.trim.isEmpty &&
      startBlocks(2).tag == "custom-block" && startBlocks(2).attrOrEmpty("s") == meta.entry.callSpec && startBlocks(2).inner.trim.isEmpty,
      "Keep the green-flag reset and main() startup script.")
    val stage = SnapXmlParser.elements(state.snapXml, "stage").head
    ensure(SnapXmlParser.child(stage.inner, "scripts").forall(_.inner.trim.isEmpty), "Additional stage scripts cannot be discarded.")
    ensure(SnapXmlParser.elements(state.snapXml, "scripts").count(_.inner.trim.nonEmpty) == 1,
      "Additional or loose script containers cannot be discarded.")
    finish(meta, bodies)
  }
}
