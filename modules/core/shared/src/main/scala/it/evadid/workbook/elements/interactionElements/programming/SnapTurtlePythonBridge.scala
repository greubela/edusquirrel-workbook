package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.usage.BeFunctionCall
import it.evadid.vm.naming.NamingStyle

/**
 * Turtle-subset Python ↔ Snap XML bridge for dual-mode editing.
 *
 * Python uses snake_case (`goto_x_y`); Snap selectors stay camelCase (`gotoXY`).
 * Successful apply writes Snap project XML. Layout is preserved when the caller
 * supplies matching previous script partitions.
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
    TurtlePrimitive("do_wait", "doWait", "do_wait(seconds)"),
    TurtlePrimitive("forward", "forward", "forward(steps)"),
    TurtlePrimitive("turn", "turn", "turn(degrees)"),
    TurtlePrimitive("goto_x_y", "gotoXY", "goto_x_y(x, y)"),
    TurtlePrimitive("set_heading", "setHeading", "set_heading(degrees)"),
    TurtlePrimitive("clear", "clear", "clear()"),
    TurtlePrimitive("down", "down", "down()"),
    TurtlePrimitive("up", "up", "up()")
  )

  val ControlFlowExamples: List[String] = List(
    "if True:\n    forward(10)\nelse:\n    turn(90)",
    "while not True:\n    forward(5)",
    "for _ in range(4):\n    turn(90)"
  )

  val VariableExamples: List[String] = List(
    "steps = 10",
    "steps = steps + 1",
    "if steps < 10:\n    forward(steps)"
  )

  /** Python / SnakeCase names accepted for seamless block roundtrips. */
  val AllowedPythonNames: Set[String] = Primitives.map(_.pythonName).toSet

  val AllowedSnapSelectors: Set[String] =
    Primitives.map(_.snapSelector).toSet ++
      SnapControlFlow.ControlSelectors ++
      SnapControlFlow.VariableSelectors ++
      SnapControlFlow.ConditionSelectors

  private val snapSelectorByPythonName: Map[String, String] =
    Primitives.map(p => p.pythonName -> p.snapSelector).toMap

  private val BlockSelectorPattern = """<block\b[^>]*\bs="([^"]+)"""".r

  /**
   * Parse Python, validate the turtle subset, and write Snap XML.
   * @param previousLayout derived script partitions from the current XML, if known
   */
  def applyPython(
      source: String,
      previousLayout: SnapCanvasLayout = SnapCanvasLayout.empty
  ): Either[String, ProgrammingExerciseState] =
    try
      val program = BeProgram.fromPythonString(source)
      validateSubset(program.fullProgram) match
        case Left(message) => Left(message)
        case Right(statements) =>
          if statements.isEmpty then Right(ProgrammingExerciseState.empty)
          else
            val layout = reconcileLayout(previousLayout, statements.size)
            Right(ProgrammingExerciseState.fromProgram(program, layout))
    catch
      case e: Throwable =>
        val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
        Left(s"Parse error; keeping existing blocks. ($detail)")

  def printedPython(expression: BeExpression): String =
    expression.structureInfo.toStringInLanguage(Python, English, false)

  /** Snap selectors in `xml` that are outside the Python-compatible allow-list. */
  def unsupportedSnapSelectors(xml: String): List[String] = {
    val fromBlocks = BlockSelectorPattern.findAllMatchIn(xml).map(_.group(1)).toList
    val customs = if xml.contains("<custom-block") then List("custom-block") else Nil
    (fromBlocks.filterNot(AllowedSnapSelectors.contains) ++ customs).distinct
  }

  def isPythonCompatibleXml(xml: String): Boolean =
    unsupportedSnapSelectors(xml).isEmpty

  /** Keep previous script partitions/positions when statement counts still match; else single script. */
  def reconcileLayout(previous: SnapCanvasLayout, statementCount: Int): SnapCanvasLayout =
    if statementCount <= 0 then SnapCanvasLayout.empty
    else if layoutMatches(previous, statementCount) then previous
    else SnapCanvasLayout.single(callCount = statementCount)

  def layoutMatches(layout: SnapCanvasLayout, totalStatements: Int): Boolean =
    !layout.isEmpty &&
      layout.scripts.map(_.callCount).sum == totalStatements &&
      layout.scripts.forall(_.callCount > 0)

  def topLevelStatements(expression: BeExpression): List[BeExpression] =
    SnapControlFlow.topLevelStatements(expression)

  /** Python / SnakeCase name used in printed code and allow-list checks. */
  def pythonName(call: BeFunctionCall): String =
    call.funcDef.functionTypeInfo.displayName
      .getNameIn(AppLanguage.English, NamingStyle.SnakeCase)
      .trim

  /** Snap `<block s>` selector for this call (maps Python snake_case → Snap id). */
  def snapSelectorOf(call: BeFunctionCall): String =
    snapSelectorByPythonName.getOrElse(pythonName(call), pythonName(call))

  /**
   * Accept only allow-listed top-level statements (comments / unsupported rejected).
   * @return Right(statements) in program order, or Left(error)
   */
  def validateSubset(expression: BeExpression): Either[String, List[BeExpression]] =
    SnapControlFlow.validateStatements(topLevelStatements(expression))

  def hasSupportedStatements(expression: BeExpression): Boolean =
    SnapControlFlow.hasSupportedStatements(expression)
}
