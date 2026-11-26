package it.evadid.vm.conversion.expressionjava

import it.evadid.vm.parsing.source.SourceText.indent
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
  ) {

  // A printer owns one rendering. Declarations are tracked per function/class scope.
  private var declared = Set.empty[String]
  private var renderingDepth = 0
  private var moduleFunctions = Set.empty[String]
  private var insideMain = false
  private var usedNames = Set.empty[String]
  private var repeatDepth = 0

  private def javaName(variable: BeDefineVariable): String = entityName(variable.name)
  private def javaType(variable: BeDefineVariable): String = variable.variableType.formatTypeForDisplay.getInLanguage(targetLanguage)
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
    case Some(BeIntrinsic.Print) => "System.out.println"
    case Some(BeIntrinsic.PrintErr) => "System.err.println"
    case Some(BeIntrinsic.Turtle) => "Turtle." + JavaTurtleLibrary.methodName(call.funcDef)
    case Some(BeIntrinsic.NumberList | BeIntrinsic.Sudoku) =>
      it.evadid.vm.modules.BeModules.qualifiedName(call.funcDef).get + "." + entityName(call.funcDef.functionTypeInfo.displayName)
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
  override protected def repetitionLine(amount: String): String = {
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

  /** Like Python's `print(a, b)`, several values print on one line separated by spaces. */
  private def printCall(call: BeFunctionCall): String =
    s"${if call.funcDef.intrinsic.contains(BeIntrinsic.PrintErr) then "System.err.println" else "System.out.println"}(${call.arguments.map(forOperand).mkString(" + \" \" + ")});"

  override protected def conditionText(condition: BeSequence): String = condition.body match {
    case Seq(value) if targetLanguage == Java => JavaSpelling.truthValue(value, forValue(value))
    case _ => super.conditionText(condition)
  }

  override def forOther(other: BeExpression): String = other match {
    case BeUseValue(BeDataValueLiteral(raw @ ("True" | "False")), _) => raw.toLowerCase
    // Java strings are double-quoted: Python's '…' literals keep their text.
    case BeUseValue(BeDataValueLiteral(raw), _) if targetLanguage == Java && raw.startsWith("'") && it.evadid.vm.types.StringLiteral.isQuoted(raw) =>
      it.evadid.vm.types.StringLiteral.quote(it.evadid.vm.types.StringLiteral.content(raw))
    case BeUseValue(BeDataValueUnit(), _) if targetLanguage == Java => "null"
    case call: BeFunctionCall if targetLanguage == Java && call.funcDef.intrinsic.exists(value => value == BeIntrinsic.Print || value == BeIntrinsic.PrintErr) && call.arguments.size != 1 =>
      printCall(call)
    case call: BeFunctionCall if targetLanguage == Java && call.funcDef.intrinsic.exists(JavaSpelling.handles) =>
      val arguments = call.arguments
        .map(value => JavaSpelling.Argument(value, forValue(value), forOperand(value)))
      // Like other calls, the spelling ends the line; values strip that ending again.
      JavaSpelling.spell(call.funcDef.intrinsic.get, arguments) + ";"
    case call: BeFunctionCall if call.isOperator && targetLanguage == Java =>
      val symbol = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
      val values = call.arguments
      // Java compares text by content with equals, and only booleans are truth values.
      def truth(value: BeExpression) = {
        val operand = forOperand(value)
        val text = JavaSpelling.truthValue(value, operand)
        if text != operand && symbol == "not" then s"($text)" else text
      }
      val arguments = if Set("and", "or", "not")(symbol) then values.map(truth) else values.map(forOperand)
      (symbol, values) match {
        // Python's floor division and power have no Java operator; the Java parser reads these calls back.
        case ("//", Seq(_, _)) => s"Math.floor(${arguments.head} / ${arguments(1)})"
        case ("**", Seq(_, _)) => s"Math.pow(${arguments.head}, ${arguments(1)})"
        // Python's `/` always divides exactly; a whole-number dividend would make Java divide integers.
        case ("/", Seq(BeUseValue(BeDataValueLiteral(dividend), _), _)) if dividend.trim.matches("[0-9]+") =>
          s"${dividend.trim}.0 / ${arguments(1)}"
        case ("==" | "!=", Seq(left, right)) if JavaSpelling.isText(left) || JavaSpelling.isText(right) =>
          val (receiver, other) = if JavaSpelling.isText(left) then (arguments.head, arguments(1)) else (arguments(1), arguments.head)
          (if symbol == "!=" then "!" else "") + s"$receiver.equals($other)"
        case _ =>
          val operator = JavaSpelling.javaOperator(symbol)
          if (arguments.size == 1) s"$operator${arguments.head}" else arguments.mkString(s" $operator ")
      }
    case call: BeFunctionCall if call.isOperator =>
      val arguments = call.arguments.map(forOperand)
      val operator = JavaSpelling.javaOperator(call.funcDef.functionTypeInfo.displayName.universalInterpretation())
      if (arguments.size == 1) s"$operator${arguments.head}" else arguments.mkString(s" $operator ")
    case _ => super.forOther(other)
  }

}
