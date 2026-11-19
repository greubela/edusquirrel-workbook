package it.evadid.vm.conversion.snapexpression

import it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchToBeExpressionParser
import it.evadid.vm.conversion.turtlestitchexpression.{TurtleStitchProgramModel, TurtleStitchXmlLoader}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}

/** Snap execution metadata becomes ordinary startup and uncalled functions in the shared tree. */
object SnapToExpression {
  def parse(xml: String): BeExpression = {
    val project = TurtleStitchXmlLoader.load(xml)
    val unsupported = SnapExpressionSupport.unsupportedSnapSelectors(xml, project)
    if unsupported.nonEmpty then
      throw it.evadid.vm.code.errors.ProgrammingConversionException.unsupported(s"Unsupported Snap blocks: ${unsupported.mkString(", ")}")
    fromProject(project).expression
  }

  def parseWithLayout(xml: String): TurtleStitchToBeExpressionParser.ParseWithLayout = {
    val project = TurtleStitchXmlLoader.load(xml)
    if SnapExpressionSupport.unsupportedSnapSelectors(xml, project).nonEmpty then TurtleStitchToBeExpressionParser.ParseWithLayout(
      it.evadid.vm.code.errors.BeExpressionUnsupported(xml, parsedFromLanguage = Some(SnapLanguage)),
      it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout.empty)
    else fromProject(project)
  }

  private def fromProject(project: TurtleStitchProgramModel.Project): TurtleStitchToBeExpressionParser.ParseWithLayout = {
    val parsed = TurtleStitchToBeExpressionParser.parseProjectWithLayout(project)
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
      functionTypeInfo = BeDefineFunction.functionInfo(it.evadid.vm.BeProgram.unusedCodeName)))
    val startup = if parsed.canvasLayout.isEmpty then statements else active
    // The exporter declares variables with defaults even when startup initializes them.
    // Re-importing those declarations must not prepend another redundant assignment, and the
    // initial literal assignments stay ahead of the definitions, as module variables do.
    val (initial, rest) = startup.span {
      case BeAssignVariable(_, BeUseValue(_: BeDataValueLiteral, _)) => true
      case _ => false
    }
    val initialized = initial.collect { case BeAssignVariable(target, _) => target.name.universalInterpretation() }.toSet
    // Project variables (scene, stage and sprites) start with their saved value; block-local ones do not.
    val variables = project.variables.filter(variable => variable.name.trim.nonEmpty &&
        !initialized(BeEntityName.fromCodeString(variable.name).universalInterpretation())).distinctBy(_.name).map { variable =>
      val literal = variable.value match {
        case Some(TurtleStitchProgramModel.BoolLiteral(value)) => if value then "True" else "False"
        case Some(TurtleStitchProgramModel.Literal(raw)) if raw.toDoubleOption.nonEmpty => raw
        case Some(TurtleStitchProgramModel.Literal(text)) => SnapInputCodec.quotePython(text)
        case None => "0"
        case Some(other) => throw it.evadid.vm.code.errors.ProgrammingConversionException.unsupported(
          s"The saved value of Snap variable ${variable.name} (${other.getClass.getSimpleName}) has no Python form.")
      }
      // Snap variables are untyped, like those its scripts assign.
      val target = BeDefineVariable(BeEntityName.fromCodeString(variable.name), BeDataType.AnyType)
      BeAssignVariable(target, BeUseValue(BeDataValueLiteral(literal), None))
    }
    parsed.copy(expression = BeStartProgram(BeSequence.optionalBody(variables.toList ++ initial ++ definitions ++ unused.toList ++ rest)))
  }
}
