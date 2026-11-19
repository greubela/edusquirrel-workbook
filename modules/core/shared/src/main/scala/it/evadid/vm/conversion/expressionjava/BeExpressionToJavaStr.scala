package it.evadid.vm.conversion.expressionjava

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Java, JavaScript, ProgrammingLanguage}
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.abstractions.{BeControlStructure, BeDefineStructure, BeExpression}
import it.evadid.vm.code.controlStructures.{BeRepeatNr, BeSequence}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.code.errors.{BeSingleLineComment, ProgrammingConversionException}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeCallTarget, BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.dependencies.{JavaTurtleLibrary, KnownDependencies}
import it.evadid.vm.conversion.printing.{GenericJavaLikeStringPrinter, ModuleLayout}
import it.evadid.vm.conversion.printing.GenericJavaLikeStringPrinter.{JavaScriptSeparation, JavaSeparation}
import it.evadid.vm.naming.NamingStyle
import it.evadid.vm.naming.NamingStyle.SnakeCase
import it.evadid.vm.types.{BeDataValueLiteral, BeDataValueUnit}

/** Java prints a program as its object classes followed by `class Main`, whose static members hold the
  * module and whose `main` runs the main sequence ([[ModuleLayout]]). JavaScript keeps loose statements.
  */
case class BeExpressionToJavaStr(language: HumanLanguage, skipUnparsable: Boolean, targetLanguage: ProgrammingLanguage = Java)
  extends GenericJavaLikeStringPrinter(
    targetLanguage,
    language,
    if (targetLanguage == JavaScript) JavaScriptSeparation() else JavaSeparation(),
    skipUnparsable
  ) derives upickle.default.ReadWriter {

  // A printer owns one rendering. Declarations are tracked per function/class scope.
  private var declared = Set.empty[String]
  private var renderingDepth = 0
  private var moduleFunctions = Set.empty[String]
  private var insideMain = false
  private var usedNames = Set.empty[String]
  private var repeatDepth = 0

  private def javaName(variable: BeDefineVariable): String = variable.name.getNameIn(language, NamingStyle.CamelCase)
  private def javaType(variable: BeDefineVariable): String = variable.variableType.formatTypeForDisplay.getInLanguage(targetLanguage)
  private def indent(text: String): String = text.linesIterator.map(line => if line.isEmpty then line else "    " + line).mkString("\n")

  override def forExpression(expression: BeExpression): String = {
    if renderingDepth == 0 then prepare(expression)
    renderingDepth += 1
    try expression match {
      case program: BeStartProgram if renderingDepth == 1 && targetLanguage == Java => module(program)
      case _ => super.forExpression(expression)
    } finally renderingDepth -= 1
  }

  private def prepare(expression: BeExpression): Unit = {
    declared = Set.empty
    insideMain = false
    repeatDepth = 0
    val statements = expression match {
      case BeStartProgram(Some(body)) => body.body
      case body: BeSequence => body.body
      case definition: BeDefineFunction => List(definition)
      case _ => Nil
    }
    val nodes = expression.subtree
    if targetLanguage == Java && statements.exists {
        case definition: BeDefineClass => definition.name.universalInterpretation() == "turtle"
        case _ => false
      } && nodes.exists {
        case call: BeFunctionCall => TurtleCommand.isTurtleMethod(call.funcDef)
        case _ => false
      } then throw ProgrammingConversionException.unsupported(
        "A user class named Turtle conflicts with the Java turtle library. Rename the class before conversion.")
    usedNames = nodes.collect { case variable: BeDefineVariable => javaName(variable) }.toSet
    moduleFunctions = statements.collect { case definition: BeDefineFunction if definition.functionTypeInfo.isMethodInClass.isEmpty =>
      definition.functionTypeInfo.displayName.universalInterpretation()
    }.toSet
  }

  /** Object classes, then `class Main` with static fields and methods and the `main` entry point. */
  private def module(program: BeStartProgram): String = {
    val layout = ModuleLayout.of(program)
    declared = layout.module.collect {
      case BeAssignVariable(target, _) => javaName(target)
      case variable: BeDefineVariable => javaName(variable)
    }.toSet
    insideMain = true
    final case class Parts(classes: Vector[String] = Vector.empty, fields: Vector[String] = Vector.empty,
        methods: Vector[String] = Vector.empty, comments: Vector[String] = Vector.empty) {
      def withComments(text: String): String = (comments :+ text).mkString("\n")
    }
    // Comments stay with the item that follows them.
    val parts = layout.module.foldLeft(Parts()) {
      case (parts, comment: BeSingleLineComment) => parts.copy(comments = parts.comments :+ forExpression(comment))
      case (parts, definition: BeDefineClass) =>
        insideMain = false
        try parts.copy(classes = parts.classes :+ parts.withComments(forExpression(definition)), comments = Vector.empty)
        finally insideMain = true
      case (parts, BeAssignVariable(target, value)) =>
        parts.copy(fields = parts.fields :+ parts.withComments(s"static ${javaType(target)} ${javaName(target)} = ${forValue(value)};"), comments = Vector.empty)
      case (parts, variable: BeDefineVariable) =>
        parts.copy(fields = parts.fields :+ parts.withComments(s"static ${javaType(variable)} ${javaName(variable)};"), comments = Vector.empty)
      case (parts, other) =>
        parts.copy(methods = parts.methods :+ parts.withComments(forExpression(other)), comments = Vector.empty)
    }
    val body = forExpression(BeSequence.optionalBody(layout.main.toList)).trim
    val main = s"public static void main(String[] unusableArgs) {\n${indent(body)}\n}"
    val groups = Seq(parts.fields.mkString("\n")).filter(_.nonEmpty) ++ parts.methods :+ main
    // Library classes follow the program so that it compiles and runs on a regular JVM.
    (parts.classes :+ s"class Main {\n${indent(groups.mkString("\n\n"))}\n}" :++ KnownDependencies.definitionsFor(program, Java)).mkString("\n\n")
  }

  override def forDefinition(definition: BeDefineStructure): String = definition match {
    case function: BeDefineFunction =>
      val previous = declared
      declared = previous ++ function.inputs.map(javaName)
      try super.forDefinition(function) finally declared = previous
    case variable: BeDefineVariable =>
      declared += javaName(variable)
      super.forDefinition(variable)
    case owner: BeDefineClass if targetLanguage == Java =>
      val previous = declared
      declared = owner.attributes.map(javaName).toSet
      try {
        val fields = owner.attributes.map(attribute => forDefinition(attribute) + ";")
        val methods = owner.methods.map(forExpression)
        val groups = Seq(fields.mkString("\n")).filter(_.nonEmpty) ++ methods
        s"class ${className(owner.name)} {\n${indent(groups.mkString("\n\n"))}\n}"
      } finally declared = previous
    case _ => super.forDefinition(definition)
  }

  override def forControlStructure(structure: BeControlStructure): String = structure match {
    case _: BeRepeatNr =>
      repeatDepth += 1
      try super.forControlStructure(structure) finally repeatDepth -= 1
    case _ => super.forControlStructure(structure)
  }

  override protected def callName(call: BeFunctionCall): String = call.funcDef.intrinsic match {
    case Some(BeIntrinsic.Print) =>
      require(call.funcDef.inputs.size == 1, "Java printing currently supports one value per call.")
      "System.out.println"
    case Some(BeIntrinsic.Turtle) => "Turtle." + JavaTurtleLibrary.methodName(call.funcDef)
    case Some(intrinsic) if KnownDependencies.javaCall(intrinsic).nonEmpty => KnownDependencies.javaCall(intrinsic).get
    case _ if call.target.isInstanceOf[BeCallTarget.Function] && !insideMain &&
        moduleFunctions(call.funcDef.functionTypeInfo.displayName.universalInterpretation()) =>
      "Main." + entityName(call.funcDef.functionTypeInfo.displayName)
    case _ => super.callName(call)
  }

  override protected def functionDeclaration(function: BeDefineFunction, parameters: String, outputType: String): String =
    function.functionTypeInfo.funcType match {
      case BeDefineFunction.Constructor() =>
        val owner = function.functionTypeInfo.isMethodInClass.getOrElse(throw IllegalArgumentException("Constructor requires an owning class."))
        s"${className(owner.name)}$parameters {"
      case BeDefineFunction.StaticMethod() => "static " + super.functionDeclaration(function, parameters, outputType)
      case BeDefineFunction.Function() if insideMain && targetLanguage == Java => "static " + super.functionDeclaration(function, parameters, outputType)
      case _ => super.functionDeclaration(function, parameters, outputType)
    }

  override protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String = {
    if (targetLanguage == JavaScript) s"function $nameStr$parStr {"
    else s"$outputTypeStr $nameStr$parStr {"
  }

  override protected def formatFunctionParameters(inputs: List[BeDefineVariable]): String =
    if (targetLanguage == JavaScript) inputs.map(_.name.getNameIn(language, SnakeCase)).mkString("(", ", ", ")")
    else super.formatFunctionParameters(inputs)

  /** Nested repeats need distinct counters that no program variable uses. */
  override protected def fixedRepetitionLine(amount: Int): String = {
    val counter = (Iterator("i", "j", "k", "m", "n") ++ Iterator.from(1).map("i" + _)).filterNot(usedNames).drop(repeatDepth - 1).next()
    s"for (int $counter = 0; $counter < $amount; $counter++) {"
  }

  override protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String = {
    forValue(parValue)
  }

  override protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String = {
    val first = !declared(varName)
    declared += varName
    val value = forValue(varValue)
    if targetLanguage == JavaScript || !first then s"$varName = $value"
    else s"$varType $varName = $value"
  }

  override protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String = {
    if (targetLanguage == JavaScript && initValue.nonEmpty) s"$nameStr = ${forValue(initValue.get)}"
    else if (targetLanguage == JavaScript) nameStr
    else if (initValue.nonEmpty) variableTypeString + " " + nameStr + " = " + forValue(initValue.get)
    else variableTypeString + " " + nameStr
  }

  override def forOther(other: BeExpression): String = other match {
    case BeUseValue(BeDataValueLiteral(raw @ ("True" | "False")), _) => raw.toLowerCase
    case BeUseValue(BeDataValueUnit(), _) if targetLanguage == Java => "null"
    case call: BeFunctionCall if targetLanguage == Java && call.funcDef.intrinsic.exists(JavaBuiltinSpelling.handles) =>
      val arguments = call.arguments
        .map(value => JavaBuiltinSpelling.Argument(value, forValue(value), forOperand(value)))
      // Like other calls, the spelling ends the line; values strip that ending again.
      JavaBuiltinSpelling.spell(call.funcDef.intrinsic.get, arguments) + ";"
    case call: BeFunctionCall if call.isOperator =>
      val arguments = call.arguments.map(forOperand)
      call.funcDef.functionTypeInfo.displayName.universalInterpretation() match {
        // Python's floor division and power have no Java operator; the Java parser reads these calls back.
        case "//" if arguments.size == 2 => s"Math.floor(${arguments.head} / ${arguments(1)})"
        case "**" if arguments.size == 2 => s"Math.pow(${arguments.head}, ${arguments(1)})"
        case symbol =>
          val operator = symbol match {
            case "and" => "&&"
            case "or" => "||"
            case "not" => "!"
            case "is" => "=="
            case "is not" => "!="
            case other => other
          }
          if (arguments.size == 1) s"$operator${arguments.head}" else arguments.mkString(s" $operator ")
      }
    case _ => super.forOther(other)
  }

}
