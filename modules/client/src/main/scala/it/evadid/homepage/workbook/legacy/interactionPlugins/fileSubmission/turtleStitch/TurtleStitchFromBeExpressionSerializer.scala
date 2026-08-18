package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.code.defining
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataValueLiteral, BeUseValueReference}
import it.evadid.workbook.elements.interactionElements.programming.{
  SnapCanvasLayout,
  SnapCanvasScript,
  SnapControlFlow,
  SnapTurtlePythonBridge
}

object TurtleStitchFromBeExpressionSerializer {

  private val OperatorToSnapReporter: Map[String, String] = Map(
    "<" -> "reportVariadicLessThan",
    ">" -> "reportVariadicGreaterThan",
    "==" -> "reportVariadicEquals",
    "and" -> "reportVariadicAnd",
    "or" -> "reportVariadicOr",
    "not" -> "reportNot"
  )

  def toXml(
      expression: BeExpression,
      projectName: String = "fromBeExpression",
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty
  ): String = {
    val scripts = scriptsFromExpression(expression, canvasLayout)
    val xmlScripts = if (scripts.nonEmpty) scripts.map(renderScript).mkString else ""
    val globalVariables = renderGlobalVariables(expression)

    s"""<project name="$projectName" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="$projectName"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts>$xmlScripts</scripts></sprite></sprites></stage><variables>$globalVariables</variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""
  }

  private case class ScriptOut(x: Int, y: Int, statements: List[BeExpression])

  private def scriptsFromExpression(expression: BeExpression, layout: SnapCanvasLayout): List[ScriptOut] = {
    val body = expression match {
      case BeStartProgram(Some(sequence)) => sequence.body
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body
      case other => List(other)
    }

    val statements = body.toList
    if (statements.isEmpty) Nil
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
    case _ => ""
  }

  private def renderAssignment(assign: BeAssignVariable): String = {
    val name = escape(SnapControlFlow.variableName(assign.target))
    SnapControlFlow.changeVarAmount(assign) match {
      case Some(amount) =>
        s"""<block s="doChangeVar"><l>$name</l>${renderArgument(amount)}</block>"""
      case None =>
        s"""<block s="doSetVar"><l>$name</l>${renderArgument(assign.value)}</block>"""
    }
  }

  private def renderGlobalVariables(expression: BeExpression): String =
    SnapControlFlow.collectVariableNames(expression).map { name =>
      s"""<variable name="${escape(name)}"></variable>"""
    }.mkString

  private def renderRepeat(repeat: BeRepeatNr): String = {
    val body = renderScriptBody(repeat.body)
    s"""<block s="doRepeat"><l>${repeat.amount}</l><script>$body</script></block>"""
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
    val selector = SnapTurtlePythonBridge.snapSelectorOf(call)
    val arguments = SnapControlFlow.orderedArgs(call)
    val inputXml = arguments.map(renderArgument).mkString
    s"<block s=\"$selector\">$inputXml</block>"
  }

  private def renderArgument(argument: BeExpression): String = argument match {
    case BeUseValue(BeDataValueLiteral(value), _) => s"<l>${escape(value)}</l>"
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case sequence: BeSequence => s"<script>${renderScriptBody(sequence)}</script>"
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l></l>"
  }

  private def renderCondition(expression: BeExpression): String = expression match {
    case BeUseValue(BeDataValueLiteral("True"), _) => """<block s="reportTrue"></block>"""
    case BeUseValue(BeDataValueLiteral("False"), _) => """<block s="reportFalse"></block>"""
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if isOperatorCall(call) => renderOperatorCondition(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => """<block s="reportTrue"></block>"""
  }

  private def renderOperatorCondition(call: BeFunctionCall): String = {
    val op = SnapControlFlow.operatorSymbol(call)
    val args = SnapControlFlow.orderedArgs(call)
    op match {
      case "not" =>
        val inner = args.headOption.map(renderCondition).getOrElse("""<block s="reportTrue"></block>""")
        s"""<block s="reportNot">$inner</block>"""
      case sym if OperatorToSnapReporter.contains(sym) =>
        val reporter = OperatorToSnapReporter(sym)
        val items = args.map(renderConditionValue).mkString
        s"""<block s="$reporter"><list>$items</list></block>"""
      case _ =>
        renderCall(call)
    }
  }

  private def renderConditionValue(expression: BeExpression): String = expression match {
    case BeUseValue(BeDataValueLiteral(value), _) => s"<l>${escape(value)}</l>"
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l>0</l>"
  }

  private def renderVariableReporter(variable: BeDefineVariable): String =
    s"""<block var="${escape(SnapControlFlow.variableName(variable))}"/>"""

  private def isOperatorCall(call: BeFunctionCall): Boolean =
    call.funcDef.functionTypeInfo.funcType match
      case BeDefineFunction.Operator(_) => true
      case _ => false

  private def escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
