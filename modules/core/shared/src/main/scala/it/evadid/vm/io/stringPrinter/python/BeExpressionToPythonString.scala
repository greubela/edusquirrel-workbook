package it.evadid.vm.io.stringPrinter.python

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Python}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter.PythonSeparation
import it.evadid.vm.naming.NamingStyle

case class BeExpressionToPythonString
(language: HumanLanguage, skipUnparsable: Boolean)
  extends GenericJavaLikeStringPrinter(
    Python, language, PythonSeparation(), skipUnparsable
  ) {

  override def forOther(other: BeExpression): String = other match {
    case call: BeFunctionCall if isOperatorCall(call) =>
      formatOperatorCall(call)
    case _ =>
      super.forOther(other)
  }

  private def isOperatorCall(call: BeFunctionCall): Boolean =
    call.funcDef.functionTypeInfo.funcType match
      case BeDefineFunction.Operator(_) => true
      case _ => false

  private def formatOperatorCall(call: BeFunctionCall): String = {
    val symbol = call.funcDef.functionTypeInfo.displayName.universalInterpretation()
    val args = call.funcDef.inputs.flatMap(variable => call.parameterValueMap.get(variable)).map(forExpression)
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
    s"def ${nameStr}${parStr}:"
  }

  override protected def formatFunctionParameters(inputs: List[BeDefineVariable]): String =
    inputs.map(_.name.getNameIn(language, NamingStyle.SnakeCase)).mkString("(", ", ", ")")

  override protected def fixedRepetitionLine(amount: Int): String = {
    s"for _ in range(${amount}):"
  }

  override protected def namedRangeLine(varName: String, start: String, end: String): String =
    s"for $varName in range($start, $end + 1):"

  override protected def repetitionParsingHint(amount: Int): String = ""

  override protected def emptyFunctionBody: String = "pass"

}
