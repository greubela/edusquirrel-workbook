package it.evadid.vm.io.stringPrinter

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, ProgrammingLanguage}
import it.evadid.core.util.CodeStringBuilderMutable
import it.evadid.vm.code.abstractions.{BeControlStructure, BeDefineStructure, BeExpression}
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.io.stringPrinter.GenericJavaLikeStringPrinter.SeparateStructures
import it.evadid.vm.naming.NamingStyle.{CamelCase, SnakeCase}
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}

abstract class GenericJavaLikeStringPrinter(
                                             progLang: ProgrammingLanguage,
                                             humLang: HumanLanguage,
                                             sepLogic: SeparateStructures,
                                             skipUnparsable: Boolean
                                           ) {
  private case class NameInfo(entityName: BeEntityName) {
    val inCurLanguage: String = entityName.getNameIn(humLang, sepLogic.preferedDisplayStyle)
    val associatedComment: String = s"${sepLogic.startSingleLineComment}EvaEntityName(${entityName.universalInterpretation()})"
  }

  private case class TypeInfo(dataType: BeDataType) {
    val inCurLanguage: String = dataType.formatTypeForDisplay.getInLanguage(progLang)
  }

  def forExpression(exprOp: Option[BeExpression]): String = exprOp.map(forExpression).getOrElse("")

  def forExpression(expr: BeExpression): String = expr.match {
    case cs: BeControlStructure => forControlStructure(cs)
    case ds: BeDefineStructure => forDefinition(ds)
    case other => forOther(other)
  }

  /*protected def assignToString(target: BeDefineVariable, value: BeExpression, willBeInlined: Boolean): String = {
    assignToString(
      target.variableType.formatTypeForDisplay.getInLanguage(progLang),
      NameInfo(target.name).inCurLanguage,
      value, willBeInlined
    )
  }*/

  protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String

  protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String

  protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String

  protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String

  protected def fixedRepetitionLine(amount: Int): String

  protected def forStatement(expr: BeExpression): String = {
    forExpression(expr) + sepLogic.endLineWith
  }


  // protected def assignToString(typeString: String, nameString: String, value: BeExpression, willBeInlined: Boolean): String


  def forOther(other: BeExpression): String = other.match {
    case BeExpressionUnparsable(originalSource, message) => if (skipUnparsable) "" else originalSource
    case BeSingleLineComment(commentStr) => s"${sepLogic.startSingleLineComment}${commentStr}"
    case BeExpressionUnsupported(originalSource) => originalSource
    case BeReturn(value) => s"return ${forExpression(value)}${sepLogic.endLineWith}"
    case BeStartProgram(startSequence) => forExpression(startSequence)
    // usage
    case BeAssignVariable(target, value) => {
      assignToDefinedVar(
        NameInfo(target.name).inCurLanguage,
        target.variableType.formatTypeForDisplay.getInLanguage(progLang),
        value
      )
    }
    case BeFunctionCall(funcDef, parameterValueMap) => {
      if (funcDef.functionTypeInfo.isNamed.isEmpty) ???
      else {
        val nameInfo = NameInfo(funcDef.functionTypeInfo.isNamed.get)
        val callParInfo = funcDef.inputs.map(curIn => {
          val name = NameInfo(curIn.name)
          val typeStr = curIn.variableType.formatTypeForDisplay.getInLanguage(progLang)
          assignToFunctionPar(name.inCurLanguage, typeStr, parameterValueMap(curIn))
        }).mkString("(", ", ", ")")
        s"${nameInfo.inCurLanguage}${callParInfo}${sepLogic.endLineWith}"
      }
    }
    case BeUseValue(value, contextIfKnown) => value.match {
      case BeDataValueUnit() => ""
      case BeUseValueReference(variable) => NameInfo(variable.name).inCurLanguage
      case BeDataValueLiteral(str) => str
    }

    case _ => throw new UnsupportedOperationException(s"GenericJavaLikeStringPrinter -> not implemented for ${other.getClass.getSimpleName}")
  }

  def forDefinition(ds: BeDefineStructure): String = ds.match {
    case BeDefineClass(name, attributes, methods) => {
      CodeStringBuilderMutable()
        .appendNextLine(s"class ${NameInfo(name).inCurLanguage}${sepLogic.startBlockWith} ${NameInfo(name).associatedComment}")
        .changeIntLevel(1)
        .appendAllAsLines(attributes.map(forExpression))
        .appendNextLine("")
        .appendAllAsLines(methods.map(forExpression))
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case BeDefineFunction(inputs, outputs, body, functionTypeInfo) => {
      if (functionTypeInfo.isNamed.isEmpty) ???
      else {
        val name = NameInfo(functionTypeInfo.isNamed.get)
        val par = inputs.map(forExpression).map(_.replace("\n", "")).mkString("(", ", ", ")")
        val outTypeStr = outputs.map(_.variableType.formatTypeForDisplay.getInLanguage(progLang)).getOrElse(BeDataType.Unit.formatTypeForDisplay.getInLanguage(progLang))
        CodeStringBuilderMutable()
          // .appendNextLine(s"def ${name.inCurLanguage}${par} -> ${outTypeStr}: ${name.associatedComment}")
          .appendNextLine(defineFunctionLine(name.inCurLanguage, par, outTypeStr))
          .changeIntLevel(1)
          .appendAsLines(forExpression(body))
          .appendNextLine(sepLogic.endBlockWith)
          .toString
      }
    }
    case BeDefineVariable(name, variableType, initValueOp) => {
      val nameInfo = NameInfo(name)
      val varType = variableType.formatTypeForDisplay.getInLanguage(progLang)
      defineVariableLine(nameInfo.inCurLanguage, varType, initValueOp) + nameInfo.associatedComment
    }
  }

  def forControlStructure(cs: BeControlStructure): String = cs.match {
    case BeIfElse(condition, thenBody, elseBody) => {
      val conditionString = forExpression(condition).replaceAll("\n", "")
      val res = CodeStringBuilderMutable()
        .appendNextLine(s"if $conditionString${sepLogic.startBlockWith}")
        .changeIntLevel(1)
        .appendAsLines(forExpression(thenBody))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
      if (elseBody.body.nonEmpty) {
        res
          .appendInLine(s"else${sepLogic.startBlockWith}")
          .changeIntLevel(1)
          .appendAsLines(forExpression(elseBody))
          .changeIntLevel(-1)
          .appendNextLine(sepLogic.endBlockWith)
      }
      res.toString
    }
    case BeRepeatNr(amount, body) => {
      CodeStringBuilderMutable()
        .appendNextLine(fixedRepetitionLine(amount) + sepLogic.startSingleLineComment + "EvaParsingHint(BeRepeatNr)")
        .changeIntLevel(1)
        .appendAsLines(forExpression(body))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case BeSequence(body, sequenceInfo) => {
      CodeStringBuilderMutable()
        .appendAllAsLines(body.map(forStatement))
        .toString
    }
    case BeWhile(condition, body) => {
      val conditionString = forExpression(condition).replaceAll("\n", "")
      CodeStringBuilderMutable()
        .appendNextLine(s"while $conditionString${sepLogic.startBlockWith}")
        .changeIntLevel(1)
        .appendAsLines(forExpression(body))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case _ => ???
  }

}

object GenericJavaLikeStringPrinter {

  sealed trait SeparateStructures {
    def startBlockWith: String

    def endBlockWith: String

    def startSingleLineComment: String

    def startBlockComment: String

    def endBlockComment: String

    def preferedDisplayStyle: NamingStyle

    def endLineWith: String
  }

  case class JavaSeparation() extends SeparateStructures {

    override def startBlockWith: String = "{"

    override def endBlockWith: String = "}"


    override def startSingleLineComment: String = "//"

    override def startBlockComment: String = "/*"

    override def endBlockComment: String = "*/"

    override def preferedDisplayStyle: NamingStyle = CamelCase

    override def endLineWith: String = ";"
  }

  case class PythonSeparation() extends SeparateStructures {

    override def startBlockWith: String = ":"

    override def endBlockWith: String = ""

    override def startSingleLineComment: String = "#"

    override def startBlockComment: String = "'''"

    override def endBlockComment: String = "'''"

    override def preferedDisplayStyle: NamingStyle = SnakeCase

    override def endLineWith: String = ""
  }

}