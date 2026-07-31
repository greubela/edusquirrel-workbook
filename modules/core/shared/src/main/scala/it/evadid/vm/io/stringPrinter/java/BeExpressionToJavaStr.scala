package it.evadid.vm.io.stringPrinter.java

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Java}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter.JavaSeparation

case class BeExpressionToJavaStr(language: HumanLanguage, skipUnparsable: Boolean)

  extends GenericJavaLikeStringPrinter(
    Java,
    language,
    JavaSeparation(),
    skipUnparsable
  ) {

  override protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String = {
    s"${outputTypeStr} ${nameStr}${parStr}{"
  }

  override protected def fixedRepetitionLine(amount: Int): String = {
    val rnd = "i_" + (System.currentTimeMillis().hashCode() % 1000)
    s"for(int ${rnd} = 0; ${rnd} < ${amount}; ${rnd}++){"
  }


  override protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String = {
    forExpression(parValue)
  }

  override protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String = {
    varName + " = " + forExpression(varValue)
  }

  override protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String = {
    if (initValue.nonEmpty) variableTypeString + " " + nameStr + " = " + forExpression(initValue.get)
    else variableTypeString + " " + nameStr
  }

}

