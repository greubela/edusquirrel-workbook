package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeFor, BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.code.defining
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog.SnapInputKind

/**
 * BeExpression → Snap/TurtleStitch project XML.
 *
 * Used for legacy Python payload migration and Python-apply writeback.
 * Live Snap edits persist `getProjectXML()` instead of going through this path.
 */
object SnapProjectXml {

  lazy val mini: String = toXml(BeProgram.miniProgram().fullProgram)

  lazy val empty: String = toXml(BeStartProgram(None))

  def toXml(
      expression: BeExpression,
      projectName: String = "fromBeExpression",
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty
  ): String = {
    val scripts = scriptsFromExpression(expression, canvasLayout)
    val xmlScripts = if scripts.nonEmpty then scripts.map(renderScript).mkString else ""
    val globalVariables = renderGlobalVariables(expression)
    val blockDefinitions = renderBlockDefinitions(expression)
    val palette = renderPalette

    s"""<project name="$projectName" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="$projectName"><notes></notes>$palette<hidden></hidden><headers></headers><code></code><blocks>$blockDefinitions</blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts>$xmlScripts</scripts></sprite></sprites></stage><variables>$globalVariables</variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""
  }

  private case class ScriptOut(x: Int, y: Int, statements: List[BeExpression])

  private def scriptsFromExpression(expression: BeExpression, layout: SnapCanvasLayout): List[ScriptOut] = {
    val body = expression match {
      case BeStartProgram(Some(sequence)) => sequence.body
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body
      case other => List(other)
    }

    val statements = SnapTurtlePythonBridge.scriptStatements(body.toList)
    if statements.isEmpty then Nil
    else if layout.isEmpty || !layoutMatches(layout, statements.size) then
      val withGreen =
        if statements.exists(isReceiveGoStatement) then statements
        else createReceiveGoCall() :: statements
      List(ScriptOut(156, 66, withGreen))
    else
      splitByLayout(statements, layout.scripts)
  }

  private def layoutMatches(layout: SnapCanvasLayout, totalStatements: Int): Boolean =
    layout.scripts.map(_.callCount).sum == totalStatements && layout.scripts.forall(_.callCount > 0)

  private def splitByLayout(statements: List[BeExpression], scripts: List[SnapCanvasScript]): List[ScriptOut] = {
    var remaining = statements
    scripts.map { script =>
      val (chunk, rest) = remaining.splitAt(script.callCount)
      remaining = rest
      ScriptOut(script.x, script.y, chunk)
    }
  }

  private def isReceiveGoStatement(expression: BeExpression): Boolean =
    expression match
      case call: BeFunctionCall => SnapTurtlePythonBridge.snapSelectorOf(call) == "receiveGo"
      case _ => false

  private def createReceiveGoCall(): BeFunctionCall = {
    val define = BeDefineFunction(
      inputs = Nil,
      outputs = None,
      body = BeExpression.pass,
      functionTypeInfo = defining.BeDefineFunction.functionInfo(
        BeEntityName.fromUniversalNameInParts("receiveGo")
      )
    )
    BeFunctionCall(define, Map.empty)
  }

  private def collectFunctionDefs(expression: BeExpression): List[BeDefineFunction] =
    SnapTurtlePythonBridge.topLevelStatements(expression).collect { case defn: BeDefineFunction => defn }

  /** Snap Make-a-Block tab name; must be in `<palette>` so loadBlock does not obsolete the call. */
  private val CustomBlockCategory = SnapTurtleCatalog.PaletteTab.Variables.toString
  private val VariablesPaletteColor = "243,118,29,1"

  private def renderPalette: String =
    s"""<palette><category name="$CustomBlockCategory" color="$VariablesPaletteColor"/></palette>"""

  private def renderBlockDefinitions(expression: BeExpression): String =
    collectFunctionDefs(expression).map(renderBlockDefinition).mkString

  private def renderBlockDefinition(defn: BeDefineFunction): String = {
    val spec = SnapInputCodec.escapeXml(SnapTurtlePythonBridge.customBlockSemanticSpecOf(defn))
    val inputs = defn.inputs.map(_ => """<input type="%n"></input>""").mkString
    val body = renderScriptBody(defn.body)
    val scriptXml = if body.nonEmpty then s"<script>$body</script>" else ""
    s"""<block-definition s="$spec" type="command" category="$CustomBlockCategory"><header></header><code></code><translations></translations><inputs>$inputs</inputs>$scriptXml</block-definition>"""
  }

  private def renderScript(script: ScriptOut): String = {
    val blocks = script.statements.map(renderStatement).mkString
    s"""<script x="${script.x}" y="${script.y}">$blocks</script>"""
  }

  private def renderStatement(expression: BeExpression): String = expression match {
    case call: BeFunctionCall => renderCall(call)
    case assign: BeAssignVariable => renderAssignment(assign)
    case ifElse: BeIfElse => renderIfElse(ifElse)
    case whileExpr: BeWhile => renderDoUntil(whileExpr)
    case repeat: BeRepeatNr => renderRepeat(repeat)
    case forExpr: BeFor => renderDoFor(forExpr)
    case _ => ""
  }

  private def renderAssignment(assign: BeAssignVariable): String = {
    val name = SnapInputCodec.escapeXml(SnapControlFlow.variableName(assign.target))
    SnapControlFlow.changeVarAmount(assign) match {
      case Some(amount) =>
        s"""<block s="doChangeVar"><l>$name</l>${renderArgument(amount)}</block>"""
      case None =>
        s"""<block s="doSetVar"><l>$name</l>${renderArgument(assign.value)}</block>"""
    }
  }

  private def renderGlobalVariables(expression: BeExpression): String =
    SnapControlFlow.collectVariableNames(expression).map { name =>
      s"""<variable name="${SnapInputCodec.escapeXml(name)}"></variable>"""
    }.mkString

  private def renderRepeat(repeat: BeRepeatNr): String = {
    val body = renderScriptBody(repeat.body)
    s"""<block s="doRepeat"><l>${repeat.amount}</l><script>$body</script></block>"""
  }

  private def renderDoFor(forExpr: BeFor): String = {
    val name = SnapInputCodec.escapeXml(SnapControlFlow.variableName(forExpr.variable))
    val body = renderScriptBody(forExpr.body)
    s"""<block s="doFor"><l>$name</l>${renderArgument(forExpr.start)}${renderArgument(forExpr.end)}<script>$body</script></block>"""
  }

  private def renderIfElse(ifElse: BeIfElse): String = {
    val condition = renderCondition(ifElse.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None)))
    val thenBody = renderScriptBody(ifElse.thenBody)
    if ifElse.elseBody.body.isEmpty then
      s"""<block s="doIf">$condition<script>$thenBody</script><list></list></block>"""
    else
      val elseBody = renderScriptBody(ifElse.elseBody)
      s"""<block s="doIfElse">$condition<script>$thenBody</script><script>$elseBody</script></block>"""
  }

  private def renderDoUntil(whileExpr: BeWhile): String = {
    val snapCondition = SnapControlFlow.invertCondition(
      whileExpr.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None))
    )
    val condition = renderCondition(snapCondition)
    val body = renderScriptBody(whileExpr.body)
    s"""<block s="doUntil">$condition<script>$body</script></block>"""
  }

  private def renderScriptBody(sequence: BeSequence): String =
    sequence.body.map(renderStatement).mkString

  private def renderCall(call: BeFunctionCall): String = {
    if SnapControlFlow.isOperatorCall(call) then renderOperator(call)
    else
      val selector = SnapTurtlePythonBridge.snapSelectorOf(call)
      val arguments = SnapControlFlow.orderedArgs(call)
      val kinds = SnapTurtleCatalog.inputKindsForSelector(selector)
      val inputXml = arguments.zipWithIndex.map { (argument, index) =>
        renderArgument(argument, kinds.lift(index).getOrElse(SnapInputKind.String))
      }.mkString
      if SnapTurtlePythonBridge.isPrimitiveSelector(selector) then
        s"<block s=\"$selector\">$inputXml</block>"
      else
        val spec = SnapInputCodec.escapeXml(SnapTurtlePythonBridge.customBlockSpecOf(call))
        s"""<custom-block s="$spec">$inputXml</custom-block>"""
  }

  private def renderArgument(argument: BeExpression, kind: SnapInputKind = SnapInputKind.String): String = argument match {
    case BeUseValue(BeDataValueLiteral(value), _) => SnapInputCodec.renderLiteral(kind, value)
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case sequence: BeSequence => s"<script>${renderScriptBody(sequence)}</script>"
    case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l></l>"
  }

  private def renderCondition(expression: BeExpression): String = expression match {
    case BeUseValue(BeDataValueLiteral("True"), _) => """<block s="reportTrue"></block>"""
    case BeUseValue(BeDataValueLiteral("False"), _) => """<block s="reportFalse"></block>"""
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => """<block s="reportTrue"></block>"""
  }

  private def renderOperator(call: BeFunctionCall): String = {
    val op = SnapControlFlow.operatorSymbol(call)
    val args = SnapControlFlow.orderedArgs(call)
    op match {
      case "not" =>
        val inner = args.headOption.map(renderCondition).getOrElse("""<block s="reportTrue"></block>""")
        s"""<block s="reportNot">$inner</block>"""
      case sym if SnapControlFlow.OperatorToSnapReporter.contains(sym) =>
        val reporter = SnapControlFlow.OperatorToSnapReporter(sym)
        val items = args.map(renderConditionValue).mkString
        reporter.kind match
          case SnapControlFlow.SnapReporterKind.Variadic =>
            s"""<block s="${reporter.selector}"><list>$items</list></block>"""
          case SnapControlFlow.SnapReporterKind.Unary =>
            s"""<block s="${reporter.selector}">${args.headOption.map(renderCondition).getOrElse("")}</block>"""
          case SnapControlFlow.SnapReporterKind.Binary | SnapControlFlow.SnapReporterKind.Literal =>
            s"""<block s="${reporter.selector}">$items</block>"""
      case _ =>
        val selector = SnapTurtlePythonBridge.snapSelectorOf(call)
        val inputXml = args.map(arg => renderArgument(arg)).mkString
        s"<block s=\"$selector\">$inputXml</block>"
    }
  }

  private def renderConditionValue(expression: BeExpression): String = expression match {
    case BeUseValue(BeDataValueLiteral(value), _) => s"<l>${SnapInputCodec.escapeXml(value)}</l>"
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l>0</l>"
  }

  private def renderVariableReporter(variable: BeDefineVariable): String =
    s"""<block var="${SnapInputCodec.escapeXml(SnapControlFlow.variableName(variable))}"/>"""
}
