package it.evadid.vm.conversion.javaediting

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog

import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapxml.SnapXmlParser

import it.evadid.vm.conversion.javaexpression.checked.{JavaTurtleInputLimits, JavaTurtleResolution as R, JavaTurtleVmPrograms as P}
import it.evadid.vm.simulation.java.JavaTurtleEvaluation as E
import it.evadid.workbook.elements.interactionElements.programming.state.*

import scala.collection.mutable
import scala.util.control.NonFatal

object JavaEditingMetadata {
  val MetadataVariable = "__java_turtle_metadata"
  val NotesPrefix = "EduSquirrel Java Turtle 1\n"
  val MaxRepresentationCharacters = 1048576

  private[conversion] case class Variable(name: String, kind: R.ValueType, parameter: Boolean)
  private[conversion] case class Method(name: String, variables: Vector[Variable]) {
    def parameters: Vector[Variable] = variables.filter(_.parameter)
    def locals: Vector[Variable] = variables.filterNot(_.parameter)
    def variable(name: String): Variable = variables.find(_.name == name).getOrElse(reject(s"Unknown Java variable '$name'."))
    def spec: String = SnapTurtleCatalog.customBlockSemanticSpec(name, parameters.map(_.name).toList)
    def callSpec: String = SnapCustomBlockRules.blockSpec(spec, name =>
      if variable(name).kind == R.ValueType.BooleanValue then "%b" else "%n")
  }
  private[conversion] case class Metadata(className: String, methods: Vector[Method]) {
    def method(name: String): Method = methods.find(_.name == name).getOrElse(reject(s"Unknown Java method '$name'."))
    def entry: Method = method("main")
    def json: String = ujson.Obj("version" -> 1, "class" -> className,
      "methods" -> ujson.Arr.from(methods.map(method => ujson.Obj("name" -> method.name,
        "variables" -> ujson.Arr.from(method.variables.map(variable => ujson.Arr(variable.name,
          kindName(variable.kind), variable.parameter))))))).render()
  }
  private[conversion] case class Value(code: String, kind: R.ValueType)

  private[conversion] val PythonKeywords = Set("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
    "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield")
  private[conversion] val ReservedNames = PythonKeywords ++ Set("java_turtle", "turtle", MetadataVariable, "float")
  private[conversion] def identifier(name: String): Boolean = name.matches("[A-Za-z_][A-Za-z0-9_]*") && !ReservedNames(name)
  private[conversion] def reject(message: String): Nothing = throw IllegalArgumentException(message)
  private[conversion] def ensure(condition: Boolean, message: String): Unit = if !condition then reject(message)
  private[conversion] def attempt[A](body: => A): Either[String, A] =
    try Right(body) catch { case NonFatal(error) => Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName)) }
  private[conversion] def kindName(kind: R.ValueType): String = kind match {
    case R.ValueType.IntValue => "int"
    case R.ValueType.DoubleValue => "double"
    case R.ValueType.BooleanValue => "boolean"
    case _ => reject("The main argument array is not an editable value.")
  }
  private[conversion] def readKind(name: String): R.ValueType = name match {
    case "int" => R.ValueType.IntValue
    case "double" => R.ValueType.DoubleValue
    case "boolean" => R.ValueType.BooleanValue
    case _ => reject("Unknown Java value type in editing metadata.")
  }
  private[conversion] def pythonType(kind: R.ValueType): String = if kind == R.ValueType.DoubleValue then "float" else if kind == R.ValueType.BooleanValue then "bool" else "int"
  private[conversion] def assignable(actual: R.ValueType, expected: R.ValueType): Boolean =
    actual == expected || actual == R.ValueType.IntValue && expected == R.ValueType.DoubleValue
  private[conversion] def numeric(kind: R.ValueType): Boolean = R.isNumeric(kind)
  private[conversion] def bounded(source: String): Unit = {
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
  private[conversion] def noteContent(raw: String): String =
    SnapXmlParser.unescape(raw.replace("&#xD;", "\r").replace("&#xd;", "\r").replace("&#13;", "\r")
      .replace("&#xA;", "\n").replace("&#xa;", "\n").replace("&#10;", "\n"))
      .replace("\r\n", "\n").replace('\r', '\n')
  def hasPythonMetadata(source: String): Boolean = source.linesIterator.exists(line => PythonMetadataHeader.findFirstIn(line).nonEmpty)
  def hasSnapMetadata(xml: String): Boolean =
    SnapXmlParser.elements(xml, "notes").exists(node => noteContent(node.inner).startsWith(NotesPrefix)) ||
      SnapMetadataHeader.findFirstIn(xml).nonEmpty

  private[conversion] def metadata(program: P.Program): (Metadata, Map[R.VariableId, String]) = {
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

  private[conversion] def readMetadata(json: String): Metadata = {
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

  private[conversion] val BinaryTokens: Map[R.BinaryOperator, String] = Map(
    R.BinaryOperator.Add -> "+", R.BinaryOperator.Subtract -> "-", R.BinaryOperator.Multiply -> "*", R.BinaryOperator.Divide -> "/",
    R.BinaryOperator.Remainder -> "%", R.BinaryOperator.Less -> "<", R.BinaryOperator.LessEqual -> "<=", R.BinaryOperator.Greater -> ">",
    R.BinaryOperator.GreaterEqual -> ">=", R.BinaryOperator.Equal -> "==", R.BinaryOperator.NotEqual -> "!=")
  private[conversion] val OperationTokens = Map("add" -> "+", "sub" -> "-", "mul" -> "*", "div" -> "/", "rem" -> "%", "neg" -> "-", "pos" -> "+")
  private[conversion] val ComparisonTokens = Map("lt" -> "<", "le" -> "<=", "gt" -> ">", "ge" -> ">=", "eq" -> "==", "ne" -> "!=")
  private[conversion] def comparison(operator: R.BinaryOperator): String = ComparisonTokens.find(_._2 == BinaryTokens(operator)).map(_._1)
    .getOrElse(reject("Expected a numeric Java comparison."))
  private[conversion] def operation(operator: R.BinaryOperator): String = operator match {
    case R.BinaryOperator.Add => "add"
    case R.BinaryOperator.Subtract => "sub"
    case R.BinaryOperator.Multiply => "mul"
    case R.BinaryOperator.Divide => "div"
    case R.BinaryOperator.Remainder => "rem"
    case _ => reject("Expected a Java arithmetic operation.")
  }
  private[conversion] def arithmetic(operator: R.BinaryOperator): Boolean = Set(R.BinaryOperator.Add, R.BinaryOperator.Subtract,
    R.BinaryOperator.Multiply, R.BinaryOperator.Divide, R.BinaryOperator.Remainder)(operator)
  private[conversion] def assigned(operator: R.AssignmentOperator): R.BinaryOperator = operator match {
    case R.AssignmentOperator.Add => R.BinaryOperator.Add
    case R.AssignmentOperator.Subtract => R.BinaryOperator.Subtract
    case R.AssignmentOperator.Multiply => R.BinaryOperator.Multiply
    case R.AssignmentOperator.Divide => R.BinaryOperator.Divide
    case R.AssignmentOperator.Remainder => R.BinaryOperator.Remainder
    case _ => reject("Expected a compound assignment.")
  }
  private[conversion] def widened(expression: R.Expression, kind: R.ValueType): R.Expression =
    if expression.valueType == R.ValueType.IntValue && kind == R.ValueType.DoubleValue then R.Widen(expression) else expression
  private[conversion] def assignment(variable: R.Variable, operator: R.AssignmentOperator, value: R.Expression): R.Expression =
    if operator == R.AssignmentOperator.Set then widened(value, variable.valueType)
    else R.Binary(assigned(operator), R.Read(variable), widened(value, variable.valueType))
  private[conversion] def alwaysTrue(value: R.Expression): Boolean =
    E.evaluate(value, variable => Left(E.Failure.MissingValue(variable.id))).toOption.contains(E.Value.BooleanValue(true))
  private[conversion] def completesNormally(body: R.Block): Boolean = body.statements.forall {
    case R.Return => false
    case R.If(_, positive, Some(negative)) => completesNormally(positive) || completesNormally(negative)
    case R.While(condition, _) => !alwaysTrue(condition)
    case R.For(init, condition, _, _) => completesNormally(init) && condition.exists(value => !alwaysTrue(value))
    case _ => true
  }

  private[conversion] def finish(meta: Metadata, bodies: Map[String, Vector[String]]): ProgrammingStateJavaString = {
    ensure(bodies.keySet == meta.methods.map(_.name).toSet, "Every declared Java method must be represented exactly once.")
    val methods = meta.methods.map { method =>
      val header = if method.name == "main" then "public static void main(String[] args)" else
        s"static void ${method.name}(${method.parameters.map(variable => s"${kindName(variable.kind)} ${variable.name}").mkString(", ")})"
      "    " + header + " {\n" + bodies(method.name).map("        " + _).mkString("\n") + "\n    }"
    }
    val source = s"class ${meta.className} {\n${methods.mkString("\n\n")}\n}\n"
    P.compile(source).fold(diagnostic => reject(diagnostic.message), _ => ProgrammingStateJavaString(source))
  }
  private[conversion] def number(raw: String, snapLiteral: Boolean = false): Value = {
    if snapLiteral && raw == "-0" then Value("-0.0", R.ValueType.DoubleValue)
    else if raw.matches("-?(0|[1-9][0-9]*)") && raw.toIntOption.nonEmpty then Value(raw, R.ValueType.IntValue)
    else {
      val finite = raw.toDoubleOption.filter(_.isFinite).getOrElse(reject("Use a finite decimal Java number."))
      ensure(raw.matches("-?(?:[0-9]+(?:\\.[0-9]+)?)(?:[eE][+-]?[0-9]+)?"), "Use a finite decimal Java number.")
      val decimal = BigDecimal(raw).bigDecimal.toPlainString
      Value(if finite == 0.0 && raw.startsWith("-") then "-0.0" else if decimal.contains('.') then decimal else decimal + ".0", R.ValueType.DoubleValue)
    }
  }
  private[conversion] def widen(value: Value): Value = {
    if value.kind == R.ValueType.DoubleValue then value
    else if value.code.toIntOption.nonEmpty then Value(value.code + ".0", R.ValueType.DoubleValue)
    else Value(s"(1.0 * ${value.code})", R.ValueType.DoubleValue)
  }
  private[conversion] def typedOperation(op: String, left: Value, right: Value, floating: Boolean): Value = {
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
  private[conversion] def logical(op: String, left: Value, right: Option[Value]): Value = {
    if Set("!", "&&", "||")(op) then {
      ensure(left.kind == R.ValueType.BooleanValue && right.forall(_.kind == R.ValueType.BooleanValue), "Use boolean values in a boolean operation.")
    } else ensure(right.exists(value => numeric(left.kind) && numeric(value.kind) ||
      Set("==", "!=")(op) && left.kind == R.ValueType.BooleanValue && value.kind == R.ValueType.BooleanValue), "Compare compatible scalar values.")
    Value(right.fold(s"(!${left.code})")(value => s"(${left.code} $op ${value.code})"), R.ValueType.BooleanValue)
  }
  private[conversion] def typedComparison(op: String, left: Value, right: Value, floating: Boolean): Value = {
    ensure(ComparisonTokens.contains(op), "Unknown typed Java comparison.")
    ensure(numeric(left.kind) && numeric(right.kind), "A numeric comparison needs numeric Java values.")
    ensure(floating || left.kind == R.ValueType.IntValue && right.kind == R.ValueType.IntValue,
      "An int comparison cannot use a double value.")
    val widenedLeft = if floating && left.kind == R.ValueType.IntValue && right.kind == R.ValueType.IntValue then
      widen(left) else left
    logical(ComparisonTokens(op), widenedLeft, Some(right))
  }
  private[conversion] def call(meta: Metadata, name: String, args: Vector[Value]): String = {
    if name == "Turtle.forward" || name == "Turtle.turnRight" then {
      ensure(args.size == 1 && numeric(args.head.kind), "A Turtle command needs one numeric argument.")
    } else {
      val method = meta.method(name)
      ensure(name != "main" && args.size == method.parameters.size && args.zip(method.parameters).forall((a, p) => assignable(a.kind, p.kind)), "The method arguments do not match its declaration.")
    }
    s"$name(${args.map(_.code).mkString(", ")});"
  }

}
