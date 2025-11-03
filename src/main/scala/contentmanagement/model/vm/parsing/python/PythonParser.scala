package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.*
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.errors.*
import contentmanagement.model.vm.code.others.BeReturn
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.*
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole
import contentmanagement.model.vm.types.BeDataType.{AnyType, BeUnionAllowedTypes}
import contentmanagement.model.vm.types.BeScope
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

import scala.collection.mutable

object PythonParser {

  final case class CodeParsingResult(
      definedClasses: List[BeDefineClass],
      definedFunctions: List[BeDefineFunction],
      definedVariables: List[BeDefineVariable],
      codeExpression: BeExpression
  )

  def parsePython(source: String): BeExpression = parsePythonWithDetails(source).codeExpression

  def parsePythonWithDetails(source: String): CodeParsingResult = {
    val normalized = normalizeSource(source)
    if (normalized.trim.isEmpty) {
      CodeParsingResult(Nil, Nil, Nil, BeSequence.optionalBody(Nil))
    } else {
      val context = new ParseContext
      val lines = toParsedLines(normalized)
      val (expressions, _) = parseBlock(lines, 0, 0, context)
      val expression = BeSequence.optionalBody(expressions)
      CodeParsingResult(context.definedClasses, context.definedFunctions, context.definedVariables, expression)
    }
  }

  private case class ParsedLine(indent: Int, content: String)

  private val AssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*(.+)$""".r
  private val FunctionPattern = """^def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?:->\s*([^:]+))?:$""".r
  private val IdentifierPattern = """^[A-Za-z_][A-Za-z0-9_]*$""".r

  private def parseBlock(
      lines: Vector[ParsedLine],
      startIndex: Int,
      indent: Int,
      context: ParseContext
  ): (List[BeExpression], Int) = {
    val expressions = mutable.ListBuffer[BeExpression]()
    var index = startIndex
    while (index < lines.length) {
      val line = lines(index)
      if (line.indent < indent) {
        return (expressions.toList, index)
      }

      val trimmed = line.content.trim
      if (trimmed.isEmpty) {
        expressions += BeExpression.pass
        index += 1
      } else if (line.indent > indent) {
        val (nested, nextIndex) = parseBlock(lines, index, line.indent, context)
        expressions ++= nested
        index = nextIndex
      } else {
        trimmed match {
          case FunctionPattern(name, params, returnType) =>
            val (functionExpr, nextIndex) = parseFunction(lines, index, indent, name, params, Option(returnType), context)
            expressions += functionExpr
            index = nextIndex
          case _ if trimmed.startsWith("return") =>
            expressions += parseReturn(trimmed, context)
            index += 1
          case _ if trimmed == "pass" =>
            expressions += BeExpression.pass
            index += 1
          case AssignmentPattern(name, valueStr) =>
            val valueExpr = parseExpression(valueStr, context)
            val variable = context.assignVariable(name, inferType(valueExpr))
            expressions += BeAssignVariable(variable, valueExpr)
            index += 1
          case _ =>
            expressions += parseExpression(trimmed, context)
            index += 1
        }
      }
    }
    (expressions.toList, index)
  }

  private def parseFunction(
      lines: Vector[ParsedLine],
      headerIndex: Int,
      indent: Int,
      name: String,
      paramsSource: String,
      returnSource: Option[String],
      context: ParseContext
  ): (BeExpression, Int) = {
    context.pushScope()
    val parameterInfos = parseParameters(paramsSource)
    val parameterDefinitions = parameterInfos.map { case (paramName, typeHint) =>
      context.defineVariable(paramName, mapType(typeHint))
    }

    val returnVariable = returnSource.map(_.trim).filter(_.nonEmpty).map { returnHint =>
      BeDefineVariable(LanguageMap.universalMap("return"), mapType(Some(returnHint)))
    }

    val computedIndent = determineBodyIndent(lines, headerIndex + 1, indent)

    val (bodyExpressions, nextIndex) = try {
      if (computedIndent <= indent) {
        (List(BeExpressionUnparsable(lines(headerIndex).content.trim, s"Missing body for function $name")), headerIndex + 1)
      } else {
        val (blockExprs, afterBlock) = parseBlock(lines, headerIndex + 1, computedIndent, context)
        (blockExprs, afterBlock)
      }
    } finally {
      context.popScope()
    }

    val body = BeSequence.optionalBody(bodyExpressions)
    val functionInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
    val indentWidth = if (bodyExpressions.nonEmpty && computedIndent > indent) computedIndent else 4
    val functionDef = BeDefineFunction(parameterDefinitions, returnVariable, body, functionInfo, indentWidth)
    context.registerFunction(name, functionDef)
    (functionDef, nextIndex)
  }

  private def parseReturn(source: String, context: ParseContext): BeExpression = {
    val payload = source.stripPrefix("return").trim
    if (payload.isEmpty) BeReturn(None)
    else BeReturn(Some(parseExpression(payload, context)))
  }

  private val binaryPrecedence: List[List[String]] = List(
    List("==", "!=", "<=", ">=", "<", ">"),
    List("+", "-"),
    List("*", "/", "//", "%")
  )

  private def parseExpression(source: String, context: ParseContext): BeExpression = {
    val trimmed = source.trim
    if (trimmed.isEmpty) {
      BeExpression.pass
    } else {
      val unwrapped = if (ParsingUtils.isParenthesized(trimmed)) trimmed.substring(1, trimmed.length - 1).trim else trimmed
      val target = if (unwrapped.isEmpty) trimmed else unwrapped
      parseBinaryExpression(target, context)
        .orElse(parseFunctionCall(target, context))
        .orElse(parseLiteralExpression(target, context))
        .getOrElse(BeExpressionUnsupported(trimmed))
    }
  }

  private def parseBinaryExpression(source: String, context: ParseContext): Option[BeExpression] = {
    binaryPrecedence.view.flatMap { operators =>
      ParsingUtils.splitTopLevelBinary(source, operators).map { case (left, operator, right) =>
        val leftExpr = parseExpression(left, context)
        val rightExpr = parseExpression(right, context)
        val function = context.resolveOperator(operator.trim, 2)
        val parameterMap = Map(
          function.inputs.head -> leftExpr,
          function.inputs(1) -> rightExpr
        )
        OperatorFunctionCall(BeFunctionCall(function, parameterMap), operator.trim)
      }
    }.headOption
  }

  private def parseFunctionCall(source: String, context: ParseContext): Option[BeExpression] = {
    ParsingUtils.findTopLevelCall(source).map { case (rawName, argsSource) =>
      val name = rawName.trim
      val argumentStrings = ParsingUtils.splitTopLevelArguments(argsSource).map(_.trim).filter(_.nonEmpty)
      val arguments = argumentStrings.map(arg => parseExpression(arg, context))
      val function = context.resolveFunction(name, arguments.length)
      val alignedFunction = context.ensureFunctionArity(name, function, arguments.length)
      val parameterMap = alignedFunction.inputs.zip(arguments).toMap
      BeFunctionCall(alignedFunction, parameterMap)
    }
  }

  private def parseLiteralExpression(source: String, context: ParseContext): Option[BeExpression] = {
    source match {
      case "None" => Some(BeUseValue(BeDataValueUnit(), None))
      case "True" | "False" => Some(BeUseValue(BeDataValueLiteral(source), None))
      case _ if isStringLiteral(source) => Some(BeUseValue(BeDataValueLiteral(source), None))
      case _ if isNumericLiteral(source) => Some(BeUseValue(BeDataValueLiteral(source), None))
      case IdentifierPattern() =>
        context.lookupVariable(source).getOrElse(context.assignVariable(source, AnyType))
        Some(BeUseValue(BeDataValueLiteral(source), None))
      case _ => None
    }
  }

  private def isStringLiteral(value: String): Boolean = {
    (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) ||
    (value.startsWith("'") && value.endsWith("'") && value.length >= 2)
  }

  private def isNumericLiteral(value: String): Boolean = {
    val cleaned = value.replace("_", "")
    cleaned.toDoubleOption.nonEmpty
  }

  private def parseParameters(source: String): List[(String, Option[String])] = {
    if (source.trim.isEmpty) Nil
    else {
      ParsingUtils.splitTopLevelArguments(source).map { rawParam =>
        val cleaned = rawParam.trim
        if (cleaned.isEmpty) ("", None)
        else {
          val parts = cleaned.split(":", 2).map(_.trim)
          val name = parts.headOption.getOrElse("")
          val typeHint = if (parts.length > 1) Some(parts(1)).map(stripDefaultValue) else None
          (name, typeHint.filter(_.nonEmpty))
        }
      }.filter(_._1.nonEmpty)
    }
  }

  private def stripDefaultValue(typeHint: String): String = {
    val equalIndex = typeHint.indexOf('=')
    if (equalIndex >= 0) typeHint.substring(0, equalIndex).trim else typeHint.trim
  }

  private def determineBodyIndent(lines: Vector[ParsedLine], startIndex: Int, parentIndent: Int): Int = {
    var index = startIndex
    while (index < lines.length) {
      val line = lines(index)
      if (line.content.trim.nonEmpty) {
        return line.indent
      }
      index += 1
    }
    parentIndent + 4
  }

  private def inferType(expr: BeExpression): BeDataType = expr.canEvaluateTo

  private def mapType(typeHint: Option[String]): BeDataType = {
    typeHint match {
      case Some(raw) if raw.nonEmpty =>
        val normalized = raw.split("\\|").map(_.trim).filter(_.nonEmpty)
        val mapped = normalized.flatMap(mapAtomicType)
        if (mapped.isEmpty) AnyType
        else if (mapped.length == 1) mapped.head
        else BeUnionAllowedTypes(mapped.toSet)
      case _ => AnyType
    }
  }

  private def mapAtomicType(typeHint: String): Option[BeDataType] = typeHint.toLowerCase match {
    case "int" | "float" | "number" | "double" => Some(BeDataType.Numeric)
    case "bool" | "boolean" => Some(BeDataType.Boolean)
    case "str" | "string" => Some(BeDataType.String)
    case "date" | "datetime" => Some(BeDataType.Date)
    case "none" | "void" | "unit" => Some(BeDataType.Unit)
    case _ => None
  }

  private def normalizeSource(source: String): String =
    source.replace("\r\n", "\n").replace('\r', '\n')

  private def toParsedLines(source: String): Vector[ParsedLine] = {
    val lines = source.split("\n", -1)
    lines.toVector.map { rawLine =>
      val indent = rawLine.takeWhile(_ == ' ').length
      val content = rawLine.drop(indent)
      ParsedLine(indent, content)
    }
  }

  private class ParseContext {
    private var scopes: List[mutable.LinkedHashMap[String, BeDefineVariable]] = List(mutable.LinkedHashMap[String, BeDefineVariable]())
    private val variablesBuffer = mutable.ListBuffer[BeDefineVariable]()
    private val functionsBuffer = mutable.ListBuffer[BeDefineFunction]()
    private val classesBuffer = mutable.ListBuffer[BeDefineClass]()
    private val functionsByName = mutable.LinkedHashMap[String, BeDefineFunction]()
    private val operatorFunctions = mutable.LinkedHashMap[String, BeDefineFunction]()

    def pushScope(): Unit = {
      scopes = mutable.LinkedHashMap[String, BeDefineVariable]() :: scopes
    }

    def popScope(): Unit = {
      scopes = scopes.tail
    }

    def assignVariable(name: String, dataType: BeDataType): BeDefineVariable = {
      lookupVariable(name).getOrElse {
        val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType)
        currentScope.update(name, variable)
        registerVariable(variable)
        variable
      }
    }

    def defineVariable(name: String, dataType: BeDataType): BeDefineVariable = {
      val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType)
      currentScope.update(name, variable)
      registerVariable(variable)
      variable
    }

    def lookupVariable(name: String): Option[BeDefineVariable] = scopes.collectFirst { case scope if scope.contains(name) => scope(name) }

    private def currentScope: mutable.LinkedHashMap[String, BeDefineVariable] = scopes.head

    def registerVariable(variable: BeDefineVariable): Unit = {
      if (!variablesBuffer.exists(_ eq variable)) {
        variablesBuffer += variable
      }
    }

    def registerFunction(name: String, function: BeDefineFunction): Unit = {
      functionsByName.update(name, function)
      if (!functionsBuffer.exists(_ eq function)) {
        functionsBuffer += function
      }
    }

    def resolveFunction(name: String, arity: Int): BeDefineFunction =
      functionsByName.getOrElse(name, {
        val params = (0 until arity).map(index => BeDefineVariable(LanguageMap.universalMap(s"arg$index"), AnyType)).toList
        val placeholder = BeDefineFunction(params, None, BeSequence.optionalBody(Nil), BeDefineFunction.functionInfo(LanguageMap.universalMap(name)))
        functionsByName.update(name, placeholder)
        placeholder
      })

    def ensureFunctionArity(name: String, function: BeDefineFunction, arity: Int): BeDefineFunction = {
      if (arity <= function.inputs.length) function
      else {
        val additional = (function.inputs.length until arity).map { index =>
          BeDefineVariable(LanguageMap.universalMap(s"arg$index"), AnyType)
        }.toList
        val updated = function.copy(inputs = function.inputs ++ additional)
        functionsByName.update(name, updated)
        val idx = functionsBuffer.indexWhere(_ eq function)
        if (idx >= 0) functionsBuffer.update(idx, updated)
        updated
      }
    }

    def resolveOperator(symbol: String, arity: Int): BeDefineFunction = {
      operatorFunctions.getOrElse(symbol, {
        val params = (0 until arity).map { index =>
          val paramName = index match {
            case 0 => "left"
            case 1 => "right"
            case other => s"arg$other"
          }
          BeDefineVariable(LanguageMap.universalMap(paramName), AnyType)
        }.toList
        val outputVar = Some(BeDefineVariable(LanguageMap.universalMap("result"), AnyType))
        val function = BeDefineFunction(params, outputVar, BeExpression.pass, BeDefineFunction.operatorInfo(symbol, 1))
        operatorFunctions.update(symbol, function)
        registerFunction(symbol, function)
        function
      })
    }

    def definedClasses: List[BeDefineClass] = classesBuffer.toList

    def definedFunctions: List[BeDefineFunction] = functionsBuffer.toList

    def definedVariables: List[BeDefineVariable] = variablesBuffer.toList
  }

  private case class OperatorFunctionCall(call: BeFunctionCall, symbol: String) extends BeExpression {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val argumentStrings = call.funcDef.inputs.flatMap(call.parameterValueMap.get).map(_.getInLanguage(programmingLanguage, humanLanguage).trim)
      argumentStrings match {
        case Nil => symbol
        case head :: tail => tail.foldLeft(head) { (acc, cur) => s"$acc $symbol $cur" }
      }
    }

    override def hasThisExpressionSideEffects: Boolean = call.hasThisExpressionSideEffects

    override def getSyntaxErrorsOfThisStructure: Seq[BeInfo] = call.getSyntaxErrorsOfThisStructure

    override def canEvaluateTo: BeDataType = call.canEvaluateTo

    override def createBlock(): BeBlock = call.createBlock()

    override def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] =
      call.getChildren(withExtensions, parentScope)

    override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression =
      call.withReplacedChildren(newChildren) match {
        case updated: BeFunctionCall => copy(call = updated)
        case other => other
      }
  }
}
