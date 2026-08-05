package it.evadid.vm.io.stringPrinter.python

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter.PythonSeparation

case class BeExpressionToPythonString
(language: HumanLanguage, skipUnparsable: Boolean)
  extends GenericJavaLikeStringPrinter(
    Python, language, PythonSeparation(), skipUnparsable
  ) {

  override protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String = {
    // Positional call args (same as Java). Named "par = value" is not parsed by
    // PythonParser.parseFunctionCall and would drop Snap slot literals on reload.
    forExpression(parValue)
  }

  override protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String = {
    varName + " = " + forExpression(varValue)
  }

  override protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String = {
    if (initValue.isEmpty) s"${nameStr} : ${variableTypeString}"
    else s"${nameStr} : ${variableTypeString} = ${forExpression(initValue.get)}"
  }

  override protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String = {
    s"def ${nameStr}${parStr} -> $outputTypeStr :"
  }

  override protected def fixedRepetitionLine(amount: Int): String = {
    s"for _ in repeat(${amount}):"
  }

}
