package it.evadid.vm.conversion.snapexpression

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog

import it.evadid.vm.conversion.expressionsnap.SnapProjectXml

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
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
object SnapExpressionSupport {

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

  val AllowedSnapSelectors: Set[String] =
    SnapTurtleCatalog.AllowedSnapSelectors ++
      SnapControlFlow.ControlSelectors ++
      SnapControlFlow.VariableSelectors ++
      SnapControlFlow.ConditionSelectors ++
      SnapControlFlow.ArithmeticSelectors

  /** Require whitespace after `block` so `<block-definition>` is not treated as a selector. */
  private val BlockSelectorPattern = """<block\s[^>]*\bs="([^"]+)"""".r
  private val CustomBlockPattern = """<custom-block\b[^>]*\bs="([^"]+)"""".r
  private val BlockDefinitionPattern = """<block-definition\b[^>]*\bs="([^"]+)"""".r

  def isScriptStatement(expression: BeExpression): Boolean =
    expression match
      case _: BeDefineFunction => false
      // The green-flag hat is Snap execution metadata, not Python source.
      case call: BeFunctionCall => snapSelectorOf(call) != "receiveGo"
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

  /**
   * Everything in `xml` that Python cannot represent, named so the message is actionable.
   *
   * Besides unknown primitive selectors this covers custom blocks: reporters, predicates,
   * hats, sprite-local definitions and script/upvar/variadic slots have no Python form,
   * and a call without a matching definition is already an `Undefined!` block in Snap.
   */
  def unsupportedSnapSelectors(xml: String): List[String] = {
    val fromBlocks =
      BlockSelectorPattern.findAllMatchIn(xml).map(_.group(1)).toList.filterNot(AllowedSnapSelectors.contains)
    val definitionProblems = SnapCustomBlockRules.allDefinitions(xml).flatMap(_.pythonIncompatibility)
    val undefinedCalls =
      SnapCustomBlockRules.obsoleteCalls(xml).map(call => s"'${call.spec}' has no matching block definition")
    (fromBlocks ++ definitionProblems ++ undefinedCalls).distinct
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
    SnapTurtleCatalog.snapSelectorByPythonName.getOrElse(pythonName(call), pythonName(call))

  /** Definition spec for a block this module creates from a Python `def`. */
  def customBlockSemanticSpecOf(defn: BeDefineFunction): String =
    SnapTurtleCatalog.customBlockSemanticSpec(
      pythonNameOf(defn),
      defn.inputs.map(SnapControlFlow.variableName)
    )

  /**
   * Python names that two `def`s would share once mapped onto Snap block specs.
   *
   * Snap resolves calls by spec, so two definitions with the same name would collapse
   * into one block and silently swallow the other's body.
   */
  def duplicateFunctionNames(expression: BeExpression): List[String] =
    SnapProjectXml
      .collectFunctionDefs(expression)
      .map(pythonNameOf)
      .groupBy(identity)
      .collect { case (name, occurrences) if occurrences.size > 1 => name }
      .toList
      .sorted

  /**
   * Accept only allow-listed top-level statements (comments / unsupported rejected).
   * @return Right(statements) in program order, or Left(error)
   */
  def validateSubset(expression: BeExpression): Either[String, List[BeExpression]] =
    duplicateFunctionNames(expression) match
      case Nil =>
        SnapControlFlow.validateStatements(topLevelStatements(expression), collectUserFunctionArities(expression))
      case names =>
        Left(s"Two block definitions would share the name(s): ${names.mkString(", ")}")

  def hasSupportedStatements(expression: BeExpression): Boolean =
    SnapControlFlow.hasSupportedStatements(expression, collectUserFunctionArities(expression))
}
