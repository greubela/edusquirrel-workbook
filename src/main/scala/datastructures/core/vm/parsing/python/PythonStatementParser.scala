package datastructures.core.vm.parsing.python

import ParsingUtils.keepExpression
import PythonClassParser.{ClassParseResult, ClassParserApi}
import PythonLexerLike.{ParsedLine, splitInlineComment}
import PythonSymbolTable.ParseContext
import datastructures.core.language.LanguageMap
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.errors.{BeExpressionUnparsable, BeSingleLineComment}
import datastructures.core.vm.code.usage.BeAssignVariable

import scala.collection.mutable

object PythonStatementParser {

  final case class BlockParseResult(expressions: List[BeExpression], nextIndex: Int)
  final case class NodeWithNext(expression: BeExpression, nextIndex: Int)

  private final case class DispatchRule(matches: String => Boolean, handle: DispatchContext => DispatchOutcome)
  private final case class DispatchContext(lines: Vector[ParsedLine], index: Int, indent: Int, trimmed: String, context: ParseContext)
  private final case class DispatchOutcome(expressions: List[BeExpression], nextIndex: Int)

  private val AnnotationAssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+?)\s*=\s*(.+)$""".r
  private val ClassPattern = """^class\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*\(([^)]*)\))?:$""".r
  private val FunctionPattern = """^def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?:->\s*([^:]+))?:$""".r
  private val WhilePattern = """^while\s+(.+):$""".r
  private val IfPattern = """^if\s+(.+):$""".r
  private val AssignmentPattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*(.+)$""".r

  final case class StatementApi(
                                 parseBlock: (Vector[ParsedLine], Int, Int, ParseContext) => BlockParseResult,
                                 parseClass: (Vector[ParsedLine], Int, Int, String, Option[String], ParseContext) => ClassParseResult,
                                 parseFunction: (Vector[ParsedLine], Int, Int, String, String, Option[String], ParseContext) => NodeWithNext,
                                 parseWhile: (Vector[ParsedLine], Int, Int, String, ParseContext) => NodeWithNext,
                                 parseIf: (Vector[ParsedLine], Int, Int, String, ParseContext) => NodeWithNext,
                                 parseReturn: (String, ParseContext) => BeExpression,
                                 parseExpression: (String, ParseContext) => BeExpression,
                                 inferType: BeExpression => datastructures.core.vm.types.BeDataType,
                                 mapType: Option[String] => datastructures.core.vm.types.BeDataType,
                                 isTryHeader: String => Boolean,
                                 collectTryExceptBlock: (Vector[ParsedLine], Int, Int) => (String, Int)
                               )

  def parseBlock(
                  lines: Vector[ParsedLine],
                  startIndex: Int,
                  indent: Int,
                  context: ParseContext,
                  api: StatementApi
                ): BlockParseResult = {
    val expressions = mutable.ListBuffer[BeExpression]()
    var index = startIndex

    val rules = buildDispatchRules(api)

    while (index < lines.length) {
      val line = lines(index)
      if (line.indent < indent) return BlockParseResult(expressions.toList, index)

      val (codePortion, inlineComment) = splitInlineComment(line.content)
      val trimmed = codePortion.trim

      if (trimmed.isEmpty) {
        expressions += inlineComment.map(comment => BeSingleLineComment(LanguageMap.universalMap(comment))).getOrElse(BeExpression.pass)
        index += 1
      } else if (line.indent > indent) {
        val nested = api.parseBlock(lines, index, line.indent, context)
        expressions ++= nested.expressions
        index = nested.nextIndex
      } else {
        val dispatchContext = DispatchContext(lines, index, indent, trimmed, context)
        val outcome = rules.find(_.matches(trimmed)).map(_.handle(dispatchContext)).getOrElse {
          DispatchOutcome(List(api.parseExpression(trimmed, context)), index + 1)
        }
        expressions ++= outcome.expressions
        index = outcome.nextIndex
        inlineComment.foreach(commentText => expressions += BeSingleLineComment(LanguageMap.universalMap(commentText)))
      }
    }

    BlockParseResult(expressions.toList.filter(keepExpression), index)
  }

  private def buildDispatchRules(api: StatementApi): List[DispatchRule] = List(
    DispatchRule(_.matches(AnnotationAssignmentPattern.regex), ctx => {
      val AnnotationAssignmentPattern(name, typeHint, valueStr) = ctx.trimmed
      val variable = ctx.context.defineVariable(name, api.mapType(Some(typeHint.trim)))
      val valueExpr = api.parseExpression(valueStr, ctx.context)
      DispatchOutcome(List(BeAssignVariable(variable, valueExpr)), ctx.index + 1)
    }),
    DispatchRule(_.matches(ClassPattern.regex), ctx => {
      val ClassPattern(name, bases) = ctx.trimmed
      val result = api.parseClass(ctx.lines, ctx.index, ctx.indent, name, Option(bases), ctx.context)
      DispatchOutcome(List(result.expression), result.nextIndex)
    }),
    DispatchRule(_.matches(FunctionPattern.regex), ctx => {
      val FunctionPattern(name, params, returnType) = ctx.trimmed
      val result = api.parseFunction(ctx.lines, ctx.index, ctx.indent, name, params, Option(returnType), ctx.context)
      DispatchOutcome(List(result.expression), result.nextIndex)
    }),
    DispatchRule(_.matches(WhilePattern.regex), ctx => {
      val WhilePattern(conditionSource) = ctx.trimmed
      val result = api.parseWhile(ctx.lines, ctx.index, ctx.indent, conditionSource, ctx.context)
      DispatchOutcome(List(result.expression), result.nextIndex)
    }),
    DispatchRule(_.matches(IfPattern.regex), ctx => {
      val IfPattern(conditionSource) = ctx.trimmed
      val result = api.parseIf(ctx.lines, ctx.index, ctx.indent, conditionSource, ctx.context)
      DispatchOutcome(List(result.expression), result.nextIndex)
    }),
    DispatchRule(api.isTryHeader, ctx => {
      val (rawBlock, nextIndex) = api.collectTryExceptBlock(ctx.lines, ctx.index, ctx.indent)
      DispatchOutcome(List(BeExpressionUnparsable(rawBlock, "try/except statements are currently unsupported")), nextIndex)
    }),
    DispatchRule(_.startsWith("return"), ctx => DispatchOutcome(List(api.parseReturn(ctx.trimmed, ctx.context)), ctx.index + 1)),
    DispatchRule(_ == "pass", ctx => DispatchOutcome(List(BeExpression.pass), ctx.index + 1)),
    DispatchRule(_.matches(AssignmentPattern.regex), ctx => {
      val AssignmentPattern(name, valueStr) = ctx.trimmed
      val valueExpr = api.parseExpression(valueStr, ctx.context)
      val variable = ctx.context.assignVariable(name, api.inferType(valueExpr))
      DispatchOutcome(List(BeAssignVariable(variable, valueExpr)), ctx.index + 1)
    }),
    DispatchRule(_.startsWith("while"), ctx => DispatchOutcome(List(BeExpressionUnparsable(ctx.trimmed, "While statements must end with ':'")), ctx.index + 1)),
    DispatchRule(_.startsWith("if"), ctx => DispatchOutcome(List(BeExpressionUnparsable(ctx.trimmed, "If statements must end with ':'")), ctx.index + 1))
  )
}
