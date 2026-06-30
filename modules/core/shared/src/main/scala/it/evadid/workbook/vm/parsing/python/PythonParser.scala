package it.evadid.workbook.vm.parsing.python

import it.evadid.workbook.vm.naming.BeEntityName
import ParsingUtils.keepExpression
import PythonClassParser.{ClassParseResult, ClassParserApi}
import PythonLexerLike.{ParsedLine, findBodyIndent, skipBlankLines, toParsedLines}
import PythonStatementParser.{BlockParseResult, NodeWithNext, StatementApi}
import PythonSymbolTable.{CurrentlyKnownStructures, ParseContext}
import it.evadid.workbook.vm.types.BeDataType.{AnyType, BeUnionAllowedTypes}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import it.evadid.workbook.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.workbook.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.workbook.vm.code.others.BeReturn
import it.evadid.workbook.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.workbook.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}

/**
 * Parses Python source code that has been normalized by [[PythonNormalizer]].
 */
class PythonParser(
                    normalizer: PythonNormalizer = PythonNormalizer.default,
                    config: PythonFrontendConfig = PythonFrontendConfig.default
                  ) {

  type KnownStructure = PythonSymbolTable.KnownStructure
  val KnownStructure: PythonSymbolTable.KnownStructure.type = PythonSymbolTable.KnownStructure

  def parsePython(source: String): BeSequence = parsePythonWithDetails(source).codeExpression

  def parsePythonWithDetails(
                              source: String,
                              initialKnownStructures: Seq[KnownStructure] = config.defaultKnownStructures
                            ): PythonParser.CodeParsingResult = {
    val normalized = normalizer.normalizePython(source)
    val initialStructures = CurrentlyKnownStructures.fromKnown(initialKnownStructures)
    if (normalized.trim.isEmpty) {
      PythonParser.CodeParsingResult(Nil, Nil, Nil, initialStructures, BeSequence.optionalBody(Nil))
    } else {
      val context = new ParseContext(initialStructures)
      val lines = toParsedLines(normalized)
      val blockResult = parseBlock(lines, 0, 0, context)
      val expression = BeSequence.optionalBody(blockResult.expressions.filter(keepExpression))
      PythonParser.CodeParsingResult(context.definedClasses, context.definedFunctions, context.definedVariables, context.currentStructures, expression)
    }
  }

  private def parseBlock(lines: Vector[ParsedLine], startIndex: Int, indent: Int, context: ParseContext): BlockParseResult = {
    val statementApi = StatementApi(
      parseBlock = parseBlock,
      parseClass = parseClass,
      parseFunction = parseFunction,
      parseWhile = parseWhile,
      parseIf = parseIf,
      parseReturn = parseReturn,
      parseExpression = parseExpression,
      inferType = inferType,
      mapType = mapType,
      isTryHeader = isTryHeader,
      collectTryExceptBlock = collectTryExceptBlock
    )
    PythonStatementParser.parseBlock(lines, startIndex, indent, context, statementApi)
  }

  private def parseClass(lines: Vector[ParsedLine], headerIndex: Int, indent: Int, name: String, basesSource: Option[String], context: ParseContext): ClassParseResult = {
    val classApi = ClassParserApi(
      parseBlock = parseBlock,
      parseWhile = parseWhile,
      parseIf = parseIf,
      parseReturn = parseReturn,
      parseExpression = parseExpression,
      parseParameters = parseParameters,
      inferType = inferType,
      mapType = mapType
    )
    PythonClassParser.parseClass(lines, headerIndex, indent, name, basesSource, context, classApi)
  }

  private def parseFunction(
                             lines: Vector[ParsedLine],
                             headerIndex: Int,
                             indent: Int,
                             name: String,
                             paramsSource: String,
                             returnSource: Option[String],
                             context: ParseContext
                           ): NodeWithNext = {
    context.pushScope()
    val parameterDefinitions = parseParameters(paramsSource).map { case (paramName, typeHint) =>
      context.defineVariable(paramName, mapType(typeHint))
    }

    val returnVariable = returnSource.map(_.trim).filter(_.nonEmpty).map(returnHint => BeDefineVariable(BeEntityName.fromUniversalNameInParts("return"), mapType(Some(returnHint))))

    val computedIndent = findBodyIndent(lines, headerIndex + 1, indent)

    val (bodyExpressions, nextIndex) = try {
      if (computedIndent <= indent) {
        (List(BeExpressionUnparsable(lines(headerIndex).content.trim, s"Missing body for function $name")), headerIndex + 1)
      } else {
        val block = parseBlock(lines, headerIndex + 1, computedIndent, context)
        (block.expressions, block.nextIndex)
      }
    } finally {
      context.popScope()
    }

    val body = BeSequence.optionalBody(bodyExpressions)
    val functionInfo = BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(name))
    val functionDef = BeDefineFunction(parameterDefinitions, returnVariable, body, functionInfo)
    context.registerFunction(name, functionDef)
    NodeWithNext(functionDef, nextIndex)
  }

  private def isTryHeader(text: String): Boolean = text == "try:"

  private def isTryCompanionHeader(text: String): Boolean = {
    val normalized = text.trim
    (normalized.startsWith("except") && normalized.endsWith(":")) || normalized == "finally:" || normalized == "else:"
  }

  private def collectTryExceptBlock(lines: Vector[ParsedLine], headerIndex: Int, indent: Int): (String, Int) = {
    val builder = new StringBuilder
    var index = headerIndex
    var continue = true
    while (index < lines.length && continue) {
      val line = lines(index)
      val trimmed = line.content.trim
      if (line.indent < indent) continue = false
      else if (line.indent == indent && index > headerIndex && !isTryCompanionHeader(trimmed)) continue = false
      else {
        if (builder.nonEmpty) builder.append('\n')
        builder.append(" " * line.indent)
        builder.append(line.content)
        index += 1
      }
    }
    (builder.toString(), index)
  }

  private val ElsePattern = """^else:$""".r

  private def parseWhile(lines: Vector[ParsedLine], headerIndex: Int, indent: Int, conditionSource: String, context: ParseContext): NodeWithNext = {
    val conditionExpr = parseExpression(conditionSource.trim, context)
    val computedIndent = findBodyIndent(lines, headerIndex + 1, indent)
    if (computedIndent <= indent) {
      NodeWithNext(BeExpressionUnparsable(lines(headerIndex).content.trim, "Missing body for while loop"), headerIndex + 1)
    } else {
      val bodyBlock = parseBlock(lines, headerIndex + 1, computedIndent, context)
      val conditionSequence = BeSequence.conditionalBody(List(conditionExpr))
      val bodySequence = BeSequence.optionalBody(bodyBlock.expressions)
      NodeWithNext(BeWhile(conditionSequence, bodySequence), bodyBlock.nextIndex)
    }
  }

  private def parseIf(lines: Vector[ParsedLine], headerIndex: Int, indent: Int, conditionSource: String, context: ParseContext): NodeWithNext = {
    val conditionExpr = parseExpression(conditionSource.trim, context)
    val computedIndent = findBodyIndent(lines, headerIndex + 1, indent)
    if (computedIndent <= indent) {
      NodeWithNext(BeExpressionUnparsable(lines(headerIndex).content.trim, "Missing body for if clause"), headerIndex + 1)
    } else {
      val thenBlock = parseBlock(lines, headerIndex + 1, computedIndent, context)
      val nextIndex = skipBlankLines(lines, thenBlock.nextIndex)
      if (nextIndex < lines.length && lines(nextIndex).indent == indent) {
        lines(nextIndex).content.trim match {
          case ElsePattern() =>
            val elseIndent = findBodyIndent(lines, nextIndex + 1, indent)
            if (elseIndent <= indent) {
              NodeWithNext(BeExpressionUnparsable(lines(nextIndex).content.trim, "Missing body for else clause"), nextIndex + 1)
            } else {
              val elseBlock = parseBlock(lines, nextIndex + 1, elseIndent, context)
              NodeWithNext(
                BeIfElse(BeSequence.conditionalBody(List(conditionExpr)), BeSequence.optionalBody(thenBlock.expressions), BeSequence.optionalBody(elseBlock.expressions)),
                elseBlock.nextIndex
              )
            }
          case other if other.startsWith("else") =>
            NodeWithNext(BeExpressionUnparsable(lines(nextIndex).content.trim, "Else statements must end with ':'"), nextIndex + 1)
          case _ =>
            NodeWithNext(BeIfElse(BeSequence.conditionalBody(List(conditionExpr)), BeSequence.optionalBody(thenBlock.expressions), BeSequence.optionalBody(Nil)), nextIndex)
        }
      } else {
        NodeWithNext(BeIfElse(BeSequence.conditionalBody(List(conditionExpr)), BeSequence.optionalBody(thenBlock.expressions), BeSequence.optionalBody(Nil)), nextIndex)
      }
    }
  }

  private def parseReturn(source: String, context: ParseContext): BeExpression = {
    val payload = source.stripPrefix("return").trim
    if (payload.isEmpty) BeReturn(None) else BeReturn(Some(parseExpression(payload, context)))
  }

  private val binaryPrecedence: List[List[String]] = List(
    List("or"), List("and"), List("is not", "is"), List("==", "!=", "<=", ">=", "<", ">"),
    List("|"), List("^"), List("&"), List("<<", ">>"), List("+", "-"), List("*", "/", "//", "%")
  )

  private val IdentifierPattern = """^[A-Za-z_][A-Za-z0-9_]*$""".r

  private def parseExpression(source: String, context: ParseContext): BeExpression = {
    val trimmed = source.trim
    if (trimmed.isEmpty) BeExpression.pass
    else {
      val unwrapped = ParsingUtils.unwrapRedundantParentheses(trimmed)
      val target = if (unwrapped.isEmpty) trimmed else unwrapped
      parseBinaryExpression(target, context)
        .orElse(parseUnaryExpression(target, context))
        .orElse(parseFunctionCall(target, context))
        .orElse(parseLiteralExpression(target, context))
        .getOrElse(BeExpressionUnsupported(trimmed))
    }
  }

  private def parseUnaryExpression(source: String, context: ParseContext): Option[BeExpression] = {
    val trimmed = source.trim
    val unaryOperators: List[String] = List("not", "+", "-", "~")
    unaryOperators.collectFirst {
      case operator if startsWithUnaryOperator(trimmed, operator) =>
        val operandSource = trimmed.substring(operator.length).trim
        Option.when(operandSource.nonEmpty) {
          val operandExpr = parseExpression(operandSource, context)
          val function = context.resolveOperator(operator, 1, List(operandExpr))
          BeFunctionCall(function, Map(function.inputs.head -> operandExpr))
        }
    }.flatten
  }

  private def startsWithUnaryOperator(source: String, operator: String): Boolean = {
    if (!source.startsWith(operator)) false
    else {
      val boundaryIndex = operator.length
      val requiresWordBoundary = operator.lastOption.exists(_.isLetterOrDigit)
      val isIdentifierChar: Char => Boolean = ch => ch.isLetterOrDigit || ch == '_'
      if (!requiresWordBoundary) true
      else if (boundaryIndex >= source.length) false
      else !isIdentifierChar(source.charAt(boundaryIndex))
    }
  }

  private def parseBinaryExpression(source: String, context: ParseContext): Option[BeExpression] = {
    binaryPrecedence.view.flatMap { operators =>
      ParsingUtils.splitTopLevelBinary(source, operators).map { case (left, operator, right) =>
        val leftExpr = parseExpression(left, context)
        val rightExpr = parseExpression(right, context)
        val function = context.resolveOperator(operator.trim, 2, List(leftExpr, rightExpr))
        BeFunctionCall(function, Map(function.inputs.head -> leftExpr, function.inputs(1) -> rightExpr))
      }
    }.headOption
  }

  private def parseFunctionCall(source: String, context: ParseContext): Option[BeExpression] = {
    ParsingUtils.findTopLevelCall(source).map { case (rawName, argsSource) =>
      val name = rawName.trim
      val arguments = ParsingUtils.splitTopLevelArguments(argsSource).map(_.trim).filter(_.nonEmpty).map(arg => parseExpression(arg, context))
      val function = context.ensureFunctionArity(name, context.resolveFunction(name, arguments.length), arguments.length)
      BeFunctionCall(function, function.inputs.zip(arguments).toMap)
    }
  }

  private def parseLiteralExpression(source: String, context: ParseContext): Option[BeExpression] = source match {
    case "None" => Some(BeUseValue(BeDataValueUnit(), None))
    case "True" | "False" => Some(BeUseValue(BeDataValueLiteral(source), None))
    case _ if isStringLiteral(source) => Some(BeUseValue(BeDataValueLiteral(source), None))
    case _ if isNumericLiteral(source) => Some(BeUseValue(BeDataValueLiteral(source), None))
    case IdentifierPattern() =>
      val variable = context.lookupVariable(source).getOrElse(context.assignVariable(source, AnyType))
      Some(BeUseValue(BeUseValueReference(variable), Some(variable)))
    case _ => None
  }

  private def isStringLiteral(value: String): Boolean =
    (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) || (value.startsWith("'") && value.endsWith("'") && value.length >= 2)

  private def isNumericLiteral(value: String): Boolean = value.replace("_", "").toDoubleOption.nonEmpty

  private def parseParameters(source: String): List[(String, Option[String])] = {
    if (source.trim.isEmpty) Nil
    else {
      ParsingUtils.splitTopLevelArguments(source).map { rawParam =>
        val cleaned = rawParam.trim
        if (cleaned.isEmpty) ("", None)
        else {
          val parts = cleaned.split(":", 2).map(_.trim)
          val name = parts.headOption.getOrElse("")
          val typeHint = if (parts.length > 1) Some(stripDefaultValue(parts(1))) else None
          (name, typeHint.filter(_.nonEmpty))
        }
      }.filter(_._1.nonEmpty)
    }
  }

  private def stripDefaultValue(typeHint: String): String = {
    val equalIndex = typeHint.indexOf('=')
    if (equalIndex >= 0) typeHint.substring(0, equalIndex).trim else typeHint.trim
  }

  private def inferType(expr: BeExpression): BeDataType = expr.staticInformationExpression.staticType match {
    case BeDataType.Error => AnyType
    case other => other
  }

  private def mapType(typeHint: Option[String]): BeDataType = typeHint match {
    case Some(raw) if raw.nonEmpty =>
      val mapped = raw.split("\\|").map(_.trim).filter(_.nonEmpty).flatMap(mapAtomicType)
      if (mapped.isEmpty) AnyType else if (mapped.length == 1) mapped.head else BeUnionAllowedTypes(mapped.toSet)
    case _ => AnyType
  }

  private def mapAtomicType(typeHint: String): Option[BeDataType] = typeHint.toLowerCase match {
    case "int" | "float" | "number" | "double" => Some(BeDataType.Numeric)
    case "bool" | "boolean" => Some(BeDataType.Boolean)
    case "str" | "string" => Some(BeDataType.String)
    case "date" | "datetime" => Some(BeDataType.Date)
    case "none" | "void" | "unit" => Some(BeDataType.Unit)
    case _ => None
  }

}

object PythonParser {
  final case class CodeParsingResult(
                                      definedClasses: List[BeDefineClass],
                                      definedFunctions: List[BeDefineFunction],
                                      definedVariables: List[BeDefineVariable],
                                      currentlyKnownStructures: CurrentlyKnownStructures,
                                      codeExpression: BeSequence
                                    )

  type KnownStructure = PythonSymbolTable.KnownStructure
  val KnownStructure: PythonSymbolTable.KnownStructure.type = PythonSymbolTable.KnownStructure

  private object PythonParserInstance extends PythonParser(PythonNormalizer.default, PythonFrontendConfig.default)

  def default: PythonParser = new PythonParser(PythonNormalizer.default, PythonFrontendConfig.default)

  def parsePython(source: String): BeSequence =
    PythonParserInstance.parsePython(source)

  def parsePythonWithDetails(
                              source: String,
                              initialKnownStructures: Seq[KnownStructure] = PythonFrontendConfig.default.defaultKnownStructures
                            ): CodeParsingResult =
    PythonParserInstance.parsePythonWithDetails(source, initialKnownStructures)
}
