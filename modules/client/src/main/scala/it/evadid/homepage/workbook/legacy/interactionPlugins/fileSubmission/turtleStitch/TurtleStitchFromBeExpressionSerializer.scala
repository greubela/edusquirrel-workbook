package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.workbook.vm.code.{defining}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.controlStructures.BeSequence
import it.evadid.workbook.vm.code.{BeExpression, defining}
import it.evadid.workbook.vm.code.defining.BeDefineFunction
import it.evadid.workbook.vm.naming.BeEntityName
import it.evadid.workbook.vm.code.others.BeStartProgram
import it.evadid.workbook.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.workbook.vm.types.BeDataValueLiteral

object TurtleStitchFromBeExpressionSerializer {

  // Upstream references for XML shape compatibility:
  // - src/store.js: Project/Scene/Stage/Sprite `toXML` methods (~2021/~2048/~2116/~2224)
  // - src/store.js: Script/block serialization (`toScriptXML` / `toBlockXML`, ~2436+)

  def toXml(expression: BeExpression, projectName: String = "fromBeExpression"): String = {
    val scripts = scriptsFromExpression(expression)
    val xmlScripts = if (scripts.nonEmpty) scripts.map(renderScript).mkString else ""

    s"""<project name="$projectName" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="$projectName"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts>$xmlScripts</scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""
  }

  private def scriptsFromExpression(expression: BeExpression): List[List[BeFunctionCall]] = {
    val body = expression match {
      case BeStartProgram(Some(sequence)) => sequence.body
      case BeStartProgram(None) => Nil
      case seq: BeSequence => seq.body
      case other => List(other)
    }

    val calls = body.collect { case call: BeFunctionCall => call }
    if (calls.isEmpty) Nil
    else {
      val withGreen = if (calls.exists(isReceiveGo)) calls else createReceiveGoCall() :: calls
      List(withGreen)
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

  private def renderScript(calls: List[BeFunctionCall]): String = {
    val blocks = calls.map(renderCall).mkString
    s"<script x=\"156\" y=\"66\">$blocks</script>"
  }

  private def renderCall(call: BeFunctionCall): String = {
    val selector = selectorOf(call)
    val arguments = orderedArgs(call)

    val inputXml = arguments.map(renderArgument).mkString
    s"<block s=\"$selector\">$inputXml</block>"
  }

  private def selectorOf(call: BeFunctionCall): String =
    call.funcDef.functionTypeInfo.displayName.getNameIn(AppLanguage.English, it.evadid.workbook.vm.naming.NamingStyle.SnakeCase).trim

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
