package it.evadid.vm.conversion.printing

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, Java, ProgrammingLanguage, Python}
import it.evadid.core.util.CodeStringBuilderMutable
import it.evadid.vm.code.abstractions.{BeControlStructure, BeDefineStructure, BeExpression}
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeDependency}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.*
import it.evadid.vm.conversion.printing.GenericJavaLikeStringPrinter.{PythonSeparation, SeparateStructures}
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
    val inCurLanguage: String = GenericJavaLikeStringPrinter.identifier(entityName.getNameIn(humLang, sepLogic.preferedDisplayStyle), progLang)
  }

  private def typeName(dataType: BeDataType): String = dataType.formatTypeForDisplay.getInLanguage(progLang)

  private def isPython: Boolean = sepLogic.isInstanceOf[PythonSeparation]

  def forExpression(exprOp: Option[BeExpression]): String = exprOp.map(forExpression).getOrElse("")

  def forExpression(expr: BeExpression): String = expr.match {
    case cs: BeControlStructure => forControlStructure(cs)
    case ds: BeDefineStructure => forDefinition(ds)
    case other => forOther(other)
  }

  protected def forValue(expression: BeExpression): String =
    if sepLogic.endLineWith.nonEmpty then forExpression(expression).stripSuffix(sepLogic.endLineWith) else forExpression(expression)

  protected def forOperand(expression: BeExpression): String = {
    val rendered = forValue(expression)
    expression match {
      case call: BeFunctionCall if call.isOperator => s"($rendered)"
      case _ => rendered
    }
  }

  protected def entityName(name: BeEntityName): String = NameInfo(name).inCurLanguage
  protected def className(name: BeEntityName): String = typeName(BeDataType.ClassType(name))
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

  protected def defineClassLine(nameStr: String): String =
    s"class $nameStr ${sepLogic.startBlockWith}"

  protected def defineFunctionLine(nameStr: String, parStr: String, outputTypeStr: String): String

  protected def formatFunctionParameters(inputs: List[BeDefineVariable]): String =
    inputs.map(forExpression).map(_.replace("\n", "")).mkString("(", ", ", ")")

  /** The header of a repetition; `amount` is the printed count. */
  protected def repetitionLine(amount: String): String

  /** Whether a counting loop counts down. Snap's two-way `for` can only be decided for literal bounds;
    * with computed bounds it is printed counting up.
    */
  protected def countsDown(start: String, end: String, direction: BeFor.Direction): Boolean = direction match {
    case BeFor.Direction.Up => false
    case BeFor.Direction.Down => true
    case BeFor.Direction.Either => start.toIntOption.zip(end.toIntOption).exists(_ > _)
  }

  /** An inclusive counting loop in one direction. */
  protected def namedRangeLine(varName: String, start: String, end: String, down: Boolean): String =
    if down then s"for (int $varName = $start; $varName >= $end; $varName--) ${sepLogic.startBlockWith}"
    else s"for (int $varName = $start; $varName <= $end; $varName++) ${sepLogic.startBlockWith}"

  /** Printed when a function or block body would otherwise be empty. Python overrides this with `pass`. */
  protected def emptyBody: String = ""

  /** A block's body; an empty one is printed as [[emptyBody]] where the language needs a statement. */
  private def blockBody(body: BeExpression): String = {
    val rendered = forExpression(body)
    if rendered.trim.isEmpty && emptyBody.nonEmpty then emptyBody else rendered
  }

  /** A header line, the indented [[blockBody]] and the closing line. */
  private def indentedBlock(headerLine: String, body: BeExpression): String =
    CodeStringBuilderMutable()
      .appendNextLine(headerLine)
      .changeIntLevel(1)
      .appendAsLines(blockBody(body))
      .changeIntLevel(-1)
      .appendNextLine(sepLogic.endBlockWith)
      .toString

  protected def forStatement(expr: BeExpression): String = {
    forExpression(expr) + (expr match {
      case _: BeControlStructure | _: BeReturn | _: BeFunctionCall | _: BeDefineClass | _: BeDefineFunction |
           _: BeSingleLineComment | _: BeExpressionUnsupported | _: BeExpressionUnparsable => ""
      case _ => sepLogic.endLineWith
    })
  }

  def forOther(other: BeExpression): String = other.match {
    case source: BeExpressionUnparsable => if (skipUnparsable) "" else source.sourceFor(progLang)
    case BeSingleLineComment(commentStr) => s"${sepLogic.startSingleLineComment} ${commentStr.getInLanguage(humLang)}"
    case source: BeExpressionUnsupported => source.sourceFor(progLang)
    case BeCurrentInstance(_) => currentInstanceName
    case BeMemberAccess(receiver, member) => s"${forOperand(receiver)}.${entityName(member.name)}"
    case BeAssignMember(receiver, member, value) => s"${forOperand(receiver)}.${entityName(member.name)} = ${forOperand(value)}"
    case BeReturn(value) => s"return ${value.map(forValue).getOrElse("")}${sepLogic.endLineWith}"
    case BeStartProgram(startSequence) => forExpression(startSequence)
    // usage
    case BeAssignVariable(target, value) => {
      assignToDefinedVar(NameInfo(target.name).inCurLanguage, typeName(target.variableType), value)
    }
    case call: BeFunctionCall => {
      val funcDef = call.funcDef
      val parameterValueMap = call.parameterValueMap
      if (funcDef.functionTypeInfo.isNamed.isEmpty && !call.target.isInstanceOf[BeCallTarget.Constructor])
        throw UnsupportedOperationException("Anonymous function calls are not supported by this printer.")
      else {
        val callParInfo = funcDef.inputs.map(curIn =>
          assignToFunctionPar(NameInfo(curIn.name).inCurLanguage, typeName(curIn.variableType), parameterValueMap(curIn))
        ).mkString("(", ", ", ")")
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
    case dependency: BeDependency => dependency.definition(progLang).getOrElse("")
    case BeDefineClass(name, attributes, methods) => {
      CodeStringBuilderMutable()
        .appendNextLine(defineClassLine(className(name)))
        .changeIntLevel(1)
        .appendAllAsLines(attributes.map(forStatement))
        .appendNextLine("")
        .appendAllAsLines(methods.map(forExpression))
        .appendNextLine(sepLogic.endBlockWith)
        .toString
    }
    case function @ BeDefineFunction(inputs, outputs, body, functionTypeInfo, _) => {
      if (functionTypeInfo.isNamed.isEmpty && functionTypeInfo.funcType != BeDefineFunction.Constructor)
        throw UnsupportedOperationException("Anonymous function declarations are not supported by this printer.")
      else {
        val par = formatFunctionParameters(function)
        val outTypeStr = typeName(outputs.map(_.variableType).getOrElse(BeDataType.Unit))
        val renderedBody = forExpression(body)
        val bodyText = if renderedBody.trim.isEmpty then emptyBody else renderedBody
        CodeStringBuilderMutable()
          .appendNextLine(functionDeclaration(function, par, outTypeStr))
          .changeIntLevel(1)
          .appendAsLines(bodyText)
          .changeIntLevel(-1)
          .appendNextLine(sepLogic.endBlockWith)
          .toString
      }
    }
    case BeDefineVariable(name, variableType, initValueOp) =>
      defineVariableLine(NameInfo(name).inCurLanguage, typeName(variableType), initValueOp)
  }

  /** The condition of an `if` or `while`, as one line. */
  protected def conditionText(condition: BeSequence): String =
    forExpression(condition).replaceAll("\n", "").stripSuffix(sepLogic.endLineWith)

  def forControlStructure(cs: BeControlStructure): String = cs.match {
    case BeIfElse(condition, thenBody, elseBody) => {
      def header(keyword: String, condition: BeSequence): String = {
        val conditionString = conditionText(condition)
        if isPython then s"$keyword $conditionString${sepLogic.startBlockWith}"
        else s"$keyword ($conditionString) ${sepLogic.startBlockWith}"
      }
      val res = CodeStringBuilderMutable()
        .appendNextLine(header("if", condition))
        .changeIntLevel(1)
        .appendAsLines(blockBody(thenBody))
        .changeIntLevel(-1)
      // An else branch holding only another if prints as an elif / else-if chain.
      @scala.annotation.tailrec def branches(otherwise: BeSequence): Unit = otherwise.body match {
        case Seq(BeIfElse(nextCondition, nextThen, nextElse)) =>
          res.appendNextLine(if isPython then header("elif", nextCondition) else sepLogic.endBlockWith + " " + header("else if", nextCondition))
          res.changeIntLevel(1).appendAsLines(blockBody(nextThen)).changeIntLevel(-1)
          branches(nextElse)
        case Seq() => res.appendNextLine(sepLogic.endBlockWith)
        case _ =>
          res.appendNextLine(if isPython then s"else${sepLogic.startBlockWith}" else s"${sepLogic.endBlockWith} else ${sepLogic.startBlockWith}")
          res.changeIntLevel(1).appendAsLines(forExpression(otherwise)).changeIntLevel(-1).appendNextLine(sepLogic.endBlockWith)
      }
      branches(elseBody)
      res.toString
    }
    case BeRepeatNr(amount, body) =>
      indentedBlock(repetitionLine(forValue(amount)), body)
    case BeFor(variable, start, end, body, direction) => {
      val varName = NameInfo(variable.name).inCurLanguage
      val startStr = forExpression(start).replaceAll("\n", "").trim
      val endStr = forExpression(end).replaceAll("\n", "").trim
      indentedBlock(namedRangeLine(varName, startStr, endStr, countsDown(startStr, endStr, direction)), body)
    }
    case BeSequence(body, sequenceInfo) => {
      val rendered = CodeStringBuilderMutable()
        .appendAllAsLines(body.map(forStatement))
        .toString
      if (isPython && levelIsTopLevel(rendered)) rendered + "\n" else rendered
    }
    case BeWhile(condition, body) => {
      val conditionString = conditionText(condition)
      val formattedCondition = if isPython then s" $conditionString" else s" ($conditionString) "
      indentedBlock(s"while$formattedCondition${sepLogic.startBlockWith}", body)
    }
    case _ => ???
  }

  private def levelIsTopLevel(rendered: String): Boolean = rendered.nonEmpty && !rendered.startsWith(" ")

}

object GenericJavaLikeStringPrinter {
  /** Words a program cannot use as names, per target language. */
  private val reservedWords: Map[ProgrammingLanguage, Set[String]] = Map(
    Python -> Set("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
      "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
      "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"),
    Java -> Set("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
      "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements",
      "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public",
      "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
      "try", "void", "volatile", "while", "true", "false", "null", "_"))

  /** A name as the target language can spell it: a reserved word gets a trailing underscore (`new` → `new_`). */
  def identifier(name: String, language: ProgrammingLanguage): String =
    if reservedWords.get(language).exists(_.contains(name)) then name + "_" else name


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

  case class JavaScriptSeparation() extends SeparateStructures {
    override def startBlockWith: String = "{"
    override def endBlockWith: String = "}"
    override def startSingleLineComment: String = "//"
    override def startBlockComment: String = "/*"
    override def endBlockComment: String = "*/"
    override def preferedDisplayStyle: NamingStyle = SnakeCase
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
