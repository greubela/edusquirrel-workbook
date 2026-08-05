package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.code.defining
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.types.BeDataValueLiteral
import it.evadid.workbook.elements.interactionElements.programming.{SnapCanvasLayout, SnapCanvasScript}

object TurtleStitchFromBeExpressionSerializer {

  // Upstream references for XML shape compatibility:
  // - src/store.js: Project/Scene/Stage/Sprite `toXML` methods (~2021/~2048/~2116/~2224)
  // - src/store.js: Script/block serialization (`toScriptXML` / `toBlockXML`, ~2436+)

  def toXml(
      expression: BeExpression,
      projectName: String = "fromBeExpression",
      canvasLayout: SnapCanvasLayout = SnapCanvasLayout.empty
  ): String = {
    val scripts = scriptsFromExpression(expression, canvasLayout)
    val xmlScripts = if (scripts.nonEmpty) scripts.map(renderScript).mkString else ""

    s"""<project name="$projectName" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="$projectName"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts>$xmlScripts</scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""
  }

  private case class ScriptOut(x: Int, y: Int, calls: List[BeFunctionCall])

  private def scriptsFromExpression(expression: BeExpression, layout: SnapCanvasLayout): List[ScriptOut] = {
    val body = expression match {
      case BeStartProgram(Some(sequence)) => sequence.body
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body
      case other => List(other)
    }

    val calls = body.collect { case call: BeFunctionCall => call }.toList
    if (calls.isEmpty) Nil
    else if layout.isEmpty || !layoutMatches(layout, calls.size) then
      // Legacy / inconsistent layout: one script, inject receiveGo if missing.
      val withGreen = if (calls.exists(isReceiveGo)) calls else createReceiveGoCall() :: calls
      List(ScriptOut(156, 66, withGreen))
    else
      splitByLayout(calls, layout.scripts)
  }

  private def layoutMatches(layout: SnapCanvasLayout, totalCalls: Int): Boolean =
    layout.scripts.map(_.callCount).sum == totalCalls && layout.scripts.forall(_.callCount > 0)

  private def splitByLayout(calls: List[BeFunctionCall], scripts: List[SnapCanvasScript]): List[ScriptOut] = {
    var remaining = calls
    scripts.map { script =>
      val (chunk, rest) = remaining.splitAt(script.callCount)
      remaining = rest
      // Do not inject receiveGo onto orphan stacks — only leave calls as parsed.
      ScriptOut(script.x, script.y, chunk)
    }
  }

  private def isReceiveGo(call: BeFunctionCall): Boolean = selectorOf(call) == "receiveGo"

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
    val blocks = script.calls.map(renderCall).mkString
    s"""<script x="${script.x}" y="${script.y}">$blocks</script>"""
  }

  private def renderCall(call: BeFunctionCall): String = {
    val selector = selectorOf(call)
    val arguments = orderedArgs(call)

    val inputXml = arguments.map(renderArgument).mkString
    s"<block s=\"$selector\">$inputXml</block>"
  }

  private def selectorOf(call: BeFunctionCall): String =
    // Snap block selectors are camelCase identifiers (receiveGo, gotoXY, …).
    call.funcDef.functionTypeInfo.displayName.getNameIn(AppLanguage.English, NamingStyle.CamelCase).trim

  private def orderedArgs(call: BeFunctionCall): List[BeExpression] =
    call.funcDef.inputs.flatMap(variable => call.parameterValueMap.get(variable))

  private def renderArgument(argument: BeExpression): String = argument match {
    case BeUseValue(BeDataValueLiteral(value), _) => s"<l>${escape(value)}</l>"
    case sequence: BeSequence =>
      val nestedBlocks = sequence.body.collect { case call: BeFunctionCall => renderCall(call) }.mkString
      s"<script>$nestedBlocks</script>"
    case call: BeFunctionCall => renderCall(call)
    case _ => "<l></l>"
  }

  private def escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
