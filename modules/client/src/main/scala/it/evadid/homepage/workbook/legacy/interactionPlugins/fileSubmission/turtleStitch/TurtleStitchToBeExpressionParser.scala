package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import TurtleStitchProgramModel.*
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.workbook.elements.interactionElements.programming.{SnapCanvasLayout, SnapCanvasScript}

import scala.collection.mutable.ListBuffer

object TurtleStitchToBeExpressionParser {

  final case class ParseWithLayout(expression: BeExpression, canvasLayout: SnapCanvasLayout)

  private val OperatorSymbols = Set(
    "+", "-", "*", "/", "//", "%", "**",
    "==", "!=", "<", "<=", ">", ">=",
    "and", "or", "not", "is", "is not", "&", "|", "^", "<<", ">>", "~"
  )

  private case class Signature(name: String, arity: Int, isOperator: Boolean)
  private case class PhaseOneResult(orderedDefinitions: List[BeDefineFunction], definitionBySignature: Map[Signature, BeDefineFunction])
  private case class ScriptParse(calls: List[BeExpression], layout: SnapCanvasScript)

  def parseProject(project: Project): BeExpression =
    parseProjectWithLayout(project).expression

  def parseProjectWithLayout(project: Project): ParseWithLayout = {
    scala.util.Try {
      val phaseOne = buildDefinitions(project)
      val scriptParses = parsePhaseTwoScripts(project, phaseOne)
      val calls = scriptParses.flatMap(_.calls)
      val layout = SnapCanvasLayout(scriptParses.map(_.layout).filter(_.callCount > 0))
      ParseWithLayout(BeStartProgram(BeSequence.optionalBody(calls)), layout)
    }.getOrElse(ParseWithLayout(BeStartProgram(BeSequence.optionalBody(Nil)), SnapCanvasLayout.empty))
  }

  def parseXml(xml: String): BeExpression =
    parseXmlWithLayout(xml).expression

  def parseXmlWithLayout(xml: String): ParseWithLayout = {
    val primary = parseProjectWithLayout(TurtleStitchXmlLoader.load(xml))
    if hasCallableBlocks(primary.expression) || !xml.contains("<block") then primary
    else
      val recovered = recoverCallableBlocksFromXmlWithLayout(xml)
      if hasCallableBlocks(recovered.expression) then recovered else primary
  }

  def hasCallableBlocks(expression: BeExpression): Boolean =
    expression match
      case BeStartProgram(Some(sequence)) => sequence.body.exists(_.isInstanceOf[BeFunctionCall])
      case BeStartProgram(None) => false
      case sequence: BeSequence => sequence.body.exists(_.isInstanceOf[BeFunctionCall])
      case _: BeFunctionCall => true
      case _ => false

  /**
   * Depth-aware recovery when the model path yields no calls (common for live Snap XML).
   */
  private def recoverCallableBlocksFromXml(xml: String): BeExpression =
    recoverCallableBlocksFromXmlWithLayout(xml).expression

  private def recoverCallableBlocksFromXmlWithLayout(xml: String): ParseWithLayout = {
    val scripts = topLevelScriptsPreferringSprite(xml)
    if scripts.isEmpty then
      val calls = topLevelBlocks(xml).flatMap(blockToCall)
      ParseWithLayout(
        BeStartProgram(BeSequence.optionalBody(calls)),
        if calls.isEmpty then SnapCanvasLayout.empty
        else SnapCanvasLayout.single(callCount = calls.size)
      )
    else
      val scriptParses = scripts.map { case (attrs, body) =>
        val calls = topLevelBlocks(body).flatMap(blockToCall)
        ScriptParse(calls, layoutFromAttrs(attrs, calls.size))
      }
      val calls = scriptParses.flatMap(_.calls)
      ParseWithLayout(
        BeStartProgram(BeSequence.optionalBody(calls)),
        SnapCanvasLayout(scriptParses.map(_.layout).filter(_.callCount > 0))
      )
  }

  private def topLevelScriptsPreferringSprite(xml: String): List[(String, String)] = {
    val fromSprites =
      findTagInnerAnywhere(xml, "sprites").toList.flatMap { sprites =>
        findTagInnerAnywhere(sprites, "sprite").toList.flatMap { sprite =>
          findTagInnerAnywhere(sprite, "scripts").toList
        }
      }
    val sections =
      if fromSprites.exists(_.contains("<block")) then fromSprites.filter(_.contains("<block"))
      else findAllTagInnersAnywhere(xml, "scripts").filter(_.contains("<block"))
    sections.flatMap(section => topLevelTaggedSections(section, "script"))
  }

  private def layoutFromAttrs(attrs: String, callCount: Int): SnapCanvasScript = {
    val x = attrDouble(attrs, "x").map(_.round.toInt).getOrElse(156)
    val y = attrDouble(attrs, "y").map(_.round.toInt).getOrElse(66)
    SnapCanvasScript(x, y, callCount)
  }

  private def attrDouble(attrs: String, name: String): Option[Double] =
    raw"""\b$name="([^"]*)"""".r.findFirstMatchIn(attrs).flatMap(m => scala.util.Try(m.group(1).toDouble).toOption)

  private def topLevelBlocks(scriptBody: String): List[(String, String)] =
    topLevelTaggedSections(scriptBody, "block")

  private def blockToCall(block: (String, String)): Option[BeExpression] = {
    val (attrs, body) = block
    val selector = """\bs="([^"]*)"""".r.findFirstMatchIn(attrs).map(_.group(1))
      .orElse("""\bvar="([^"]*)"""".r.findFirstMatchIn(attrs).map(_.group(1)))
      .filter(_.nonEmpty)
    selector.map { name =>
      val literals =
        topLevelTaggedSections(body, "l").map(_._2.trim).map(v => if v.nonEmpty then v else "0")
      val nestedBlocks = topLevelBlocks(body)
      val arity = math.max(literals.size, nestedBlocks.size)
      val params = (1 to arity).toList.map { idx =>
        BeDefineVariable(BeEntityName.fromCodeString(s"arg$idx"), BeDataType.AnyType)
      }
      val isOperator = OperatorSymbols.contains(name)
      val define =
        if isOperator then
          BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.operatorInfo(name, if arity <= 1 then 0 else 1))
        else
          BeDefineFunction(
            params,
            None,
            BeExpression.pass,
            BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(name))
          )
      val values: List[BeExpression] =
        if literals.nonEmpty then
          literals.map(v => BeUseValue(BeDataValueLiteral(v), None))
        else
          nestedBlocks.flatMap(blockToCall)
      val mapped = params.zip(values).toMap
      BeFunctionCall(define, mapped)
    }
  }

  private def findTagInnerAnywhere(xml: String, tag: String): Option[String] =
    findAllTagInnersAnywhere(xml, tag).headOption

  private def findAllTagInnersAnywhere(xml: String, tag: String): List[String] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    val out = ListBuffer.empty[String]
    var i = 0
    while i < xml.length do
      val start = xml.indexOf(open, i)
      if start < 0 then return out.toList
      val afterTag = start + open.length
      if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then
        i = afterTag
      else
        val gt = xml.indexOf('>', afterTag)
        if gt < 0 then return out.toList
        val rawAttrs = xml.substring(afterTag, gt).trim
        if rawAttrs.endsWith("/") then
          out += ""
          i = gt + 1
        else
          val innerStart = gt + 1
          val innerEnd = findMatchingClose(xml, innerStart, open, close)
          if innerEnd < 0 then return out.toList
          out += xml.substring(innerStart, innerEnd)
          i = innerEnd + close.length
    out.toList
  }

  private def topLevelTaggedSections(xml: String, tag: String): List[(String, String)] = {
    val open = s"<$tag"
    val close = s"</$tag>"
    val out = ListBuffer.empty[(String, String)]
    var i = 0
    while i < xml.length do
      val start = xml.indexOf(open, i)
      if start < 0 then return out.toList
      val afterTag = start + open.length
      if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then
        i = afterTag
      else
        val gt = xml.indexOf('>', afterTag)
        if gt < 0 then return out.toList
        val attrs = xml.substring(afterTag, gt).trim
        if attrs.endsWith("/") then
          out += ((attrs.stripSuffix("/").trim, ""))
          i = gt + 1
        else
          val innerStart = gt + 1
          val innerEnd = findMatchingClose(xml, innerStart, open, close)
          if innerEnd < 0 then return out.toList
          out += ((attrs, xml.substring(innerStart, innerEnd)))
          i = innerEnd + close.length
    out.toList
  }

  private def isTagNameEnd(ch: Char): Boolean =
    ch.isWhitespace || ch == '>' || ch == '/'

  private def findMatchingClose(xml: String, from: Int, open: String, close: String): Int = {
    var depth = 1
    var i = from
    while i < xml.length && depth > 0 do
      val nextOpen = xml.indexOf(open, i)
      val nextClose = xml.indexOf(close, i)
      if nextClose < 0 then return -1
      if nextOpen >= 0 && nextOpen < nextClose then
        val after = nextOpen + open.length
        if after < xml.length && isTagNameEnd(xml.charAt(after)) then depth += 1
        i = math.max(after, nextOpen + 1)
      else
        depth -= 1
        if depth == 0 then return nextClose
        i = nextClose + close.length
    -1
  }

  private def buildDefinitions(project: Project): PhaseOneResult = {
    val signatures = collectSignatures(project)
    val defs = signatures.map(createDefinition)
    PhaseOneResult(defs, signatures.zip(defs).toMap)
  }

  private def parsePhaseTwoScripts(project: Project, phaseOne: PhaseOneResult): List[ScriptParse] = {
    project.scenes.toList.flatMap { scene =>
      val stage = parseScriptsWithLayout(scene.stage.scripts, phaseOne)
      val sprites = scene.stage.sprites.toList.flatMap(sprite => parseScriptsWithLayout(sprite.scripts, phaseOne))
      stage ++ sprites
    }
  }

  private def parseScriptsWithLayout(scripts: Vector[Script], phaseOne: PhaseOneResult): List[ScriptParse] =
    scripts.toList.map { script =>
      val calls = script.blocks.toList.flatMap(block => parseBlock(block, phaseOne))
      val x = script.x.map(_.round.toInt).getOrElse(156)
      val y = script.y.map(_.round.toInt).getOrElse(66)
      ScriptParse(calls, SnapCanvasScript(x, y, calls.size))
    }

  private def parseBlock(block: BlockLike, phaseOne: PhaseOneResult): Option[BeExpression] = {
    val signature = signatureOf(block)
    val definition = phaseOne.definitionBySignature.get(signature)
    definition.map { defn =>
      val params = defn.inputs
      val values = inputValuesOf(block)
      val mapped = params.zipAll(values, null, Literal(""))
        .filter(_._1 != null)
        .map { case (parameter, input) =>
          parameter -> parseInputValue(input, Some(parameter), phaseOne)
        }.toMap
      BeFunctionCall(defn, mapped)
    }
  }

  private def parseInputValue(input: InputValue, context: Option[BeDefineVariable], phaseOne: PhaseOneResult): BeExpression = input match {
    case Literal(value) => BeUseValue(BeDataValueLiteral(value), context)
    case BoolLiteral(value) => BeUseValue(BeDataValueLiteral(value.toString), context)
    case ColorLiteral(value) => BeUseValue(BeDataValueLiteral(s"${value.r},${value.g},${value.b},${value.a}"), context)
    case ListLiteral(items) =>
      val flattened = items.map {
        case Literal(v) => v
        case BoolLiteral(v) => v.toString
        case _ => "item"
      }.mkString("[", ",", "]")
      BeUseValue(BeDataValueLiteral(flattened), context)
    case NestedScript(script) => BeSequence.optionalBody(script.blocks.toList.flatMap(block => parseBlock(block, phaseOne)))
    case NestedBlock(block) => parseBlock(block, phaseOne).getOrElse(BeExpression.pass)
  }

  private def collectSignatures(project: Project): List[Signature] = {
    val allBlocks = project.scenes.toList.flatMap { scene =>
      val stageBlocks = scene.stage.scripts.toList.flatMap(_.blocks)
      val spriteBlocks = scene.stage.sprites.toList.flatMap(_.scripts.toList.flatMap(_.blocks))
      stageBlocks ++ spriteBlocks
    }

    allBlocks.map(signatureOf).distinct
  }

  private def signatureOf(block: BlockLike): Signature = block match {
    case PrimitiveBlock(selector, variable, inputs, _) =>
      val name = selector.orElse(variable).getOrElse("block")
      Signature(name, inputs.size.max(0), OperatorSymbols.contains(name))
    case CustomBlockCall(spec, _, inputs, _, _) =>
      Signature(spec, inputs.size.max(0), OperatorSymbols.contains(spec))
  }

  private def inputValuesOf(block: BlockLike): List[InputValue] = block match {
    case PrimitiveBlock(_, _, inputs, _) => inputs.toList
    case CustomBlockCall(_, _, inputs, _, _) => inputs.toList
  }

  private def createDefinition(signature: Signature): BeDefineFunction = {
    val params = (1 to signature.arity).toList.map { idx =>
      BeDefineVariable(BeEntityName.fromCodeString(s"arg$idx"), BeDataType.AnyType)
    }

    if (signature.isOperator)
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.operatorInfo(signature.name, if (signature.arity <= 1) 0 else 1))
    else
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(signature.name)))
  }
}
