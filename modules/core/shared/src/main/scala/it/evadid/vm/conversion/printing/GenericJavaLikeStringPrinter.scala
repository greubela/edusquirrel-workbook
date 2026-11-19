package it.evadid.vm.conversion.printing

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, ProgrammingLanguage}
import it.evadid.core.util.CodeStringBuilderMutable
import it.evadid.vm.code.abstractions.{BeControlStructure, BeDefineStructure, BeExpression}
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.*
import it.evadid.vm.conversion.printing.GenericJavaLikeStringPrinter.{JavaSeparation, PythonSeparation, SeparateStructures}
import it.evadid.vm.naming.NamingStyle.{CamelCase, SnakeCase}
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}

abstract class GenericJavaLikeStringPrinter(
                                             progLang: ProgrammingLanguage,
                                             humLang: HumanLanguage,
                                             sepLogic: SeparateStructures,
                                             skipUnparsable: Boolean
                                           ) {
  private case class NameInfo(entityName: BeEntityName) derives upickle.default.ReadWriter {
    val inCurLanguage: String = entityName.getNameIn(humLang, sepLogic.preferedDisplayStyle)
    val associatedComment: String = s"${sepLogic.startSingleLineComment}EvaEntityName(${entityName.universalInterpretation()})"
  }

  private case class TypeInfo(dataType: BeDataType) derives upickle.default.ReadWriter {
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

  protected def forValue(expression: BeExpression): String =
    if sepLogic.endLineWith.nonEmpty then forExpression(expression).stripSuffix(sepLogic.endLineWith) else forExpression(expression)

  protected def forOperand(expression: BeExpression): String = {
    val rendered = forValue(expression)
    expression match {
      case call: BeFunctionCall if call.funcDef.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Operator] => s"($rendered)"
      case _ => rendered
    }
  }

  protected def entityName(name: BeEntityName): String = NameInfo(name).inCurLanguage
  protected def className(name: BeEntityName): String = BeDataType.ClassType(name).formatTypeForDisplay.getInLanguage(progLang)
  protected def currentInstanceName: String = "this"
  protected def constructorCallPrefix: String = "new "
  protected def callName(call: BeFunctionCall): String = {
    val definition = call.funcDef
    def owner = definition.functionTypeInfo.isMethodInClass
      .getOrElse(throw IllegalArgumentException("Method or constructor has no owning class."))
    call.target match {
      case BeCallTarget.Function(_) => entityName(definition.functionTypeInfo.displayName)
      case BeCallTarget.InstanceMethod(receiver, _) => forOperand(receiver) + "." + entityName(definition.functionTypeInfo.displayName)
      case BeCallTarget.StaticMethod(_) => className(owner.name) + "." + entityName(definition.functionTypeInfo.displayName)
      case BeCallTarget.Constructor(_) => constructorCallPrefix + className(owner.name)
    }
  }
  protected def functionDeclaration(function: BeDefineFunction, parameters: String, outputType: String): String =
    defineFunctionLine(entityName(function.functionTypeInfo.displayName), parameters, outputType)

  protected def formatFunctionParameters(function: BeDefineFunction): String =
    formatFunctionParameters(function.inputs)

  protected def assignToFunctionPar(parName: String, parType: String, parValue: BeExpression): String

  protected def assignToDefinedVar(varName: String, varType: String, varValue: BeExpression): String

  protected def defineVariableLine(nameStr: String, variableTypeString: String, initValue: Option[BeExpression]): String

  protected def defineClassLine(nameStr: String, nameComment: String): String =
    s"class $nameStr${sepLogic.startBlockWith} $nameComment"

  protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String

  protected def formatFunctionParameters(inputs: List[BeDefineVariable]): String =
    inputs.map(forExpression).map(_.replace("\n", "")).mkString("(", ", ", ")")

  protected def fixedRepetitionLine(amount: Int): String

  protected def namedRangeLine(varName: String, start: String, end: String): String =
    s"for(int $varName = $start; $varName <= $end; $varName++)${sepLogic.startBlockWith}"

  /** Optional trailing parse hint after a fixed-count repeat line; empty for idiomatic Python. */
  protected def repetitionParsingHint(amount: Int): String =
    sepLogic.startSingleLineComment + "EvaParsingHint(BeRepeatNr)"

  /** Printed when a function body would otherwise be empty. Python overrides this with `pass`. */
  protected def emptyFunctionBody: String = ""

  protected def forStatement(expr: BeExpression): String = {
    forExpression(expr) + (expr match {
      case _: BeControlStructure | _: BeReturn | _: BeFunctionCall => ""
      case _ => sepLogic.endLineWith
    })
  }

  // protected def assignToString(typeString: String, nameString: String, value: BeExpression, willBeInlined: Boolean): String

  def forOther(other: BeExpression): String = other.match {
    case source: BeExpressionUnparsable => if (skipUnparsable) "" else source.sourceFor(progLang)
    case BeSingleLineComment(commentStr) => s"${sepLogic.startSingleLineComment}${commentStr}"
    case source: BeExpressionUnsupported => source.sourceFor(progLang)
    case BeCurrentInstance(_) => currentInstanceName
    case BeMemberAccess(receiver, member) => s"${forOperand(receiver)}.${entityName(member.name)}"
    case BeAssignMember(receiver, member, value) => s"${forOperand(receiver)}.${entityName(member.name)} = ${forOperand(value)}"
    case BeReturn(value) => s"return ${value.map(forValue).getOrElse("")}${sepLogic.endLineWith}"
    case BeStartProgram(startSequence) => forExpression(startSequence)
    // usage
    case BeAssignVariable(target, value) => {
      assignToDefinedVar(
        NameInfo(target.name).inCurLanguage,
        target.variableType.formatTypeForDisplay.getInLanguage(progLang),
        value
      )
    }
    case call: BeFunctionCall => {
      val funcDef = call.funcDef
      val parameterValueMap = call.parameterValueMap
      if (funcDef.functionTypeInfo.isNamed.isEmpty && !call.target.isInstanceOf[BeCallTarget.Constructor])
        throw UnsupportedOperationException("Anonymous function calls are not supported by this printer.")
      else {
        val callParInfo = funcDef.inputs.map(curIn => {
          val name = NameInfo(curIn.name)
          val typeStr = curIn.variableType.formatTypeForDisplay.getInLanguage(progLang)
          assignToFunctionPar(name.inCurLanguage, typeStr, parameterValueMap(curIn))
        }).mkString("(", ", ", ")")
        s"${callName(call)}${callParInfo}${sepLogic.endLineWith}"
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
        .appendNextLine(defineClassLine(className(name), NameInfo(name).associatedComment))
        .changeIntLevel(1)
        .appendAllAsLines(attributes.map(forStatement))
        .appendNextLine("")
        .appendAllAsLines(methods.map(forExpression))
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case function @ BeDefineFunction(inputs, outputs, body, functionTypeInfo, _) => {
      if (functionTypeInfo.isNamed.isEmpty && functionTypeInfo.funcType != BeDefineFunction.Constructor())
        throw UnsupportedOperationException("Anonymous function declarations are not supported by this printer.")
      else {
        val par = formatFunctionParameters(function)
        val outTypeStr = outputs.map(_.variableType.formatTypeForDisplay.getInLanguage(progLang)).getOrElse(BeDataType.Unit.formatTypeForDisplay.getInLanguage(progLang))
        val renderedBody = forExpression(body)
        val bodyText = if renderedBody.trim.isEmpty then emptyFunctionBody else renderedBody
        CodeStringBuilderMutable()
          .appendNextLine(functionDeclaration(function, par, outTypeStr))
          .changeIntLevel(1)
          .appendAsLines(bodyText)
          .changeIntLevel(-1)
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
      val conditionString = forExpression(condition).replaceAll("\n", "").stripSuffix(sepLogic.endLineWith)
      val formattedCondition = sepLogic match {
        case _: JavaSeparation => s"($conditionString)"
        case _ => s" $conditionString"
      }
      val res = CodeStringBuilderMutable()
        .appendNextLine(s"if$formattedCondition${sepLogic.startBlockWith}")
        .changeIntLevel(1)
        .appendAsLines(forExpression(thenBody))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
      if (elseBody.body.nonEmpty) {
        res
          .appendInLine(sepLogic match {
            case _: PythonSeparation => s"else${sepLogic.startBlockWith}"
            case _ => s" else ${sepLogic.startBlockWith}"
          })
          .changeIntLevel(1)
          .appendAsLines(forExpression(elseBody))
          .changeIntLevel(-1)
          .appendNextLine(sepLogic.endBlockWith)
      }
      res.toString
    }
    case BeRepeatNr(amount, body) => {
      CodeStringBuilderMutable()
        .appendNextLine(fixedRepetitionLine(amount) + repetitionParsingHint(amount))
        .changeIntLevel(1)
        .appendAsLines(forExpression(body))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case BeFor(variable, start, end, body) => {
      val varName = NameInfo(variable.name).inCurLanguage
      val startStr = forExpression(start).replaceAll("\n", "").trim
      val endStr = forExpression(end).replaceAll("\n", "").trim
      CodeStringBuilderMutable()
        .appendNextLine(namedRangeLine(varName, startStr, endStr))
        .changeIntLevel(1)
        .appendAsLines(forExpression(body))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case BeSequence(body, sequenceInfo) => {
      val rendered = CodeStringBuilderMutable()
        .appendAllAsLines(body.map(forStatement))
        .toString
      if (sepLogic.isInstanceOf[PythonSeparation] && levelIsTopLevel(rendered)) rendered + "\n" else rendered
    }
    case BeWhile(condition, body) => {
      val conditionString = forExpression(condition).replaceAll("\n", "").stripSuffix(sepLogic.endLineWith)
      val formattedCondition = sepLogic match {
        case _: PythonSeparation => s" $conditionString"
        case _ => s" ($conditionString) "
      }
      CodeStringBuilderMutable()
        .appendNextLine(s"while$formattedCondition${sepLogic.startBlockWith}")
        .changeIntLevel(1)
        .appendAsLines(forExpression(body))
        .changeIntLevel(-1)
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case _ => ???
  }

  private def levelIsTopLevel(rendered: String): Boolean = rendered.nonEmpty && !rendered.startsWith(" ")

}

object GenericJavaLikeStringPrinter {

  sealed trait SeparateStructures derives upickle.default.ReadWriter {
    def startBlockWith: String

    def endBlockWith: String

    def startSingleLineComment: String

    def startBlockComment: String

    def endBlockComment: String

    def preferedDisplayStyle: NamingStyle

    def endLineWith: String
  }

  case class JavaSeparation() extends SeparateStructures derives upickle.default.ReadWriter {

    override def startBlockWith: String = "{"

    override def endBlockWith: String = "}"

    override def startSingleLineComment: String = "//"

    override def startBlockComment: String = "/*"

    override def endBlockComment: String = "*/"

    override def preferedDisplayStyle: NamingStyle = CamelCase

    override def endLineWith: String = ";"
  }

  case class JavaScriptSeparation() extends SeparateStructures derives upickle.default.ReadWriter {
    override def startBlockWith: String = "{"
    override def endBlockWith: String = "}"
    override def startSingleLineComment: String = "//"
    override def startBlockComment: String = "/*"
    override def endBlockComment: String = "*/"
    override def preferedDisplayStyle: NamingStyle = SnakeCase
    override def endLineWith: String = ";"
  }

  case class PythonSeparation() extends SeparateStructures derives upickle.default.ReadWriter {

    override def startBlockWith: String = ":"

    override def endBlockWith: String = ""

    override def startSingleLineComment: String = "#"

    override def startBlockComment: String = "'''"

    override def endBlockComment: String = "'''"

    override def preferedDisplayStyle: NamingStyle = SnakeCase

    override def endLineWith: String = ""
  }

}
