package it.evadid.vm.conversion.expressionpython


import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Python}
import it.evadid.vm.code.abstractions.{BeDefineStructure, BeExpression}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.printing.{GenericJavaLikeStringPrinter, ModuleLayout}
import it.evadid.vm.conversion.printing.GenericJavaLikeStringPrinter.PythonSeparation
import it.evadid.vm.naming.NamingStyle

case class BeExpressionToPythonString
(language: HumanLanguage, skipUnparsable: Boolean)
  extends GenericJavaLikeStringPrinter(
    Python, language, PythonSeparation(), skipUnparsable
  ) derives upickle.default.ReadWriter {

  override protected def currentInstanceName: String = "self"
  override protected def constructorCallPrefix: String = ""
  override protected def callName(call: BeFunctionCall): String =
    if it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.isTurtleMethod(call.funcDef) then
      "turtle." + entityName(call.funcDef.functionTypeInfo.displayName)
    else if it.evadid.vm.simulation.data.DataLibraries.isDataMethod(call.funcDef) then
      it.evadid.vm.simulation.data.DataLibraries.ownerName(call.funcDef).get + "." + entityName(call.funcDef.functionTypeInfo.displayName)
    else super.callName(call)
  override protected def functionDeclaration(function: BeDefineFunction, parameters: String, outputType: String): String =
    function.functionTypeInfo.funcType match {
      case BeDefineFunction.Constructor() => s"def __init__$parameters -> None:"
      case BeDefineFunction.StaticMethod() => "@staticmethod\n" + super.functionDeclaration(function, parameters, outputType)
      case _ => super.functionDeclaration(function, parameters, outputType)
    }

  override protected def formatFunctionParameters(function: BeDefineFunction): String = {
    val explicit = formatFunctionParameters(function.inputs)
    if function.functionTypeInfo.funcType == BeDefineFunction.Method() || function.functionTypeInfo.funcType == BeDefineFunction.Constructor() then
      "(self" + (if function.inputs.isEmpty then ")" else ", " + explicit.drop(1))
    else explicit
  }

  override def forDefinition(definition: BeDefineStructure): String = definition match {
    case function: BeDefineFunction =>
      val previous = declared
      val parameters = function.inputs.map(input => entityName(input.name)).toSet
      // A function assigns module variables (as in the shared tree); Python needs `global` for that.
      val globals = ModuleLayout.assigned(function.body).map(variable => entityName(variable.name))
        .distinct.filter(name => moduleVariables(name) && !parameters(name))
      // Parameters are annotated in the signature; names declared `global` cannot be annotated.
      declared = parameters ++ globals
      val printed = if globals.isEmpty then function else function.copy(body = function.body.copy(body =
        it.evadid.vm.code.errors.BeExpressionUnsupported(s"global ${globals.mkString(", ")}", Some(Python)) +: function.body.body))
      try super.forDefinition(printed) finally declared = previous
    case BeDefineVariable(name, variableType, initValue) =>
      declared += entityName(name)
      defineVariableLine(
        entityName(name),
        variableType.formatTypeForDisplay.getInLanguage(Python),
        initValue
      )
    case _ => super.forDefinition(definition)
  }

  override def forOther(other: BeExpression): String = other match {
    case call: BeFunctionCall if call.isOperator =>
      formatOperatorCall(call)
    case _ =>
      super.forOther(other)
  }

  private def formatOperatorCall(call: BeFunctionCall): String = {
    val symbol = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
    def isText(value: BeExpression) = value.staticInformationExpression.staticType == it.evadid.vm.types.BeDataType.String
    // Python joins text only with text, so other values in a text `+` are converted with str().
    val joinsText = symbol == "+" && call.arguments.exists(isText)
    val args = call.arguments.map(value => if joinsText && !isText(value) then s"str(${forValue(value)})" else forOperand(value))
    symbol match {
      case "not" =>
        s"not ${args.headOption.getOrElse("")}"
      case _ if args.size >= 2 =>
        args.mkString(s" $symbol ")
      case _ if args.size == 1 =>
        s"$symbol${args.head}"
      case _ =>
        super.forOther(call)
    }
  }

  override protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String = {
    // Positional call args (same as Java). Named "par = value" is not parsed by
    // bound by the Python conversion and would drop Snap slot literals on reload.
    forValue(parValue)
  }

  /** Names already annotated in the current scope: a variable's type hint is printed once. */
  private var declared = Set.empty[String]
  private var renderingDepth = 0

  /** Variables declared or assigned by module-level statements of the program being printed. */
  private var moduleVariables = Set.empty[String]

  override def forExpression(expression: BeExpression): String = {
    if renderingDepth == 0 then {
      declared = Set.empty
      moduleVariables = expression match {
        case it.evadid.vm.code.others.BeStartProgram(Some(body)) => body.body.flatMap {
          case variable: BeDefineVariable => Some(variable)
          case it.evadid.vm.code.usage.BeAssignVariable(target, _) => Some(target)
          case _ => None
        }.map(variable => entityName(variable.name)).toSet
        case _ => Set.empty
      }
    }
    renderingDepth += 1
    try super.forExpression(expression) finally renderingDepth -= 1
  }

  override protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String = {
    val renderedValue = forValue(varValue)
    val first = !declared(varName)
    declared += varName
    if (renderedValue.trim.startsWith("lambda ") || !first) s"$varName = $renderedValue"
    else s"$varName: $varType = $renderedValue"
  }

  override protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String = {
    if (initValue.isEmpty) s"$nameStr: $variableTypeString"
    else s"$nameStr: $variableTypeString = ${forValue(initValue.get)}"
  }

  override protected def defineClassLine(nameStr: String): String =
    s"class $nameStr:"

  override protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String = {
    s"def $nameStr$parStr -> $outputTypeStr:"
  }

  override protected def formatFunctionParameters(inputs: List[BeDefineVariable]): String =
    inputs.map(variable =>
      s"${entityName(variable.name)}: ${variable.variableType.formatTypeForDisplay.getInLanguage(Python)}"
    ).mkString("(", ", ", ")")

  override protected def repetitionLine(amount: String): String = s"for _ in range($amount):"

  override protected def namedRangeLine(varName: String, start: String, end: String, down: Boolean): String = {
    val minusOne = """^(.+)\s-\s1$""".r
    val plusOne = """^(.+)\s\+\s1$""".r
    if down then end match {
      case literal if literal.toIntOption.nonEmpty => s"for $varName in range($start, ${literal.toInt - 1}, -1):"
      case plusOne(stop) => s"for $varName in range($start, ${stop.trim}, -1):"
      case _ => s"for $varName in range($start, $end - 1, -1):"
    }
    else (start, end) match {
      case ("0", minusOne(stop)) => s"for $varName in range(${stop.trim}):"
      case ("0", digits) if digits.toIntOption.nonEmpty => s"for $varName in range(${digits.toInt + 1}):"
      case (_, last) if last.toIntOption.nonEmpty => s"for $varName in range($start, ${last.toInt + 1}):"
      case (_, minusOne(stop)) => s"for $varName in range($start, ${stop.trim}):"
      case _ => s"for $varName in range($start, $end + 1):"
    }
  }

  override protected def emptyFunctionBody: String = "pass"

}
