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
import it.evadid.workbook.elements.interactionElements.programming.SnapCustomBlockMerge.{CustomBlockPlan, CustomBlockPlans}
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog.SnapInputKind

/**
 * BeExpression → Snap/TurtleStitch project XML.
 *
 * Used for legacy Python payload migration and Python-apply writeback.
 * Live Snap edits persist `getProjectXML()` instead of going through this path.
 *
 * Custom blocks are merged rather than regenerated: pass the XML being replaced as
 * `previousXml` and [[SnapCustomBlockMerge]] keeps each existing definition's label,
 * slot types and category. Calls take their `s` from the definition that will be
 * emitted, which is the only way Snap resolves them (see [[SnapCustomBlockRules]]).
 */
object SnapProjectXml {

  lazy val mini: String = toXml(BeProgram.miniProgram().fullProgram)

  lazy val empty: String = toXml(BeStartProgram(None))

  def toXml(
      expression: BeExpression,
      projectName: String = "fromBeExpression",
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty,
      previousXml: String = "",
      newBlockCategory: String = SnapCustomBlockMerge.DefaultNewBlockCategory
  ): String = {
    val functionDefs = collectFunctionDefs(expression)
    val plans = SnapCustomBlockMerge.planFor(functionDefs, previousXml, newBlockCategory)
    given RenderContext = RenderContext(plans)
    val scripts = scriptsFromExpression(expression, canvasLayout)
    val xmlScripts = if scripts.nonEmpty then scripts.map(renderScript).mkString else ""
    val globalVariables = renderGlobalVariables(expression, functionDefs)
    val blockDefinitions = renderBlockDefinitions(functionDefs, plans)
    val palette = renderPalette(plans, previousXml)

    s"""<project name="$projectName" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="$projectName"><notes></notes>$palette<hidden></hidden><headers></headers><code></code><blocks>$blockDefinitions</blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts>$xmlScripts</scripts></sprite></sprites></stage><variables>$globalVariables</variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""
  }

  /**
   * Rendering state: which definitions will exist, and — while rendering a definition
   * body — how that definition names its slots.
   */
  private final case class RenderContext(
      plans: CustomBlockPlans,
      parameterSlots: Map[String, String] = Map.empty
  ) {
    def forDefinition(plan: CustomBlockPlan): RenderContext = copy(parameterSlots = plan.parameterSlots)

    /** Python parameter names must be written as the slot names of their definition. */
    def slotName(variableName: String): String = parameterSlots.getOrElse(variableName, variableName)
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

  /**
   * Every `def` in the program, nested ones hoisted.
   *
   * Snap has no nested definitions, so a `def` inside a loop or another `def` still
   * has to become a top-level `<block-definition>`; otherwise its calls would load
   * as `Undefined!`.
   */
  def collectFunctionDefs(expression: BeExpression): List[BeDefineFunction] = {
    val found = List.newBuilder[BeDefineFunction]
    def walk(node: BeExpression): Unit = node match
      case defn: BeDefineFunction =>
        found += defn
        walk(defn.body)
      case seq: BeSequence => seq.body.foreach(walk)
      case BeStartProgram(Some(seq)) => seq.body.foreach(walk)
      case ifElse: BeIfElse =>
        ifElse.thenBody.body.foreach(walk)
        ifElse.elseBody.body.foreach(walk)
      case whileExpr: BeWhile => whileExpr.body.body.foreach(walk)
      case repeat: BeRepeatNr => repeat.body.body.foreach(walk)
      case forExpr: BeFor => forExpr.body.body.foreach(walk)
      case _ => ()
    walk(expression)
    found.result()
  }

  /**
   * `<palette>` must list every category the definitions use, or `loadCustomBlocks`
   * downgrades them to `other` and they vanish from their palette tab. Categories the
   * previous XML registered are kept so a reload does not drop the exercise tabs.
   */
  private def renderPalette(plans: CustomBlockPlans, previousXml: String): String = {
    val previous = SnapXmlParser
      .elements(previousXml, "palette")
      .flatMap(palette => SnapXmlParser.children(palette.inner).filter(_.tag == "category"))
    val previousByName = previous.flatMap(node => node.attr("name").map(_ -> node)).toMap
    val names = (previous.flatMap(_.attr("name")) ++ plans.categories).distinct
      .filterNot(SnapCustomBlockRules.BuiltInCategories.contains)
    if names.isEmpty then ""
    else
      val categories = names.map { name =>
        val color = previousByName.get(name).flatMap(_.attr("color")).getOrElse(DefaultPaletteColor)
        s"""<category name="${SnapInputCodec.escapeXml(name)}" color="${SnapInputCodec.escapeXml(color)}"/>"""
      }
      categories.mkString("<palette>", "", "</palette>")
  }

  /** Snap's `variables` palette color, used for categories we invent. */
  private val DefaultPaletteColor = "243,118,29,1"

  private def renderBlockDefinitions(
      functionDefs: List[BeDefineFunction],
      plans: CustomBlockPlans
  )(using context: RenderContext): String =
    functionDefs
      .flatMap(defn => plans.get(SnapTurtlePythonBridge.pythonNameOf(defn)).map(defn -> _))
      .map { (defn, plan) =>
        plan.toXml(renderScriptBody(defn.body)(using context.forDefinition(plan)))
      }
      .mkString

  private def renderScript(script: ScriptOut)(using RenderContext): String = {
    val blocks = script.statements.map(renderStatement).mkString
    s"""<script x="${script.x}" y="${script.y}">$blocks</script>"""
  }

  private def renderStatement(expression: BeExpression)(using RenderContext): String = expression match {
    case call: BeFunctionCall => renderCall(call)
    case assign: BeAssignVariable => renderAssignment(assign)
    case ifElse: BeIfElse => renderIfElse(ifElse)
    case whileExpr: BeWhile => renderDoUntil(whileExpr)
    case repeat: BeRepeatNr => renderRepeat(repeat)
    case forExpr: BeFor => renderDoFor(forExpr)
    case _ => ""
  }

  private def renderAssignment(assign: BeAssignVariable)(using RenderContext): String = {
    val name = variableXmlName(assign.target)
    SnapControlFlow.changeVarAmount(assign) match {
      case Some(amount) =>
        s"""<block s="doChangeVar"><l>$name</l>${renderArgument(amount)}</block>"""
      case None =>
        s"""<block s="doSetVar"><l>$name</l>${renderArgument(assign.value)}</block>"""
    }
  }

  /**
   * Scene-level variables. Function parameters are excluded: they live in the
   * definition's own scope, and declaring them globally would shadow that.
   */
  private def renderGlobalVariables(expression: BeExpression, functionDefs: List[BeDefineFunction]): String = {
    val parameterNames = functionDefs.flatMap(_.inputs.map(SnapControlFlow.variableName)).toSet
    SnapControlFlow
      .declaredVariables(expression)
      .filterNot((name, _) => parameterNames.contains(name))
      .map { (name, literal) =>
        val valueXml = literal match
          case Some(value) => SnapInputCodec.renderLiteral(SnapInputKind.String, value)
          case None => "<l>0</l>"
        s"""<variable name="${SnapInputCodec.escapeXml(name)}">$valueXml</variable>"""
      }
      .mkString
  }

  private def renderRepeat(repeat: BeRepeatNr)(using RenderContext): String = {
    val body = renderScriptBody(repeat.body)
    s"""<block s="doRepeat"><l>${repeat.amount}</l><script>$body</script></block>"""
  }

  private def renderDoFor(forExpr: BeFor)(using RenderContext): String = {
    val name = variableXmlName(forExpr.variable)
    val body = renderScriptBody(forExpr.body)
    s"""<block s="doFor"><l>$name</l>${renderArgument(forExpr.start)}${renderArgument(forExpr.end)}<script>$body</script></block>"""
  }

  private def renderIfElse(ifElse: BeIfElse)(using RenderContext): String = {
    val condition = renderCondition(ifElse.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None)))
    val thenBody = renderScriptBody(ifElse.thenBody)
    if ifElse.elseBody.body.isEmpty then
      s"""<block s="doIf">$condition<script>$thenBody</script><list></list></block>"""
    else
      val elseBody = renderScriptBody(ifElse.elseBody)
      s"""<block s="doIfElse">$condition<script>$thenBody</script><script>$elseBody</script></block>"""
  }

  private def renderDoUntil(whileExpr: BeWhile)(using RenderContext): String = {
    val snapCondition = SnapControlFlow.invertCondition(
      whileExpr.condition.body.headOption.getOrElse(BeUseValue(BeDataValueLiteral("True"), None))
    )
    val condition = renderCondition(snapCondition)
    val body = renderScriptBody(whileExpr.body)
    s"""<block s="doUntil">$condition<script>$body</script></block>"""
  }

  private def renderScriptBody(sequence: BeSequence)(using RenderContext): String =
    sequence.body.map(renderStatement).mkString

  private def renderCall(call: BeFunctionCall)(using context: RenderContext): String = {
    if SnapControlFlow.isOperatorCall(call) then renderOperator(call)
    else
      val selector = SnapTurtlePythonBridge.snapSelectorOf(call)
      val arguments = SnapControlFlow.orderedArgs(call)
      val kinds = SnapTurtleCatalog.inputKindsForSelector(selector)
      if SnapTurtlePythonBridge.isPrimitiveSelector(selector) then
        val inputXml = arguments.zipWithIndex.map { (argument, index) =>
          renderArgument(argument, kinds.lift(index).getOrElse(SnapInputKind.String))
        }.mkString
        s"<block s=\"$selector\">$inputXml</block>"
      else
        val pythonName = SnapTurtlePythonBridge.pythonName(call)
        val plan = context.plans.get(pythonName)
        val arity = plan.map(_.slotNames.size).getOrElse(arguments.size)
        val aligned = arguments.take(arity) ++ List.fill((arity - arguments.size).max(0))(
          BeUseValue(BeDataValueLiteral(""), None)
        )
        val inputXml = aligned.zipWithIndex.map { (argument, index) =>
          renderArgument(argument, kinds.lift(index).getOrElse(SnapInputKind.String))
        }.mkString
        val spec = plan
          .map(_.callSpec)
          .getOrElse(SnapCustomBlockRules.blockSpecOfNewDefinition(
            SnapTurtleCatalog.customBlockSemanticSpec(pythonName, arguments.indices.toList.map(index => s"arg$index"))
          ))
        s"""<custom-block s="${SnapInputCodec.escapeXml(spec)}">$inputXml</custom-block>"""
  }

  private def renderArgument(argument: BeExpression, kind: SnapInputKind = SnapInputKind.String)(using
      RenderContext
  ): String = argument match {
    case BeUseValue(BeDataValueLiteral(value), _) => SnapInputCodec.renderLiteral(kind, value)
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case sequence: BeSequence => s"<script>${renderScriptBody(sequence)}</script>"
    case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l></l>"
  }

  private def renderCondition(expression: BeExpression)(using RenderContext): String = expression match {
    case BeUseValue(BeDataValueLiteral("True"), _) => """<block s="reportTrue"></block>"""
    case BeUseValue(BeDataValueLiteral("False"), _) => """<block s="reportFalse"></block>"""
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => """<block s="reportTrue"></block>"""
  }

  private def renderOperator(call: BeFunctionCall)(using RenderContext): String = {
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

  private def renderConditionValue(expression: BeExpression)(using RenderContext): String = expression match {
    case BeUseValue(BeDataValueLiteral(value), _) => s"<l>${SnapInputCodec.escapeXml(value)}</l>"
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if SnapControlFlow.isOperatorCall(call) => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l>0</l>"
  }

  private def renderVariableReporter(variable: BeDefineVariable)(using RenderContext): String =
    s"""<block var="${variableXmlName(variable)}"/>"""

  private def variableXmlName(variable: BeDefineVariable)(using context: RenderContext): String =
    SnapInputCodec.escapeXml(context.slotName(SnapControlFlow.variableName(variable)))
}
