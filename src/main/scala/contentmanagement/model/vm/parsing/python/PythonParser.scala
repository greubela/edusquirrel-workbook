package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.*
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.errors.*
import contentmanagement.model.vm.code.others.BeReturn
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.usage.*
import contentmanagement.model.vm.parsing.python.ParsingUtils.keepExpression
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole
import contentmanagement.model.vm.types.BeDataType.{AnyType, BeUnionAllowedTypes}
import contentmanagement.model.vm.types.BeScope
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

import scala.collection.mutable

/**
  * Parses Python source code that has been normalized by [[PythonNormalizer]].
  *
  * The normalizer rewrites `elif` chains into nested `if`/`else` blocks and expands
  * augmented assignments into plain assignments before the parser runs. As a result,
  * this parser only needs to handle base `if`/`else` constructs and simple `=`
  * assignments while still covering the semantics of the original student code.
  */
object PythonParser {

  private val normalizer = new PythonNormalizer()

  sealed trait KnownStructure {
    def name: String
  }

  object KnownStructure {
    final case class Variable(name: String, variable: BeDefineVariable) extends KnownStructure
    final case class Function(name: String, function: BeDefineFunction) extends KnownStructure
    final case class Operator(name: String, function: BeDefineFunction) extends KnownStructure
    final case class Class(name: String, clazz: BeDefineClass) extends KnownStructure
  }

  private val DefaultKnownStructures: Seq[KnownStructure] =
    DefaultDefinitions.operatorDefinitionsWithSymbols.map { case (symbol, function) =>
      KnownStructure.Operator(symbol, function)
    } ++ DefaultDefinitions.builtinFunctionDefinitions.map { case (name, function) =>
      KnownStructure.Function(name, function)
    }

  final case class CurrentlyKnownStructures(
      variables: Map[String, BeDefineVariable],
      functions: Map[String, BeDefineFunction],
      operators: Map[(String, Int), List[BeDefineFunction]],
      classes: Map[String, BeDefineClass]
  ) {
    def addVariable(name: String, variable: BeDefineVariable): CurrentlyKnownStructures =
      copy(variables = variables.updated(name, variable))

    def addFunction(name: String, function: BeDefineFunction): CurrentlyKnownStructures =
      copy(functions = functions.updated(name, function))

    def addOperator(name: String, function: BeDefineFunction): CurrentlyKnownStructures = {
      val key = name -> function.inputs.length
      val existing = operators.getOrElse(key, List.empty)
      val updatedList =
        if (existing.exists(_ eq function)) existing.map(cur => if (cur eq function) function else cur)
        else existing :+ function
      copy(
        functions = functions.updated(name, function),
        operators = operators.updated(key, updatedList)
      )
    }

    def addClass(name: String, clazz: BeDefineClass): CurrentlyKnownStructures =
      copy(classes = classes.updated(name, clazz))

    def +(structure: KnownStructure): CurrentlyKnownStructures = structure match {
      case KnownStructure.Variable(name, variable) => addVariable(name, variable)
      case KnownStructure.Function(name, function) => addFunction(name, function)
      case KnownStructure.Operator(name, function) => addOperator(name, function)
      case KnownStructure.Class(name, clazz) => addClass(name, clazz)
    }
  }

  object CurrentlyKnownStructures {
    val empty: CurrentlyKnownStructures =
      CurrentlyKnownStructures(
        Map.empty,
        Map.empty,
        Map.empty[(String, Int), List[BeDefineFunction]],
        Map.empty
      )

    def fromKnown(structures: Seq[KnownStructure]): CurrentlyKnownStructures =
      structures.foldLeft(empty)(_ + _)
  }

  final case class CodeParsingResult(
      definedClasses: List[BeDefineClass],
      definedFunctions: List[BeDefineFunction],
      definedVariables: List[BeDefineVariable],
      currentlyKnownStructures: CurrentlyKnownStructures,
      codeExpression: BeSequence
  )

  def parsePython(source: String): BeSequence = parsePythonWithDetails(source).codeExpression

  def parsePythonWithDetails(
      source: String,
      initialKnownStructures: Seq[KnownStructure] = DefaultKnownStructures
  ): CodeParsingResult = {
    val normalized = normalizer.normalizePython(source)
    val initialStructures = CurrentlyKnownStructures.fromKnown(initialKnownStructures)
    if (normalized.trim.isEmpty) {
      CodeParsingResult(Nil, Nil, Nil, initialStructures, BeSequence.optionalBody(Nil))
    } else {
      val context = new ParseContext(initialStructures)
      val lines = toParsedLines(normalized)
      val (expressions, _) = parseBlock(lines, 0, 0, context)
      val expressionsCleaned = expressions.filter(keepExpression)
      val expression = BeSequence.optionalBody(expressionsCleaned)
      CodeParsingResult(
        context.definedClasses,
        context.definedFunctions,
        context.definedVariables,
        context.currentStructures,
        expression
      )
    }
  }

  private case class ParsedLine(indent: Int, content: String)

  private val AssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*(.+)$""".r
  private val AnnotationAssignmentPattern =
    """^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)\s*=\s*(.+)$""".r
  private val ClassPattern = """^class\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*\(([^)]*)\))?:$""".r
  private val FunctionPattern = """^def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?:->\s*([^:]+))?:$""".r
  private val WhilePattern = """^while\s+(.+):$""".r
  private val IfPattern = """^if\s+(.+):$""".r
  private val ElsePattern = """^else:$""".r
  private val IdentifierPattern = """^[A-Za-z_][A-Za-z0-9_]*$""".r
  private val ClassAttributeAnnotationAssignmentPattern =
    """^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)\s*=\s*(.+)$""".r
  private val ClassAttributeAnnotationPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.+)$""".r
  private val SelfAttributeAnnotationAssignmentPattern =
    """^(self|cls)\.([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)\s*=\s*(.+)$""".r
  private val SelfAttributeAnnotationPattern =
    """^(self|cls)\.([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.+)$""".r
  private val SelfAttributeAssignmentPattern =
    """^(self|cls)\.([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*(.+)$""".r

  private def splitInlineComment(line: String): (String, Option[String]) = {
    var index = 0
    var commentIndex = -1
    var stringDelimiter: Option[String] = None
    val length = line.length
    while (index < length && commentIndex == -1) {
      stringDelimiter match {
        case Some(delimiter) if delimiter.length == 1 =>
          val current = line.charAt(index)
          if (current == '\\') {
            index += 2
          } else if (current == delimiter.head) {
            stringDelimiter = None
            index += 1
          } else {
            index += 1
          }
        case Some(delimiter) =>
          if (line.startsWith(delimiter, index)) {
            stringDelimiter = None
            index += delimiter.length
          } else {
            index += 1
          }
        case None =>
          if (line.startsWith("\"\"\"", index)) {
            stringDelimiter = Some("\"\"\"")
            index += 3
          } else if (line.startsWith("'''", index)) {
            stringDelimiter = Some("'''")
            index += 3
          } else {
            val current = line.charAt(index)
            current match {
              case '\\' => index += 2
              case '\"' =>
                stringDelimiter = Some("\"")
                index += 1
              case '\'' =>
                stringDelimiter = Some("'")
                index += 1
              case '#' =>
                commentIndex = index
              case _ =>
                index += 1
            }
          }
      }
    }
    if (commentIndex >= 0) {
      val codePart = line.substring(0, commentIndex)
      val commentText = line.substring(commentIndex + 1).trim
      (codePart, if (commentText.nonEmpty) Some(commentText) else Some(""))
    } else {
      (line, None)
    }
  }

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

      val (codePortion, inlineComment) = splitInlineComment(line.content)
      val trimmed = codePortion.trim
      if (trimmed.isEmpty) {
        inlineComment match {
          case Some(commentText) =>
            expressions += BeSingleLineComment(LanguageMap.universalMap(commentText))
          case None =>
            expressions += BeExpression.pass
        }
        index += 1
      } else if (line.indent > indent) {
        val (nested, nextIndex) = parseBlock(lines, index, line.indent, context)
        expressions ++= nested
        index = nextIndex
      } else {
        trimmed match {
          case AnnotationAssignmentPattern(name, typeHint, valueStr) =>
            val variable = context.defineVariable(name, mapType(Some(typeHint.trim)), hasExplicitTypeHint = true)
            val valueExpr = parseExpression(valueStr, context)
            expressions += BeAssignVariable(variable, valueExpr)
            index += 1
          case ClassPattern(name, bases) =>
            val (classExpr, nextIndex) = parseClass(lines, index, indent, name, Option(bases), context)
            expressions += classExpr
            index = nextIndex
          case FunctionPattern(name, params, returnType) =>
            val (functionExpr, nextIndex) = parseFunction(lines, index, indent, name, params, Option(returnType), context)
            expressions += functionExpr
            index = nextIndex
          case WhilePattern(conditionSource) =>
            val (whileExpr, nextIndex) = parseWhile(lines, index, indent, conditionSource, context)
            expressions += whileExpr
            index = nextIndex
          case IfPattern(conditionSource) =>
            val (ifExpr, nextIndex) = parseIf(lines, index, indent, conditionSource, context)
            expressions += ifExpr
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
          case _ if trimmed.startsWith("while") =>
            expressions += BeExpressionUnparsable(trimmed, "While statements must end with ':'")
            index += 1
          case _ if trimmed.startsWith("if") =>
            expressions += BeExpressionUnparsable(trimmed, "If statements must end with ':'")
            index += 1
          case _ =>
            expressions += parseExpression(trimmed, context)
            index += 1
        }
        inlineComment.foreach { commentText =>
          expressions += BeSingleLineComment(LanguageMap.universalMap(commentText))
        }
      }
    }
    (expressions.toList, index)
  }

  private case class AttributeRecord(name: String, variable: BeDefineVariable)

  private case class ParsedMethod(name: String, template: BeDefineFunction, attributes: List[AttributeRecord], nextIndex: Int)

  private def parseClass(
      lines: Vector[ParsedLine],
      headerIndex: Int,
      indent: Int,
      name: String,
      basesSource: Option[String],
      context: ParseContext
  ): (BeExpression, Int) = {
    val bodyIndent = determineBodyIndent(lines, headerIndex + 1, indent)
    basesSource.foreach(_ => ())
    if (bodyIndent <= indent) {
      (
        BeExpressionUnparsable(lines(headerIndex).content.trim, s"Missing body for class $name"),
        headerIndex + 1
      )
    } else {
      val attributesBuffer = mutable.LinkedHashMap[String, BeDefineVariable]()
      val methodsBuffer = mutable.ListBuffer[ParsedMethod]()
      val ignoredBodyExpressions = mutable.ListBuffer[BeExpression]()
      var index = headerIndex + 1
      var continue = true
      while (index < lines.length && continue) {
        val line = lines(index)
        if (line.indent < bodyIndent) {
          continue = false
        } else if (line.indent > bodyIndent) {
          val isolatedContext = new ParseContext(context.snapshotStructures)
          val (nested, nextIndex) = parseBlock(lines, index, line.indent, isolatedContext)
          ignoredBodyExpressions ++= nested
          index = nextIndex
        } else {
          val (codePortion, inlineComment) = splitInlineComment(line.content)
          val trimmed = codePortion.trim
          if (trimmed.isEmpty) {
            inlineComment.foreach { commentText =>
              ignoredBodyExpressions += BeSingleLineComment(LanguageMap.universalMap(commentText))
            }
            index += 1
          } else {
            trimmed match {
              case FunctionPattern(methodName, paramsSource, returnSource) =>
                val methodResult = parseMethod(lines, index, bodyIndent, methodName, paramsSource, Option(returnSource), context)
                methodsBuffer += methodResult
                methodResult.attributes.foreach { attributeRecord =>
                  attributesBuffer.update(attributeRecord.name, attributeRecord.variable)
                }
                index = methodResult.nextIndex
              case ClassAttributeAnnotationAssignmentPattern(attributeName, typeHint, valueSource) =>
                recordAttribute(attributesBuffer, attributeName, Some(typeHint), Some(valueSource), context)
                index += 1
              case ClassAttributeAnnotationPattern(attributeName, typeSource) if !typeSource.contains("=") =>
                recordAttribute(attributesBuffer, attributeName, Some(typeSource), None, context)
                index += 1
              case AssignmentPattern(attributeName, valueSource) =>
                recordAttribute(attributesBuffer, attributeName, None, Some(valueSource), context)
                index += 1
              case WhilePattern(conditionSource) =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                val (whileExpr, nextIndex) = parseWhile(lines, index, bodyIndent, conditionSource, isolatedContext)
                ignoredBodyExpressions += whileExpr
                index = nextIndex
              case IfPattern(conditionSource) =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                val (ifExpr, nextIndex) = parseIf(lines, index, bodyIndent, conditionSource, isolatedContext)
                ignoredBodyExpressions += ifExpr
                index = nextIndex
              case _ if trimmed.startsWith("return") =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                ignoredBodyExpressions += parseReturn(trimmed, isolatedContext)
                index += 1
              case _ if trimmed == "pass" =>
                ignoredBodyExpressions += BeExpression.pass
                index += 1
              case _ =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                ignoredBodyExpressions += parseExpression(trimmed, isolatedContext)
                index += 1
            }
            inlineComment.foreach { commentText =>
              ignoredBodyExpressions += BeSingleLineComment(LanguageMap.universalMap(commentText))
            }
          }
        }
      }

      val attributes = attributesBuffer.values.toList
      val parsedMethods = methodsBuffer.toList

      val classNameMap = LanguageMap.universalMap[HumanLanguage](name)
      val classPlaceholder = BeDefineClass(classNameMap, attributes, Nil)
      val methodInstances = parsedMethods.map { methodResult =>
        methodResult.template.copy(
          functionTypeInfo = BeDefineFunction.BeFunctionTypeInfo(
            isMethodInClass = Some(classPlaceholder),
            isNamed = Some(LanguageMap.universalMap[HumanLanguage](methodResult.name)),
            funcType = BeDefineFunction.Method()
          )
        )
      }
      // TODO: Consider how to incorporate ignoredBodyExpressions into BeDefineClass without storing them directly.
      val classExpr = classPlaceholder.copy(methods = methodInstances)
      context.registerClass(name, classExpr)
      (classExpr, index)
    }
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
      val hasExplicitType = typeHint.exists(_.nonEmpty)
      context.defineVariable(paramName, mapType(typeHint), hasExplicitType)
    }

    val returnVariable = returnSource.map(_.trim).filter(_.nonEmpty).map { returnHint =>
      BeDefineVariable(LanguageMap.universalMap("return"), mapType(Some(returnHint)), hasExplicitTypeHint = true)
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
    val indentWidth =
      if (bodyExpressions.nonEmpty && computedIndent > indent) computedIndent - indent else 4
    val functionDef = BeDefineFunction(parameterDefinitions, returnVariable, body, functionInfo, indentWidth)
    context.registerFunction(name, functionDef)
    (functionDef, nextIndex)
  }

  private def parseMethod(
      lines: Vector[ParsedLine],
      headerIndex: Int,
      indent: Int,
      name: String,
      paramsSource: String,
      returnSource: Option[String],
      context: ParseContext
  ): ParsedMethod = {
    val methodContext = new ParseContext(context.snapshotStructures)
    methodContext.pushScope()
    val parameterInfos = parseParameters(paramsSource)
    val parameterDefinitions = parameterInfos.map { case (paramName, typeHint) =>
      val hasExplicitType = typeHint.exists(_.nonEmpty)
      methodContext.defineVariable(paramName, mapType(typeHint), hasExplicitType)
    }

    val returnVariable = returnSource.map(_.trim).filter(_.nonEmpty).map { returnHint =>
      BeDefineVariable(LanguageMap.universalMap("return"), mapType(Some(returnHint)), hasExplicitTypeHint = true)
    }

    val computedIndent = determineBodyIndent(lines, headerIndex + 1, indent)

    val (bodyExpressions, nextIndex, discoveredAttributes) =
      if (computedIndent <= indent) {
        val unparsable = BeExpressionUnparsable(lines(headerIndex).content.trim, s"Missing body for method $name")
        (List(unparsable), headerIndex + 1, List.empty[AttributeRecord])
      } else {
        val (blockExpressions, afterBlock) = parseBlock(lines, headerIndex + 1, computedIndent, methodContext)
        val methodAttributes =
          if (name == "__init__" || name == "__new__")
            collectMethodAttributes(lines, headerIndex + 1, afterBlock, computedIndent, methodContext)
          else List.empty[AttributeRecord]
        (blockExpressions, afterBlock, methodAttributes)
      }

    methodContext.popScope()

    val body = BeSequence.optionalBody(bodyExpressions)
    val functionInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
    val indentWidth =
      if (bodyExpressions.nonEmpty && computedIndent > indent) computedIndent - indent else 4
    val template = BeDefineFunction(parameterDefinitions, returnVariable, body, functionInfo, indentWidth)
    ParsedMethod(name, template, discoveredAttributes, nextIndex)
  }

  private def collectMethodAttributes(
      lines: Vector[ParsedLine],
      startIndex: Int,
      endIndex: Int,
      bodyIndent: Int,
      context: ParseContext
  ): List[AttributeRecord] = {
    val attributes = mutable.LinkedHashMap[String, BeDefineVariable]()
    var index = startIndex
    while (index < endIndex) {
      val line = lines(index)
      if (line.indent == bodyIndent) {
        val (codePortion, _) = splitInlineComment(line.content)
        val trimmed = codePortion.trim
        trimmed match {
          case SelfAttributeAnnotationAssignmentPattern(_, attributeName, typeHint, valueSource) =>
            recordAttribute(attributes, attributeName, Some(typeHint), Some(valueSource), context)
          case SelfAttributeAnnotationPattern(_, attributeName, typeSource) if !typeSource.contains("=") =>
            recordAttribute(attributes, attributeName, Some(typeSource), None, context)
          case SelfAttributeAssignmentPattern(_, attributeName, valueSource) =>
            recordAttribute(attributes, attributeName, None, Some(valueSource), context)
          case _ =>
        }
      }
      index += 1
    }
    attributes.iterator.map { case (name, variable) => AttributeRecord(name, variable) }.toList
  }

  private def recordAttribute(
      buffer: mutable.LinkedHashMap[String, BeDefineVariable],
      attributeName: String,
      explicitType: Option[String],
      valueSource: Option[String],
      context: ParseContext
  ): Unit = {
    val attribute = createAttributeDefinition(attributeName, explicitType, valueSource, context)
    buffer.update(attributeName, attribute)
  }

  private def createAttributeDefinition(
      attributeName: String,
      explicitType: Option[String],
      valueSource: Option[String],
      context: ParseContext
  ): BeDefineVariable = {
    val dataType = explicitType.map(typeHint => mapType(Some(typeHint.trim))).getOrElse {
      valueSource.map(_.trim).filter(_.nonEmpty).map { valueText =>
        val isolated = new ParseContext(context.snapshotStructures)
        inferType(parseExpression(valueText, isolated))
      }.getOrElse(AnyType)
    }
    val hasExplicitType = explicitType.exists(_.trim.nonEmpty)
    BeDefineVariable(LanguageMap.universalMap(attributeName), dataType, hasExplicitType)
  }

  private def parseWhile(
      lines: Vector[ParsedLine],
      headerIndex: Int,
      indent: Int,
      conditionSource: String,
      context: ParseContext
  ): (BeExpression, Int) = {
    val conditionExpr = parseExpression(conditionSource.trim, context)
    val computedIndent = determineBodyIndent(lines, headerIndex + 1, indent)
    if (computedIndent <= indent) {
      (BeExpressionUnparsable(lines(headerIndex).content.trim, "Missing body for while loop"), headerIndex + 1)
    } else {
      val (bodyExpressions, nextIndex) = parseBlock(lines, headerIndex + 1, computedIndent, context)
      val conditionSequence = BeSequence.conditionalBody(List(conditionExpr))
      val bodySequence = BeSequence.optionalBody(bodyExpressions)
      (BeWhile(conditionSequence, bodySequence), nextIndex)
    }
  }

  private def parseIf(
      lines: Vector[ParsedLine],
      headerIndex: Int,
      indent: Int,
      conditionSource: String,
      context: ParseContext
  ): (BeExpression, Int) = {
    val conditionExpr = parseExpression(conditionSource.trim, context)
    val computedIndent = determineBodyIndent(lines, headerIndex + 1, indent)
    if (computedIndent <= indent) {
      (BeExpressionUnparsable(lines(headerIndex).content.trim, "Missing body for if clause"), headerIndex + 1)
    } else {
      val (thenBodyExpressions, afterThen) = parseBlock(lines, headerIndex + 1, computedIndent, context)
      val nextIndex = skipEmptyLines(lines, afterThen)
      if (nextIndex < lines.length && lines(nextIndex).indent == indent) {
        lines(nextIndex).content.trim match {
          case ElsePattern() =>
            val elseIndent = determineBodyIndent(lines, nextIndex + 1, indent)
            if (elseIndent <= indent) {
              (BeExpressionUnparsable(lines(nextIndex).content.trim, "Missing body for else clause"), nextIndex + 1)
            } else {
              val (elseExpressions, afterElse) = parseBlock(lines, nextIndex + 1, elseIndent, context)
              val conditionSequence = BeSequence.conditionalBody(List(conditionExpr))
              val thenSequence = BeSequence.optionalBody(thenBodyExpressions)
              val elseSequence = BeSequence.optionalBody(elseExpressions)
              (BeIfElse(conditionSequence, thenSequence, elseSequence), afterElse)
            }
          case other if other.startsWith("else") =>
            (BeExpressionUnparsable(lines(nextIndex).content.trim, "Else statements must end with ':'"), nextIndex + 1)
          case _ =>
            val conditionSequence = BeSequence.conditionalBody(List(conditionExpr))
            val thenSequence = BeSequence.optionalBody(thenBodyExpressions)
            val elseSequence = BeSequence.optionalBody(Nil)
            (BeIfElse(conditionSequence, thenSequence, elseSequence), nextIndex)
        }
      } else {
        val conditionSequence = BeSequence.conditionalBody(List(conditionExpr))
        val thenSequence = BeSequence.optionalBody(thenBodyExpressions)
        val elseSequence = BeSequence.optionalBody(Nil)
        (BeIfElse(conditionSequence, thenSequence, elseSequence), nextIndex)
      }
    }
  }

  private def parseReturn(source: String, context: ParseContext): BeExpression = {
    val payload = source.stripPrefix("return").trim
    if (payload.isEmpty) BeReturn(None)
    else BeReturn(Some(parseExpression(payload, context)))
  }

  private val binaryPrecedence: List[List[String]] = List(
    List("or"),
    List("and"),
    List("is not", "is"),
    List("==", "!=", "<=", ">=", "<", ">"),
    List("|"),
    List("^"),
    List("&"),
    List("<<", ">>"),
    List("+", "-"),
    List("*", "/", "//", "%")
  )

  private val operatorPrecedence: Map[String, Int] = Map(
    "or" -> 1,
    "and" -> 2,
    "is" -> 3,
    "is not" -> 3,
    "==" -> 4,
    "!=" -> 4,
    "<=" -> 5,
    ">=" -> 5,
    "<" -> 5,
    ">" -> 5,
    "|" -> 6,
    "^" -> 7,
    "&" -> 8,
    "<<" -> 9,
    ">>" -> 9,
    "+" -> 10,
    "-" -> 10,
    "*" -> 11,
    "/" -> 11,
    "//" -> 11,
    "%" -> 11,
    "not" -> 12,
    "~" -> 12
  )

  private val DefaultOperatorPrecedence = -1

  private val unaryOperators: List[String] = List("not", "+", "-", "~")

  private def parseExpression(source: String, context: ParseContext): BeExpression = {
    val trimmed = source.trim
    if (trimmed.isEmpty) {
      BeExpression.pass
    } else {
      val unwrapped = if (ParsingUtils.isParenthesized(trimmed)) trimmed.substring(1, trimmed.length - 1).trim else trimmed
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
    unaryOperators
      .collectFirst {
        case operator if startsWithUnaryOperator(trimmed, operator) =>
          val operandSource = trimmed.substring(operator.length).trim
          Option.when(operandSource.nonEmpty) {
          val operandExpr = parseExpression(operandSource, context)
          val function = context.resolveOperator(operator, 1, List(operandExpr))
            val parameterMap = Map(function.inputs.head -> operandExpr)
            OperatorFunctionCall(BeFunctionCall(function, parameterMap), operator)
          }
      }
      .flatten
  }

  private def startsWithUnaryOperator(source: String, operator: String): Boolean = {
    if (!source.startsWith(operator)) false
    else {
      val boundaryIndex = operator.length
      val requiresWordBoundary = operator.lastOption.exists(_.isLetterOrDigit)
      val isIdentifierChar: Char => Boolean = ch => ch.isLetterOrDigit || ch == '_'
      if (!requiresWordBoundary) true
      else if (boundaryIndex >= source.length) false
      else {
        val nextChar = source.charAt(boundaryIndex)
        !isIdentifierChar(nextChar)
      }
    }
  }

  private def parseBinaryExpression(source: String, context: ParseContext): Option[BeExpression] = {
    binaryPrecedence.view.flatMap { operators =>
      ParsingUtils.splitTopLevelBinary(source, operators).map { case (left, operator, right) =>
        val leftExpr = parseExpression(left, context)
        val rightExpr = parseExpression(right, context)
        val function = context.resolveOperator(operator.trim, 2, List(leftExpr, rightExpr))
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
        val variable = context.lookupVariable(source).getOrElse(context.assignVariable(source, AnyType))
        Some(BeUseValue(BeUseValueReference(variable), Some(variable)))
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

  private def skipEmptyLines(lines: Vector[ParsedLine], startIndex: Int): Int = {
    var index = startIndex
    while (index < lines.length && lines(index).content.trim.isEmpty) {
      index += 1
    }
    index
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

  private def toParsedLines(source: String): Vector[ParsedLine] = {
    val lines = source.split("\n", -1)
    lines.toVector.map { rawLine =>
      val indent = rawLine.takeWhile(_ == ' ').length
      val content = rawLine.drop(indent)
      ParsedLine(indent, content)
    }
  }

  private class ParseContext(initialKnownStructures: CurrentlyKnownStructures) {
    private var currentlyKnownStructures: CurrentlyKnownStructures = initialKnownStructures
    private var scopes: List[mutable.LinkedHashMap[String, BeDefineVariable]] = {
      val baseScope = mutable.LinkedHashMap[String, BeDefineVariable]()
      baseScope ++= initialKnownStructures.variables
      List(baseScope)
    }
    private val variablesBuffer = mutable.ListBuffer[BeDefineVariable]()
    variablesBuffer ++= initialKnownStructures.variables.values
    private val functionsBuffer = mutable.ListBuffer[BeDefineFunction]()
    private val classesBuffer = mutable.ListBuffer[BeDefineClass]()
    classesBuffer ++= initialKnownStructures.classes.values
    private val functionsByName = mutable.LinkedHashMap[String, BeDefineFunction]()
    functionsByName ++= initialKnownStructures.functions
    private val operatorFunctions = mutable.LinkedHashMap[(String, Int), List[BeDefineFunction]]()
    operatorFunctions ++= initialKnownStructures.operators

    initialKnownStructures.operators.foreach { case ((symbol, _), functions) =>
      functions.foreach { function =>
        if (!functionsByName.contains(symbol)) {
          functionsByName.update(symbol, function)
        }
        if (!functionsBuffer.exists(_ eq function)) {
          functionsBuffer += function
        }
      }
    }
    initialKnownStructures.functions.foreach { case (_, function) =>
      if (!functionsBuffer.exists(_ eq function)) {
        functionsBuffer += function
      }
    }

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
        registerVariable(name, variable)
        variable
      }
    }

    def defineVariable(
        name: String,
        dataType: BeDataType,
        hasExplicitTypeHint: Boolean = false
    ): BeDefineVariable = {
      val variable = BeDefineVariable(LanguageMap.universalMap(name), dataType, hasExplicitTypeHint)
      currentScope.update(name, variable)
      registerVariable(name, variable)
      variable
    }

    def lookupVariable(name: String): Option[BeDefineVariable] = scopes.collectFirst { case scope if scope.contains(name) => scope(name) }

    private def currentScope: mutable.LinkedHashMap[String, BeDefineVariable] = scopes.head

    def registerVariable(name: String, variable: BeDefineVariable): Unit = {
      if (!variablesBuffer.exists(_ eq variable)) {
        variablesBuffer += variable
      }
      currentlyKnownStructures = currentlyKnownStructures.addVariable(name, variable)
    }

    def registerFunction(name: String, function: BeDefineFunction, isOperator: Boolean = false): Unit = {
      functionsByName.update(name, function)
      if (!functionsBuffer.exists(_ eq function)) {
        functionsBuffer += function
      }
      currentlyKnownStructures =
        if (isOperator) currentlyKnownStructures.addOperator(name, function)
        else currentlyKnownStructures.addFunction(name, function)
    }

    def registerClass(name: String, clazz: BeDefineClass): Unit = {
      if (!classesBuffer.exists(_ eq clazz)) {
        classesBuffer += clazz
      }
      currentlyKnownStructures = currentlyKnownStructures.addClass(name, clazz)
    }

    def snapshotStructures: CurrentlyKnownStructures = currentlyKnownStructures

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
        val operatorKey = name -> updated.inputs.length
        if (operatorFunctions.contains(operatorKey)) {
          val existing = operatorFunctions(operatorKey)
          val replaced =
            if (existing.exists(_ eq function)) existing.map(cur => if (cur eq function) updated else cur)
            else existing :+ updated
          operatorFunctions.update(operatorKey, replaced)
          currentlyKnownStructures = currentlyKnownStructures.addOperator(name, updated)
        } else {
          currentlyKnownStructures = currentlyKnownStructures.addFunction(name, updated)
        }
        updated
      }
    }

    def resolveOperator(symbol: String, arity: Int, arguments: List[BeExpression]): BeDefineFunction = {
      val key = symbol -> arity
      operatorFunctions.get(key) match {
        case Some(candidates) if candidates.nonEmpty =>
          val scored = candidates.zipWithIndex.flatMap { case (candidate, index) =>
            val assignmentResults = candidate.inputs.zip(arguments).map { case (param, argument) =>
              param.variableType.canTakeValuesFrom(argument.canEvaluateTo)
            }

            if (assignmentResults.exists(_.isInstanceOf[AssigningNotPossible])) None
            else {
              val score = assignmentResults.map {
                case _: AssigningPossibleWithSameType => 0
                case _: AssigningPossibleWithImplicitCast => 1
                case _ => 2
              }.sum
              Some((candidate, score, index))
            }
          }

          scored.sortBy { case (_, score, index) => (score, index) }.headOption.map(_._1).getOrElse(candidates.head)
        case _ =>
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
          registerOperator(symbol, function)
          function
      }
    }

    private def registerOperator(symbol: String, function: BeDefineFunction): Unit = {
      val key = symbol -> function.inputs.length
      val existing = operatorFunctions.getOrElse(key, List.empty)
      val updatedList =
        if (existing.exists(_ eq function)) existing.map(cur => if (cur eq function) function else cur)
        else existing :+ function
      operatorFunctions.update(key, updatedList)
      registerFunction(symbol, function, isOperator = true)
    }

    def definedClasses: List[BeDefineClass] = classesBuffer.toList

    def definedFunctions: List[BeDefineFunction] = functionsBuffer.toList

    def definedVariables: List[BeDefineVariable] = variablesBuffer.toList

    def currentStructures: CurrentlyKnownStructures = currentlyKnownStructures
  }

  private case class OperatorFunctionCall(call: BeFunctionCall, symbol: String) extends BeExpression {
    override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = {
      val arguments = call.funcDef.inputs.flatMap(call.parameterValueMap.get)
      arguments match {
        case Nil => symbol
        case head :: tail if tail.isEmpty =>
          val renderedHead = formatOperand(head, isLeftOperand = false, programmingLanguage, humanLanguage)
          if (isAlphabeticOperator(symbol)) s"$symbol $renderedHead" else s"$symbol$renderedHead"
        case head :: tail =>
          val renderedHead = formatOperand(head, isLeftOperand = true, programmingLanguage, humanLanguage)
          tail.foldLeft(renderedHead) { (acc, expr) =>
            val rendered = formatOperand(expr, isLeftOperand = false, programmingLanguage, humanLanguage)
            s"$acc $symbol $rendered"
          }
      }
    }

    private def isAlphabeticOperator(value: String): Boolean =
      value.forall(ch => ch.isLetter || ch.isWhitespace)

    private def formatOperand(
        expression: BeExpression,
        isLeftOperand: Boolean,
        programmingLanguage: ProgrammingLanguage,
        humanLanguage: HumanLanguage
    ): String = {
      val rendered = expression.getInLanguage(programmingLanguage, humanLanguage).trim
      val requiresParentheses = expression match {
        case nested: OperatorFunctionCall =>
          val parentPrecedence = operatorPrecedence.getOrElse(symbol, DefaultOperatorPrecedence)
          val childPrecedence = operatorPrecedence.getOrElse(nested.symbol, DefaultOperatorPrecedence)
          if (childPrecedence < parentPrecedence) true
          else if (childPrecedence > parentPrecedence) false
          else !isLeftOperand && (symbol == "-" || symbol == "/" || symbol == "//")
        case _ => false
      }
      if (requiresParentheses && !(rendered.startsWith("(") && rendered.endsWith(")"))) s"($rendered)" else rendered
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
