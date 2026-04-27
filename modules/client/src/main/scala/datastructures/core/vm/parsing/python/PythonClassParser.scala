package datastructures.core.vm.parsing.python

import PythonLexerLike.{ParsedLine, findBodyIndent, splitCodeAndComment}
import PythonSymbolTable.ParseContext
import datastructures.core.language.{HumanLanguage, LanguageMap}
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.controlStructures.BeSequence
import datastructures.core.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import datastructures.core.vm.code.errors.{BeExpressionUnparsable, BeSingleLineComment}
import datastructures.core.vm.types.BeDataType.{AnyType, BeUnionAllowedTypes}
import datastructures.core.vm.types.BeDataType

import scala.collection.mutable

object PythonClassParser {
  private val FunctionPattern = """^def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?:->\s*([^:]+))?:$""".r
  private val ClassAttributeAnnotationAssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)\s*=\s*(.+)$""".r
  private val ClassAttributeAnnotationPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.+)$""".r
  private val AssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*(.+)$""".r
  private val SelfAttributeAnnotationAssignmentPattern = """^(self|cls)\.([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)\s*=\s*(.+)$""".r
  private val SelfAttributeAnnotationPattern = """^(self|cls)\.([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.+)$""".r
  private val SelfAttributeAssignmentPattern = """^(self|cls)\.([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*(.+)$""".r
  private val WhilePattern = """^while\s+(.+):$""".r
  private val IfPattern = """^if\s+(.+):$""".r

  final case class AttributeRecord(name: String, variable: BeDefineVariable)
  final case class ParsedMethod(name: String, template: BeDefineFunction, attributes: List[AttributeRecord], nextIndex: Int)
  final case class ClassParseResult(expression: BeExpression, nextIndex: Int)

  final case class ClassParserApi(
                                   parseBlock: (Vector[ParsedLine], Int, Int, ParseContext) => PythonStatementParser.BlockParseResult,
                                   parseWhile: (Vector[ParsedLine], Int, Int, String, ParseContext) => PythonStatementParser.NodeWithNext,
                                   parseIf: (Vector[ParsedLine], Int, Int, String, ParseContext) => PythonStatementParser.NodeWithNext,
                                   parseReturn: (String, ParseContext) => BeExpression,
                                   parseExpression: (String, ParseContext) => BeExpression,
                                   parseParameters: String => List[(String, Option[String])],
                                   inferType: BeExpression => BeDataType,
                                   mapType: Option[String] => BeDataType
                                 )

  def parseClass(
                  lines: Vector[ParsedLine],
                  headerIndex: Int,
                  indent: Int,
                  name: String,
                  basesSource: Option[String],
                  context: ParseContext,
                  api: ClassParserApi
                ): ClassParseResult = {
    val bodyIndent = findBodyIndent(lines, headerIndex + 1, indent)
    basesSource.foreach(_ => ())
    if (bodyIndent <= indent) {
      ClassParseResult(BeExpressionUnparsable(lines(headerIndex).content.trim, s"Missing body for class $name"), headerIndex + 1)
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
          val nested = api.parseBlock(lines, index, line.indent, isolatedContext)
          ignoredBodyExpressions ++= nested.expressions
          index = nested.nextIndex
        } else {
          val (codePortion, inlineComment) = splitCodeAndComment(line.content)
          val trimmed = codePortion.trim
          if (trimmed.isEmpty) {
            inlineComment.foreach(commentText => ignoredBodyExpressions += BeSingleLineComment(LanguageMap.universalMap(commentText)))
            index += 1
          } else {
            trimmed match {
              case FunctionPattern(methodName, paramsSource, returnSource) =>
                val methodResult = parseMethod(lines, index, bodyIndent, methodName, paramsSource, Option(returnSource), context, api)
                methodsBuffer += methodResult
                methodResult.attributes.foreach(attributeRecord => attributesBuffer.update(attributeRecord.name, attributeRecord.variable))
                index = methodResult.nextIndex
              case ClassAttributeAnnotationAssignmentPattern(attributeName, typeHint, valueSource) =>
                recordAttribute(attributesBuffer, attributeName, Some(typeHint), Some(valueSource), context, api)
                index += 1
              case ClassAttributeAnnotationPattern(attributeName, typeSource) if !typeSource.contains("=") =>
                recordAttribute(attributesBuffer, attributeName, Some(typeSource), None, context, api)
                index += 1
              case AssignmentPattern(attributeName, valueSource) =>
                recordAttribute(attributesBuffer, attributeName, None, Some(valueSource), context, api)
                index += 1
              case WhilePattern(conditionSource) =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                val whileResult = api.parseWhile(lines, index, bodyIndent, conditionSource, isolatedContext)
                ignoredBodyExpressions += whileResult.expression
                index = whileResult.nextIndex
              case IfPattern(conditionSource) =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                val ifResult = api.parseIf(lines, index, bodyIndent, conditionSource, isolatedContext)
                ignoredBodyExpressions += ifResult.expression
                index = ifResult.nextIndex
              case _ if trimmed.startsWith("return") =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                ignoredBodyExpressions += api.parseReturn(trimmed, isolatedContext)
                index += 1
              case _ if trimmed == "pass" =>
                ignoredBodyExpressions += BeExpression.pass
                index += 1
              case _ =>
                val isolatedContext = new ParseContext(context.snapshotStructures)
                ignoredBodyExpressions += api.parseExpression(trimmed, isolatedContext)
                index += 1
            }
            inlineComment.foreach(commentText => ignoredBodyExpressions += BeSingleLineComment(LanguageMap.universalMap(commentText)))
          }
        }
      }

      val attributes = attributesBuffer.values.toList
      val parsedMethods = methodsBuffer.toList

      val classNameMap = LanguageMap.universalMap[HumanLanguage](name)
      val classPlaceholder = BeDefineClass(classNameMap, attributes, Nil, ignoredBodyExpressions.toList)
      val methodInstances = parsedMethods.map { methodResult =>
        methodResult.template.copy(
          functionTypeInfo = BeDefineFunction.BeFunctionTypeInfo(
            isMethodInClass = Some(classPlaceholder),
            isNamed = Some(LanguageMap.universalMap[HumanLanguage](methodResult.name)),
            funcType = BeDefineFunction.Method()
          )
        )
      }
      val classExpr = classPlaceholder.copy(methods = methodInstances)
      context.registerClass(name, classExpr)
      ClassParseResult(classExpr, index)
    }
  }

  private def parseMethod(
                           lines: Vector[ParsedLine],
                           headerIndex: Int,
                           indent: Int,
                           name: String,
                           paramsSource: String,
                           returnSource: Option[String],
                           context: ParseContext,
                           api: ClassParserApi
                         ): ParsedMethod = {
    val methodContext = new ParseContext(context.snapshotStructures)
    methodContext.pushScope()
    val parameterDefinitions = api.parseParameters(paramsSource).map { case (paramName, typeHint) =>
      methodContext.defineVariable(paramName, api.mapType(typeHint))
    }

    val returnVariable = returnSource.map(_.trim).filter(_.nonEmpty).map(returnHint => BeDefineVariable(LanguageMap.universalMap("return"), api.mapType(Some(returnHint))))

    val computedIndent = findBodyIndent(lines, headerIndex + 1, indent)

    val (bodyExpressions, nextIndex, discoveredAttributes) =
      if (computedIndent <= indent) {
        val unparsable = BeExpressionUnparsable(lines(headerIndex).content.trim, s"Missing body for method $name")
        (List(unparsable), headerIndex + 1, List.empty[AttributeRecord])
      } else {
        val block = api.parseBlock(lines, headerIndex + 1, computedIndent, methodContext)
        val methodAttributes =
          if (name == "__init__" || name == "__new__")
            collectMethodAttributes(lines, headerIndex + 1, block.nextIndex, computedIndent, methodContext, api)
          else List.empty[AttributeRecord]
        (block.expressions, block.nextIndex, methodAttributes)
      }

    methodContext.popScope()

    val body = BeSequence.optionalBody(bodyExpressions)
    val functionInfo = BeDefineFunction.functionInfo(LanguageMap.universalMap(name))
    val indentWidth = if (bodyExpressions.nonEmpty && computedIndent > indent) computedIndent - indent else 4
    val template = BeDefineFunction(parameterDefinitions, returnVariable, body, functionInfo, indentWidth)
    ParsedMethod(name, template, discoveredAttributes, nextIndex)
  }

  private def collectMethodAttributes(
                                       lines: Vector[ParsedLine],
                                       startIndex: Int,
                                       endIndex: Int,
                                       bodyIndent: Int,
                                       context: ParseContext,
                                       api: ClassParserApi
                                     ): List[AttributeRecord] = {
    val attributes = mutable.LinkedHashMap[String, BeDefineVariable]()
    var index = startIndex
    while (index < endIndex) {
      val line = lines(index)
      if (line.indent == bodyIndent) {
        val (codePortion, _) = splitCodeAndComment(line.content)
        val trimmed = codePortion.trim
        trimmed match {
          case SelfAttributeAnnotationAssignmentPattern(_, attributeName, typeHint, valueSource) =>
            recordAttribute(attributes, attributeName, Some(typeHint), Some(valueSource), context, api)
          case SelfAttributeAnnotationPattern(_, attributeName, typeSource) if !typeSource.contains("=") =>
            recordAttribute(attributes, attributeName, Some(typeSource), None, context, api)
          case SelfAttributeAssignmentPattern(_, attributeName, valueSource) =>
            recordAttribute(attributes, attributeName, None, Some(valueSource), context, api)
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
                               context: ParseContext,
                               api: ClassParserApi
                             ): Unit = {
    val normalizedExplicit = explicitType.map(_.trim).filter(_.nonEmpty)
    val dataType = normalizedExplicit.map(typeHint => api.mapType(Some(typeHint))).getOrElse {
      valueSource.map(_.trim).filter(_.nonEmpty).map { valueText =>
        val isolated = new ParseContext(context.snapshotStructures)
        api.inferType(api.parseExpression(valueText, isolated))
      }.getOrElse(AnyType)
    }
    val attribute = BeDefineVariable(LanguageMap.universalMap(attributeName), dataType)
    buffer.update(attributeName, attribute)
  }
}
