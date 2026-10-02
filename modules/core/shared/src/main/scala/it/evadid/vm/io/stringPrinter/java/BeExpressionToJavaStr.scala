package it.evadid.vm.io.stringPrinter.java

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Java, JavaScript, ProgrammingLanguage}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter.{JavaScriptSeparation, JavaSeparation}
import it.evadid.vm.naming.NamingStyle.SnakeCase
import it.evadid.vm.code.defining.BeDefineVariable

case class BeExpressionToJavaStr(language: HumanLanguage, skipUnparsable: Boolean, targetLanguage: ProgrammingLanguage = Java)

  extends GenericJavaLikeStringPrinter(
    targetLanguage,
    language,
    if (targetLanguage == JavaScript) JavaScriptSeparation() else JavaSeparation(),
    skipUnparsable
  ) {

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
    if (targetLanguage == JavaScript) s"$varName = ${forExpression(varValue)}"
    else s"$varType $varName = ${forExpression(varValue)}"
  }

  override protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String = {
    if (targetLanguage == JavaScript && initValue.nonEmpty) s"$nameStr = ${forExpression(initValue.get)}"
    else if (targetLanguage == JavaScript) nameStr
    else if (initValue.nonEmpty) variableTypeString + " " + nameStr + " = " + forExpression(initValue.get)
    else variableTypeString + " " + nameStr
  }

  override def forOther(other: BeExpression): String = other match {
    case call: BeFunctionCall if call.funcDef.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Operator] =>
      val operator = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
      val arguments = call.funcDef.inputs.flatMap(call.parameterValueMap.get).map(forExpression)
      if (arguments.size == 1) s"$operator${arguments.head}" else arguments.mkString(s" $operator ")
    case _ => super.forOther(other)
  }

}
