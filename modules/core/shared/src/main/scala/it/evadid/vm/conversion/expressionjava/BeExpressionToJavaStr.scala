package it.evadid.vm.conversion.expressionjava

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Java, JavaScript, ProgrammingLanguage}
import it.evadid.vm.code.abstractions.{BeExpression, BeDefineStructure}
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.conversion.printing.GenericJavaLikeStringPrinter
import it.evadid.vm.conversion.printing.GenericJavaLikeStringPrinter.{JavaScriptSeparation, JavaSeparation}
import it.evadid.vm.naming.NamingStyle.SnakeCase
import it.evadid.vm.code.defining.BeDefineVariable

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
  override def forExpression(expression: BeExpression): String = {
    if renderingDepth == 0 then declared = Set.empty
    renderingDepth += 1
    try super.forExpression(expression) finally renderingDepth -= 1
  }
  override def forDefinition(definition: BeDefineStructure): String = definition match {
    case function: BeDefineFunction =>
      val previous = declared
      declared = previous ++ function.inputs.map(_.name.getNameIn(language, it.evadid.vm.naming.NamingStyle.CamelCase))
      try super.forDefinition(function) finally declared = previous
    case variable: BeDefineVariable =>
      declared += variable.name.getNameIn(language, it.evadid.vm.naming.NamingStyle.CamelCase)
      super.forDefinition(variable)
    case _ => super.forDefinition(definition)
  }

  override protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String = {
    if (targetLanguage == JavaScript) s"function $nameStr$parStr {"
    else s"$outputTypeStr $nameStr$parStr{"
  }

  override protected def formatFunctionParameters(inputs: List[BeDefineVariable]): String =
    if (targetLanguage == JavaScript) inputs.map(_.name.getNameIn(language, SnakeCase)).mkString("(", ", ", ")")
    else super.formatFunctionParameters(inputs)

  override protected def fixedRepetitionLine(amount: Int): String = {
    val rnd = "i_" + (System.currentTimeMillis().hashCode() % 1000)
    s"for(int ${rnd} = 0; ${rnd} < ${amount}; ${rnd}++){"
  }

  override protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String = {
    forExpression(parValue)
  }

  override protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String = {
    val first = !declared(varName)
    declared += varName
    val value = forExpression(varValue)
    if targetLanguage == JavaScript || !first then s"$varName = $value"
    else s"$varType $varName = $value"
  }

  override protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String = {
    if (targetLanguage == JavaScript && initValue.nonEmpty) s"$nameStr = ${forExpression(initValue.get)}"
    else if (targetLanguage == JavaScript) nameStr
    else if (initValue.nonEmpty) variableTypeString + " " + nameStr + " = " + forExpression(initValue.get)
    else variableTypeString + " " + nameStr
  }

  override def forOther(other: BeExpression): String = other match {
    case call: BeFunctionCall if call.funcDef.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Operator] =>
      val operator = call.funcDef.functionTypeInfo.displayName.universalInterpretation() match {
        case "and" => "&&"
        case "or" => "||"
        case "not" => "!"
        case other => other
      }
      val arguments = call.funcDef.inputs.flatMap(call.parameterValueMap.get).map(forOperand)
      if (arguments.size == 1) s"$operator${arguments.head}" else arguments.mkString(s" $operator ")
    case call: BeFunctionCall if it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.isTurtleMethod(call.funcDef) =>
      "Turtle." + super.forOther(call)
    case _ => super.forOther(other)
  }

}
