package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction
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
  val Primitives: List[TurtlePrimitive] =
    SnapTurtleCatalog.Primitives.map { primitive =>
      TurtlePrimitive(primitive.pythonName, primitive.snapSelector, primitive.example)
    }

  val ControlFlowExamples: List[String] = List(
    "if condition:\n    ...\nelse:\n    ...",
    "while not condition:\n    ...",
    "for _ in range(n):\n    ...",
    "for i in range(start, end + 1):\n    ..."
  )

  val VariableExamples: List[String] = List(
    "steps = x",
    "steps = steps + n",
    "if steps < n:\n    forward(steps)"
  )

  val UserFunctionExamples: List[String] = List(
    "def square(n):\n    forward(n)"
  )

  /** Python / SnakeCase names accepted for seamless block roundtrips, including aliases. */
  val AllowedPythonNames: Set[String] = SnapTurtleCatalog.AllowedPythonNames

  val AllowedSnapSelectors: Set[String] =
    SnapTurtleCatalog.AllowedSnapSelectors ++
      SnapControlFlow.ControlSelectors ++
      SnapControlFlow.VariableSelectors ++
      SnapControlFlow.ConditionSelectors ++
      SnapControlFlow.ArithmeticSelectors

  private val snapSelectorByPythonName: Map[String, String] =
    SnapTurtleCatalog.snapSelectorByPythonName

  /** Require whitespace after `block` so `<block-definition>` is not treated as a selector. */
  private val BlockSelectorPattern = """<block\s[^>]*\bs="([^"]+)"""".r
  private val CustomBlockPattern = """<custom-block\b[^>]*\bs="([^"]+)"""".r
  private val BlockDefinitionPattern = """<block-definition\b[^>]*\bs="([^"]+)"""".r

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
            val layout = reconcileLayout(previousLayout, scriptStatementCount(statements))
            Right(ProgrammingExerciseState.fromProgram(program, layout))
    catch
      case e: Throwable =>
        val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName)
        Left(s"Parse error; keeping existing blocks. ($detail)")

  def printedPython(expression: BeExpression): String =
    expression.structureInfo.toStringInLanguage(Python, English, false)

  def isScriptStatement(expression: BeExpression): Boolean =
    expression match
      case _: BeDefineFunction => false
      case _ => true

  def scriptStatements(expressions: List[BeExpression]): List[BeExpression] =
    expressions.filter(isScriptStatement)

  def scriptStatementCount(expressions: List[BeExpression]): Int =
    scriptStatements(expressions).size

  def collectUserFunctionNames(expression: BeExpression): Set[String] =
    collectUserFunctionArities(expression).keySet

  def collectUserFunctionArities(expression: BeExpression): Map[String, Int] = {
    val arities = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    def walk(node: BeExpression): Unit = node match
      case defn: BeDefineFunction =>
        val name = pythonNameOf(defn)
        if name.nonEmpty then arities += name -> defn.inputs.size
        walk(defn.body)
      case seq: it.evadid.vm.code.controlStructures.BeSequence =>
        seq.body.foreach(walk)
      case start: it.evadid.vm.code.others.BeStartProgram =>
        start.startSequence.foreach(_.body.foreach(walk))
      case ifElse: it.evadid.vm.code.controlStructures.BeIfElse =>
        ifElse.thenBody.body.foreach(walk)
        ifElse.elseBody.body.foreach(walk)
      case whileExpr: it.evadid.vm.code.controlStructures.BeWhile =>
        whileExpr.body.body.foreach(walk)
      case repeat: it.evadid.vm.code.controlStructures.BeRepeatNr =>
        repeat.body.body.foreach(walk)
      case forExpr: it.evadid.vm.code.controlStructures.BeFor =>
        forExpr.body.body.foreach(walk)
      case _ => ()
    walk(expression)
    arities.toMap
  }

  /** Snap selectors in `xml` that are outside the Python-compatible allow-list. */
  def unsupportedSnapSelectors(xml: String): List[String] = {
    val fromBlocks = BlockSelectorPattern.findAllMatchIn(xml).map(_.group(1)).toList
    val definedSpecs = BlockDefinitionPattern.findAllMatchIn(xml).map(_.group(1)).map(SnapTurtleCatalog.typeSpecFromSemantic).toSet
    val customSpecs = CustomBlockPattern.findAllMatchIn(xml).map(_.group(1)).toList
    val unknownCustoms = customSpecs.filterNot { spec =>
      definedSpecs.contains(spec) || definedSpecs.contains(SnapTurtleCatalog.typeSpecFromSemantic(spec))
    }
    (fromBlocks.filterNot(AllowedSnapSelectors.contains) ++ unknownCustoms.map(_ => "custom-block").distinct).distinct
  }

  def isPythonCompatibleXml(xml: String): Boolean =
    unsupportedSnapSelectors(xml).isEmpty

  def isPrimitiveSelector(selector: String): Boolean =
    AllowedSnapSelectors.contains(selector)

  def isOperatorSelector(selector: String): Boolean =
    SnapControlFlow.ConditionSelectors.contains(selector) ||
      SnapControlFlow.ArithmeticSelectors.contains(selector)

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

  def pythonNameOf(defn: BeDefineFunction): String =
    defn.functionTypeInfo.displayName.getNameIn(AppLanguage.English, NamingStyle.SnakeCase).trim

  /** Snap `<block s>` selector for this call (maps Python snake_case → Snap id). */
  def snapSelectorOf(call: BeFunctionCall): String =
    snapSelectorByPythonName.getOrElse(pythonName(call), pythonName(call))

  def customBlockSpecOf(call: BeFunctionCall): String =
    SnapTurtleCatalog.customBlockTypeSpec(pythonName(call), call.funcDef.inputs.size)

  def customBlockSemanticSpecOf(defn: BeDefineFunction): String =
    SnapTurtleCatalog.customBlockSemanticSpec(
      pythonNameOf(defn),
      defn.inputs.map(SnapControlFlow.variableName)
    )

  /**
   * Accept only allow-listed top-level statements (comments / unsupported rejected).
   * @return Right(statements) in program order, or Left(error)
   */
  def validateSubset(expression: BeExpression): Either[String, List[BeExpression]] =
    SnapControlFlow.validateStatements(topLevelStatements(expression), collectUserFunctionArities(expression))

  def hasSupportedStatements(expression: BeExpression): Boolean =
    SnapControlFlow.hasSupportedStatements(expression, collectUserFunctionArities(expression))
}
