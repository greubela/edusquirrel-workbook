package it.evadid.vm.conversion.expressionsnap

import it.evadid.vm.conversion.snapxml.SnapXmlText

import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasLayout
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapCanvasScript
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapModuleBlocks
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary

import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapCustomBlockRules
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.snapexpression.SnapInputCodec

import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.*
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeUseValueReference, StringLiteral}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtleCatalog.SnapInputKind
import it.evadid.vm.conversion.expressionsnap.SnapCustomBlockMerge.{CustomBlockPlan, CustomBlockPlans}

/**
 * BeExpression → Snap/TurtleStitch project XML.
 *
 * Used for Python-to-Snap conversion and Python-apply writeback.
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
      newBlockCategory: SnapVocabulary.BlockCategory = SnapCustomBlockMerge.DefaultNewBlockCategory
  ): String = {
    val functionDefs = collectFunctionDefs(expression)
    val plans = SnapCustomBlockMerge.planFor(functionDefs, previousXml, newBlockCategory)
    given RenderContext = RenderContext(plans)
    val scripts = scriptsFromExpression(expression, canvasLayout)
    val xmlScripts = if scripts.nonEmpty then scripts.map(renderScript).mkString else ""
    val moduleBlocks = SnapModuleBlocks.requiredBy(expression)
    val globalVariables = renderGlobalVariables(expression, functionDefs) + SnapModuleBlocks.variableXml(moduleBlocks)
    val blockDefinitions = renderBlockDefinitions(functionDefs, plans) + moduleBlocks.map(_.definitionXml).mkString
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

    val detached = body.collect { case function: BeDefineFunction if BeProgram.isUnusedCode(function) => function.body.body }.flatten.toList
    val statements = SnapExpressionSupport.scriptStatements(body.toList)
    if detached.nonEmpty then scriptsWithDetached(statements, detached, layout.scripts.toList)
    // The green flag always marks the main program, even when it has no statements yet.
    else if statements.isEmpty then List(ScriptOut(156, 66, List(createReceiveGoCall())))
    else if layout.isEmpty || !layoutMatches(layout, statements.size) then
      List(ScriptOut(156, 66, createReceiveGoCall() :: statements))
    else
      splitByLayout(statements, layout.scripts.toList)
  }

  /**
   * The green-flag script plus the detached `unusedCode` statements. A saved layout places both when its
   * green-flag and detached call counts match; otherwise the detached script goes below the main one.
   */
  private def scriptsWithDetached(
      statements: List[BeExpression],
      detached: List[BeExpression],
      knownLayout: List[SnapCanvasScript]
  ): List[ScriptOut] =
    if knownLayout.filter(_.hasReceiveGo).map(_.callCount).sum == statements.size &&
        knownLayout.filterNot(_.hasReceiveGo).map(_.callCount).sum == detached.size && knownLayout.nonEmpty then {
      val (scripts, _, _) = knownLayout.foldLeft((List.empty[ScriptOut], statements, detached)) {
        case ((scripts, startup, loose), script) =>
          val (chunk, rest) = (if script.hasReceiveGo then startup else loose).splitAt(script.callCount)
          val rendered = ScriptOut(script.x, script.y, if script.hasReceiveGo then createReceiveGoCall() :: chunk else chunk)
          (scripts :+ rendered, if script.hasReceiveGo then rest else startup, if script.hasReceiveGo then loose else rest)
      }
      scripts
    } else List(ScriptOut(156, 66, createReceiveGoCall() :: statements), ScriptOut(156, 240, detached))

  private def layoutMatches(layout: SnapCanvasLayout, totalStatements: Int): Boolean =
    layout.scripts.map(_.callCount).sum == totalStatements && layout.scripts.forall(_.callCount > 0)

  private def splitByLayout(statements: List[BeExpression], scripts: List[SnapCanvasScript]): List[ScriptOut] = {
    val (rendered, _) = scripts.foldLeft((List.empty[ScriptOut], statements)) { case ((done, remaining), script) =>
      val (chunk, rest) = remaining.splitAt(script.callCount)
      val scriptStatements =
        if script.hasReceiveGo then createReceiveGoCall() :: chunk
        else chunk
      (done :+ ScriptOut(script.x, script.y, scriptStatements), rest)
    }
    rendered
  }

  private def createReceiveGoCall(): BeFunctionCall = {
    val define = BeDefineFunction(
      inputs = Nil,
      outputs = None,
      body = BeExpression.pass,
      functionTypeInfo = BeDefineFunction.functionInfo(
        BeEntityName.fromUniversalNameInParts("receiveGo")
      )
    )
    BeFunctionCall.fromDefinition(define, Map.empty)
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
        if !BeProgram.isUnusedCode(defn) then found += defn
        walk(defn.body)
      case seq: BeSequence => seq.body.foreach(walk)
      case BeStartProgram(Some(seq)) => seq.body.foreach(walk)
      case ifElse: BeIfElse =>
        ifElse.thenBody.body.foreach(walk)
        ifElse.elseBody.body.foreach(walk)
      case whileExpr: BeWhile => whileExpr.body.body.foreach(walk)
      case repeat: BeRepeatNr =>
        walk(repeat.amount)
        repeat.body.body.foreach(walk)
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
    val previous = SnapCustomBlockRules.paletteEntries(previousXml)
    val previousByName = previous.flatMap(node => node.attribute("name").map(_ -> node)).toMap
    val names = (previous.flatMap(_.attribute("name")) ++ plans.categories.map(_.xml)).distinct
      .filterNot(SnapCustomBlockRules.BuiltInCategories.contains)
    if names.isEmpty then ""
    else
      val categories = names.map { name =>
        val color = previousByName.get(name).flatMap(_.attribute("color")).getOrElse(DefaultPaletteColor)
        s"""<category name="${SnapXmlText.escape(name)}" color="${SnapXmlText.escape(color)}"/>"""
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
      .flatMap(defn => plans.get(SnapExpressionSupport.pythonNameOf(defn)).map(defn -> _))
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
    case variable: BeDefineVariable => variable.initValue.map(value => renderAssignment(BeAssignVariable(variable, value))).getOrElse("")
    case ifElse: BeIfElse => renderIfElse(ifElse)
    case whileExpr: BeWhile => renderDoUntil(whileExpr)
    case repeat: BeRepeatNr => renderRepeat(repeat)
    case forExpr: BeFor => renderDoFor(forExpr)
    case BeReturn(None) => """<block s="doStopThis"><l><option>this block</option></l></block>"""
    case BeReturn(Some(value)) => s"""<block s="doReport">${renderReported(value)}</block>"""
    case _ => ""
  }

  /** A reported truth value is a predicate input; any other value an ordinary reporter input. */
  private def renderReported(value: BeExpression)(using RenderContext): String = value match {
    case BeUseValue(BeDataValueLiteral("True" | "False"), _) => renderCondition(value)
    case call: BeFunctionCall if call.isOperator && value.staticInformationExpression.staticType == BeDataType.Boolean =>
      renderCondition(value)
    case _ => renderArgument(value)
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
      // Snap lists project variables by name; a fixed order keeps exports stable.
      .sortBy(_._1)
      .map { (name, literal) =>
        val valueXml = literal match
          case Some(value) => SnapInputCodec.renderLiteral(SnapInputKind.String, value)
          case None => "<l>0</l>"
        s"""<variable name="${SnapXmlText.escape(name)}">$valueXml</variable>"""
      }
      .mkString
  }

  private def renderRepeat(repeat: BeRepeatNr)(using RenderContext): String = {
    val body = renderScriptBody(repeat.body)
    s"""<block s="doRepeat">${renderArgument(repeat.amount)}<script>$body</script></block>"""
  }

  /** Snap's `for` counts either way. A one-way loop (Python `range`, Java `for`) with computed bounds
    * runs only when its bounds point its way, so it is guarded by that comparison.
    */
  private def renderDoFor(forExpr: BeFor)(using RenderContext): String = {
    val name = variableXmlName(forExpr.variable)
    val body = renderScriptBody(forExpr.body)
    val loop = s"""<block s="doFor"><l>$name</l>${renderArgument(forExpr.start)}${renderArgument(forExpr.end)}<script>$body</script></block>"""
    def literal(value: BeExpression) = value match {
      case BeUseValue(BeDataValueLiteral(raw), _) => raw.trim.toDoubleOption
      case _ => None
    }
    val comparison = forExpr.direction match {
      case BeFor.Direction.Up => Some("<=")
      case BeFor.Direction.Down => Some(">=")
      case BeFor.Direction.Either => None
    }
    (comparison, literal(forExpr.start).zip(literal(forExpr.end))) match {
      case (None, _) => loop
      case (Some(operator), Some((start, end))) =>
        if (operator == "<=" && start <= end) || (operator == ">=" && start >= end) then loop else ""
      case (Some(operator), None) =>
        val guard = SnapControlFlow.operatorCall(operator, List(forExpr.start, forExpr.end))
        s"""<block s="doIf">${renderCondition(guard)}<script>$loop</script><list></list></block>"""
    }
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
    SnapExpressionSupport.scriptStatements(sequence.body.toList).map(renderStatement).mkString

  private def renderCall(call: BeFunctionCall)(using context: RenderContext): String = {
    if call.isOperator then renderOperator(call)
    else SnapModuleBlocks.of(call.funcDef) match
      case Some(block) =>
        val inputXml = call.arguments.map(renderArgument(_, SnapInputKind.Numeric)).mkString
        s"""<custom-block s="${SnapXmlText.escape(block.blockSpec)}">$inputXml</custom-block>"""
      case None => renderOrdinaryCall(call)
  }

  private def renderOrdinaryCall(call: BeFunctionCall)(using context: RenderContext): String = {
    val selector = SnapExpressionSupport.snapSelectorOf(call)
    val arguments = call.arguments
    val kinds = SnapTurtleCatalog.inputKindsForSelector(selector)
    if SnapExpressionSupport.isPrimitiveSelector(selector) &&
        (TurtleCommand.isTurtleMethod(call.funcDef) ||
          context.plans.get(SnapExpressionSupport.pythonName(call)).isEmpty) then
      s"<block s=\"$selector\">${renderInputs(arguments, kinds)}</block>"
    else
      val pythonName = SnapExpressionSupport.pythonName(call)
      val plan = context.plans.get(pythonName)
      val arity = plan.map(_.slotNames.size).getOrElse(arguments.size)
      val aligned = arguments.take(arity) ++ List.fill((arity - arguments.size).max(0))(
        BeUseValue(BeDataValueLiteral(""), None)
      )
      val inputXml = renderInputs(aligned, kinds)
      val spec = plan
        .map(_.callSpec)
        .getOrElse(SnapCustomBlockRules.blockSpecOfNewDefinition(
          SnapTurtleCatalog.customBlockSemanticSpec(pythonName, arguments.indices.toList.map(index => s"arg$index"))
        ))
      s"""<custom-block s="${SnapXmlText.escape(spec)}">$inputXml</custom-block>"""
  }

  /** Arguments in their slots' kinds; slots the catalog does not know take text. */
  private def renderInputs(arguments: List[BeExpression], kinds: Seq[SnapInputKind])(using RenderContext): String =
    arguments.zipWithIndex.map { (argument, index) =>
      renderArgument(argument, kinds.lift(index).getOrElse(SnapInputKind.String))
    }.mkString

  private def renderArgument(argument: BeExpression, kind: SnapInputKind = SnapInputKind.String)(using
      RenderContext
  ): String = argument match {
    // A truth value outside a boolean slot is Snap's true/false reporter, not the text "True".
    case BeUseValue(BeDataValueLiteral("True" | "False"), _) if kind != SnapInputKind.Bool => renderCondition(argument)
    case BeUseValue(BeDataValueLiteral(value), _) => SnapInputCodec.renderLiteral(kind, value)
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case sequence: BeSequence => s"<script>${renderScriptBody(sequence)}</script>"
    case call: BeFunctionCall if SnapControlFlow.isToText(call) => renderJoin(call.arguments)
    case call: BeFunctionCall if SnapControlFlow.isMathReporter(call) => renderMath(call)
    case call: BeFunctionCall if call.isOperator => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l></l>"
  }

  private def renderCondition(expression: BeExpression)(using RenderContext): String = expression match {
    case BeUseValue(BeDataValueLiteral("True"), _) => """<block s="reportTrue"></block>"""
    case BeUseValue(BeDataValueLiteral("False"), _) => """<block s="reportFalse"></block>"""
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if call.isOperator => renderOperator(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => """<block s="reportTrue"></block>"""
  }

  /** `sqrt of value`, or `pick random` over [0, 1) for `random()`. */
  private def renderMath(call: BeFunctionCall)(using RenderContext): String =
    if call.funcDef.intrinsic.contains(BeIntrinsic.Random) then
      s"""<block s="reportRandom"><l>0</l><l>${SnapControlFlow.RandomUpperBound}</l></block>"""
    else s"""<block s="reportMonadic"><l><option>sqrt</option></l>${call.arguments.map(renderConditionValue).mkString}</block>"""

  /** Snap's `join`, which converts its items to text; `str(value)` items are their value. */
  private def renderJoin(items: List[BeExpression])(using RenderContext): String =
    s"""<block s="reportJoinWords"><list>${items.map {
      case call: BeFunctionCall if SnapControlFlow.isToText(call) => call.arguments.map(renderConditionValue).mkString
      case item => renderConditionValue(item)
    }.mkString}</list></block>"""

  private def checkLiteralRemainder(symbol: String, args: List[BeExpression]): Unit = {
    def number(value: BeExpression): Option[Double] = value match {
      case BeUseValue(BeDataValueLiteral(raw), _) => raw.toDoubleOption
      case call: BeFunctionCall if call.isOperator && SnapControlFlow.operatorSymbol(call) == "-" && call.arguments.size == 1 =>
        number(call.arguments.head).map(-_)
      case _ => None
    }
    for left <- args.headOption.flatMap(number); right <- args.lift(1).flatMap(number) do {
      val rest = left % right
      val signed = symbol == "fmod"
      val expected = if signed then rest else if rest == 0 then math.copySign(0.0, right)
        else if (rest < 0) == (right < 0) then rest else rest + right
      def snapModulo(a: Double, b: Double) = ((a % b) + b) % b
      val actual = if signed then math.signum(left) * snapModulo(left.abs, right.abs) else snapModulo(left, right)
      if right == 0 || java.lang.Double.compare(expected, actual) != 0 then
        throw it.evadid.vm.code.errors.ProgrammingConversionException.unsupported(s"Snap cannot represent the remainder of $left and $right exactly.")
    }
  }

  private def renderOperator(call: BeFunctionCall)(using RenderContext): String = {
    val op = SnapControlFlow.operatorSymbol(call)
    val args = call.arguments
    if op == "%" || op == "fmod" then checkLiteralRemainder(op, args)
    op match {
      case "+" if SnapControlFlow.isTextJoin(call) => renderJoin(args)
      case "-" if args.size == 1 =>
        // Snap's difference reporter is binary; unary minus needs an explicit zero.
        s"""<block s="reportDifference"><l>0</l>${renderConditionValue(args.head)}</block>"""
      case "not" =>
        val inner = args.headOption.map(renderCondition).getOrElse("""<block s="reportTrue"></block>""")
        s"""<block s="reportNot">$inner</block>"""
      case "quotient" =>
        s"""<block s="reportQuotient">${args.map(renderConditionValue).mkString}</block>"""
      case "fmod" =>
        val List(left, right) = args: @unchecked
        def monadic(name: String, arg: BeExpression) =
          s"""<block s="reportMonadic"><l><option>$name</option></l>${renderConditionValue(arg)}</block>"""
        val magnitude = s"""<block s="reportModulus">${monadic("abs", left)}${monadic("abs", right)}</block>"""
        s"""<block s="reportVariadicProduct"><list>${monadic("sign", left)}$magnitude</list></block>"""
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
        val selector = SnapExpressionSupport.snapSelectorOf(call)
        val inputXml = args.map(arg => renderArgument(arg)).mkString
        s"<block s=\"$selector\">$inputXml</block>"
    }
  }

  private def renderConditionValue(expression: BeExpression)(using RenderContext): String = expression match {
    case BeUseValue(BeDataValueLiteral("True" | "False"), _) => renderCondition(expression)
    case BeUseValue(BeDataValueLiteral(value), _) => s"<l>${SnapXmlText.escape(StringLiteral.content(value))}</l>"
    case BeUseValue(BeUseValueReference(variable), _) => renderVariableReporter(variable)
    case call: BeFunctionCall if call.isOperator => renderOperator(call)
    case call: BeFunctionCall if SnapControlFlow.isToText(call) => renderJoin(call.arguments)
    case call: BeFunctionCall if SnapControlFlow.isMathReporter(call) => renderMath(call)
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l>0</l>"
  }

  private def renderVariableReporter(variable: BeDefineVariable)(using RenderContext): String =
    s"""<block var="${variableXmlName(variable)}"/>"""

  private def variableXmlName(variable: BeDefineVariable)(using context: RenderContext): String =
    SnapXmlText.escape(context.slotName(SnapControlFlow.variableName(variable)))
}
