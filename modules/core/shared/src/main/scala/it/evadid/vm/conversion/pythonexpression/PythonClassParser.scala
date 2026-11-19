package it.evadid.vm.conversion.pythonexpression

import it.evadid.vm.conversion.parsed.{DefaultDefinitions, SymbolTable}
import PythonLexerLike.{ParsedLine, findBodyIndent, splitCodeAndComment}
import SymbolTable.ParseContext
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.defining.BeDefineFunction.*
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType

/** Class signatures are bound before bodies; definitions never contain a receiver parameter. */
object PythonClassParser {
  private[pythonexpression] val ClassPattern = """^class\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*\(([^)]*)\))?:$""".r
  private val FunctionPattern = """^def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?:->\s*([^:]+))?:$""".r
  private val FieldPattern = """^(self\.)?([A-Za-z_][A-Za-z0-9_]*)(?:\s*:\s*([^=]+?))?(?:\s*=(?!=)\s*(.+))?$""".r
  final case class ClassParseResult(expression: BeExpression, nextIndex: Int) derives upickle.default.ReadWriter
  final case class ClassParserApi(
    parseBlock: (Vector[ParsedLine], Int, Int, ParseContext) => PythonStatementParser.BlockParseResult,
    parseExpression: (String, ParseContext) => BeExpression,
    parseParameters: String => List[(String, Option[String])],
    inferType: BeExpression => BeDataType,
    mapType: Option[String] => BeDataType)

  private case class MethodHeader(index: Int, name: String, parameterNames: List[String], parameters: List[BeDefineVariable], result: Option[BeDefineVariable], kind: BeFunctionType)

  def declareClasses(lines: Vector[ParsedLine], context: ParseContext, api: ClassParserApi): Unit = {
    val declarations = lines.zipWithIndex.collect {
      case (line, index) if line.indent == 0 && ClassPattern.matches(splitCodeAndComment(line.content)._1.trim) =>
        val ClassPattern(name, bases) = splitCodeAndComment(line.content)._1.trim: @unchecked
        (index, name, Option(bases))
    }
    declarations.foreach { (_, name, bases) =>
      if bases.forall(_.trim.isEmpty) then context.registerClass(name, BeDefineClass(BeEntityName.fromCodeString(name), Nil, Nil))
    }
    declarations.foreach { (index, name, bases) => parseClass(lines, index, 0, name, bases, context, api, signaturesOnly = true) }
  }

  def parseClass(lines: Vector[ParsedLine], headerIndex: Int, indent: Int, name: String,
      basesSource: Option[String], context: ParseContext, api: ClassParserApi, signaturesOnly: Boolean = false): ClassParseResult = {
    val nextIndex = (headerIndex + 1 until lines.size).find(i => lines(i).content.trim.nonEmpty && lines(i).indent <= indent).getOrElse(lines.size)
    val bodyIndent = findBodyIndent(lines, headerIndex + 1, indent)
    val bodyLines = lines.slice(headerIndex + 1, nextIndex)
    def unsupported(reason: String): ClassParseResult = ClassParseResult(
      BeExpressionUnsupported(lines.slice(headerIndex, nextIndex).map(line => " " * line.indent + line.content).mkString("\n"), Some(Python)), nextIndex)
    if bodyIndent <= indent then return ClassParseResult(BeExpressionUnparsable(lines(headerIndex).content,
      s"Missing body for class $name", Some(Python)), nextIndex)
    if basesSource.exists(_.trim.nonEmpty) then return unsupported("Inheritance")

    val headers = bodyLines.zipWithIndex.collect {
      case (line, offset) if line.indent == bodyIndent && FunctionPattern.matches(splitCodeAndComment(line.content)._1.trim) =>
        val FunctionPattern(methodName, parameterSource, resultSource) = splitCodeAndComment(line.content)._1.trim: @unchecked
        val previous = lines.lift(headerIndex + offset).map(_.content.trim)
        val kind = if previous.contains("@staticmethod") then StaticMethod()
          else if methodName == "__init__" then Constructor() else Method()
        val parameters = api.parseParameters(parameterSource)
        if kind != StaticMethod() then require(parameters.headOption.exists(_._1 == "self"), s"Method '$name.$methodName' requires an explicit self parameter.")
        val explicit = if kind == StaticMethod() then parameters else parameters.tail
        MethodHeader(headerIndex + 1 + offset, methodName, explicit.map(_._1),
          explicit.map((parameter, hint) => BeDefineVariable(BeEntityName.fromCodeString(parameter), api.mapType(hint))),
          Option(resultSource).filterNot(value => Set("None", "void")(value.trim)).map(hint => BeDefineVariable(BeEntityName.fromCodeString("result"), api.mapType(Some(hint)))), kind)
    }.toList
    val unknownBody = bodyLines.exists { line =>
      val text = splitCodeAndComment(line.content)._1.trim
      line.indent == bodyIndent && text.nonEmpty && text != "pass" && text != "@staticmethod" &&
        !FunctionPattern.matches(text) && !FieldPattern.matches(text)
    }
    if unknownBody then return unsupported("Class-body statements or decorators")

    val classContext = new ParseContext(context.snapshotStructures, context.sourceCallableNames)
    val fields = bodyLines.zipWithIndex.flatMap { (line, offset) =>
      val text = splitCodeAndComment(line.content)._1.trim
      text match {
        case FieldPattern(receiver, fieldName, hint, value) if (receiver != null || line.indent == bodyIndent) && (hint != null || value != null) =>
          val position = headerIndex + offset + 1
          val enclosing = headers.filter(_.index < position).lastOption
          val fieldContext = new ParseContext(context.snapshotStructures, context.sourceCallableNames)
          enclosing.foreach(header => header.parameterNames.zip(header.parameters).foreach((rawName, parameter) => fieldContext.defineVariable(rawName, parameter.variableType)))
          lazy val initial = Option(value).map(api.parseExpression(_, fieldContext))
          val constructorType = Option(value).flatMap(ParsingUtils.findTopLevelCall).flatMap { (called, _) =>
            context.lookupClass(called).map(clazz => BeDataType.ClassType(clazz.name))
          }
          val dataType = Option(hint).map(h => api.mapType(Some(h.trim))).orElse(constructorType).orElse(initial.map(api.inferType)).getOrElse(BeDataType.AnyType)
          Some(BeDefineVariable(BeEntityName.fromCodeString(fieldName), dataType, if receiver == null then initial else None))
        case _ => None
      }
    }.toList.groupBy(_.name).values.map(_.head).toList.sortBy(_.name.universalInterpretation())
    val owner = BeDefineClass(BeEntityName.fromCodeString(name), fields, Nil)
    def signature(header: MethodHeader) = BeDefineFunction(header.parameters, header.result, BeExpression.pass,
      BeFunctionTypeInfo(Some(owner), Option.when(header.kind != Constructor())(BeEntityName.fromCodeString(header.name)), header.kind))
    val signatures = owner.copy(methods = headers.map(signature))
    if signaturesOnly then {
      context.registerClass(name, signatures)
      return ClassParseResult(signatures, nextIndex)
    }
    classContext.registerClass(name, signatures)
    val methods = headers.map { header =>
      val methodContext = new ParseContext(classContext.snapshotStructures, context.sourceCallableNames,
        Option.when(header.kind != StaticMethod())(owner))
      header.parameterNames.zip(header.parameters).foreach((rawName, parameter) => methodContext.defineVariable(rawName, parameter.variableType))
      val start = header.index + 1
      val methodIndent = findBodyIndent(lines, start, bodyIndent)
      require(methodIndent > bodyIndent, s"Missing body for '$name.${header.name}'.")
      signature(header).copy(body = BeSequence.optionalBody(api.parseBlock(lines, start, methodIndent, methodContext).expressions))
    }
    val result = owner.copy(methods = methods)
    context.registerClass(name, result)
    ClassParseResult(result, nextIndex)
  }
}
