package it.evadid.vm.conversion.snapexpression

import it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchToBeExpressionParser
import it.evadid.vm.conversion.snapxml.SnapXmlParser
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}

/** Snap execution metadata becomes ordinary startup and uncalled functions in the shared tree. */
object SnapToExpression {
  def parse(xml: String): BeExpression = {
    val unsupported = SnapExpressionSupport.unsupportedSnapSelectors(xml)
    require(unsupported.isEmpty, s"Unsupported Snap blocks: ${unsupported.mkString(", ")}")
    parseWithLayout(xml).expression
  }

  def parseWithLayout(xml: String): TurtleStitchToBeExpressionParser.ParseWithLayout = {
    val unsupported = SnapExpressionSupport.unsupportedSnapSelectors(xml)
    if unsupported.nonEmpty then return TurtleStitchToBeExpressionParser.ParseWithLayout(
      it.evadid.vm.code.errors.BeExpressionUnsupported(xml, parsedFromLanguage = Some(SnapLanguage)),
      it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout.empty)
    val parsed = TurtleStitchToBeExpressionParser.parseXmlWithLayout(xml)
    val top = SnapExpressionSupport.topLevelStatements(parsed.expression)
    val definitions = top.collect { case definition: BeDefineFunction => definition }
    val statements = SnapExpressionSupport.scriptStatements(top)
    val (active, detached, _) = parsed.canvasLayout.scripts.foldLeft(
      (List.empty[BeExpression], List.empty[BeExpression], statements)) { case ((active, detached, remaining), script) =>
      val (body, rest) = remaining.splitAt(script.callCount)
      if script.hasReceiveGo then (active ++ body, detached, rest)
      else (active, detached ++ body, rest)
    }
    val unused = Option.when(detached.nonEmpty)(BeDefineFunction(
      inputs = Nil, outputs = None, body = BeSequence.optionalBody(detached),
      functionTypeInfo = BeDefineFunction.functionInfo(BeEntityName.fromCodeString("unusedCode"))))
    val startup = if parsed.canvasLayout.isEmpty then statements else active
    // The exporter declares variables with defaults even when startup initializes them.
    // Re-importing those declarations must not prepend another redundant assignment.
    val initialized = startup.takeWhile(_.isInstanceOf[BeAssignVariable]).collect {
      case BeAssignVariable(target, BeUseValue(_: BeDataValueLiteral, _)) => target.name.universalInterpretation()
    }.toSet
    val variables = SnapXmlParser.elements(xml, "variables").flatMap(container => SnapXmlParser.children(container.inner))
      .filter(variable => variable.tag == "variable" && !initialized(BeEntityName.fromCodeString(variable.attrOrEmpty("name")).universalInterpretation())).distinctBy(_.attrOrEmpty("name")).map { variable =>
        val raw = SnapXmlParser.children(variable.inner).headOption.map(value => SnapXmlParser.unescape(value.inner)).getOrElse("0")
        val literal = if raw.toDoubleOption.nonEmpty then raw else raw match {
          case "true" => "True"
          case "false" => "False"
          case text => SnapInputCodec.quotePython(text)
        }
        val target = BeDefineVariable(BeEntityName.fromCodeString(variable.attrOrEmpty("name")), BeDataValueLiteral(literal).currentType)
        BeAssignVariable(target, BeUseValue(BeDataValueLiteral(literal), None))
      }
    parsed.copy(expression = BeStartProgram(BeSequence.optionalBody(variables.toList ++ definitions ++ unused.toList ++ startup)))
  }
}
