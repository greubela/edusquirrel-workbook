package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.naming.NamingStyle

/**
 * Turtle-subset Python ↔ ProgrammingExerciseState bridge for dual-mode Snap editing.
 *
 * Python uses snake_case (`goto_x_y`); Snap selectors stay camelCase (`gotoXY`).
 * Layout is preserved when total call counts still match the sidecar partitions.
 */
object SnapTurtlePythonBridge {

  /** One allow-listed primitive with Python syntax and Snap block selector. */
  final case class TurtlePrimitive(
      pythonName: String,
      snapSelector: String,
      example: String
  )

  /**
   * Canonical turtle subset. `pythonName` matches BeProgram→Python (SnakeCase);
   * `snapSelector` is the Snap `<block s="...">` id.
   */
  val Primitives: List[TurtlePrimitive] = List(
    TurtlePrimitive("receive_go", "receiveGo", "receive_go()"),
    TurtlePrimitive("forward", "forward", "forward(steps)"),
    TurtlePrimitive("turn", "turn", "turn(degrees)"),
    TurtlePrimitive("goto_x_y", "gotoXY", "goto_x_y(x, y)"),
    TurtlePrimitive("set_heading", "setHeading", "set_heading(degrees)"),
    TurtlePrimitive("clear", "clear", "clear()"),
    TurtlePrimitive("down", "down", "down()"),
    TurtlePrimitive("up", "up", "up()")
  )

  /** Python / SnakeCase names accepted for seamless block roundtrips. */
  val AllowedPythonNames: Set[String] = Primitives.map(_.pythonName).toSet

  /** @deprecated Use [[AllowedPythonNames]]; kept for older call sites. */
  val AllowedCallNames: Set[String] = AllowedPythonNames

  private val snapSelectorByPythonName: Map[String, String] =
    Primitives.map(p => p.pythonName -> p.snapSelector).toMap

  /**
   * Parse Python, validate the turtle subset, and reconcile canvas layout.
   * @return Right(new state) or Left(user-facing error message)
   */
  def applyPython(
      source: String,
      previous: ProgrammingExerciseState
  ): Either[String, ProgrammingExerciseState] =
    try
      val program = BeProgram.fromPythonString(source)
      validateSubset(program.fullProgram) match
        case Left(message) => Left(message)
        case Right(calls) =>
          val layout = reconcileLayout(previous.canvasLayout, calls.size)
          Right(ProgrammingExerciseState(program, layout))
    catch
      case e: Throwable =>
        val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
        Left(s"Parse error; keeping existing blocks. ($detail)")

  /** Keep previous script partitions/positions when call counts still match; else single script. */
  def reconcileLayout(previous: SnapCanvasLayout, callCount: Int): SnapCanvasLayout =
    if callCount <= 0 then SnapCanvasLayout.empty
    else if layoutMatches(previous, callCount) then previous
    else SnapCanvasLayout.single(callCount = callCount)

  def layoutMatches(layout: SnapCanvasLayout, totalCalls: Int): Boolean =
    !layout.isEmpty &&
      layout.scripts.map(_.callCount).sum == totalCalls &&
      layout.scripts.forall(_.callCount > 0)

  def topLevelCalls(expression: BeExpression): List[BeFunctionCall] =
    bodyExpressions(expression).collect { case c: BeFunctionCall => c }

  /** Python / SnakeCase name used in printed code and allow-list checks. */
  def pythonName(call: BeFunctionCall): String =
    call.funcDef.functionTypeInfo.displayName
      .getNameIn(AppLanguage.English, NamingStyle.SnakeCase)
      .trim

  /** Snap `<block s>` selector for this call (maps Python snake_case → Snap id). */
  def snapSelectorOf(call: BeFunctionCall): String =
    snapSelectorByPythonName.getOrElse(pythonName(call), pythonName(call))

  /** @deprecated Use [[pythonName]]. */
  def callName(call: BeFunctionCall): String = pythonName(call)

  /**
   * Accept only allow-listed top-level calls (comments / control / unsupported rejected).
   * @return Right(calls) in program order, or Left(error)
   */
  def validateSubset(expression: BeExpression): Either[String, List[BeFunctionCall]] = {
    val body = bodyExpressions(expression)
    if body.isEmpty then Right(Nil)
    else
      val problems = body.flatMap(describeUnsupported)
      if problems.nonEmpty then
        Left("Unsupported for blocks: " + problems.take(3).mkString(" | "))
      else
        Right(body.collect { case c: BeFunctionCall => c })
  }

  private def bodyExpressions(expression: BeExpression): List[BeExpression] =
    expression match
      case BeStartProgram(Some(sequence)) => sequence.body.toList
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body.toList
      case other => List(other)

  private def describeUnsupported(expression: BeExpression): Option[String] =
    expression match
      case call: BeFunctionCall =>
        val name = pythonName(call)
        if AllowedPythonNames.contains(name) then None
        else Some(s"$name(...)")
      case _: BeSingleLineComment =>
        Some("comments are not mapped to Snap blocks")
      case u: BeExpressionUnsupported =>
        Some(preview(u.originalSource))
      case u: BeExpressionUnparsable =>
        Some(preview(u.originalSource))
      case other =>
        Some(preview(other.getClass.getSimpleName.replace("Be", "")))

  private def preview(text: String): String =
    val trimmed = text.trim
    if trimmed.length <= 40 then trimmed
    else trimmed.take(37) + "..."
}
