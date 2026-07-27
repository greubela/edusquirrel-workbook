package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import TurtleStitchProgramModel.*
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}

import scala.collection.mutable.ListBuffer

object TurtleStitchToBeExpressionParser {

  private val OperatorSymbols = Set(
    "+", "-", "*", "/", "//", "%", "**",
    "==", "!=", "<", "<=", ">", ">=",
    "and", "or", "not", "is", "is not", "&", "|", "^", "<<", ">>", "~"
  )

  private case class Signature(name: String, arity: Int, isOperator: Boolean)
  private case class PhaseOneResult(orderedDefinitions: List[BeDefineFunction], definitionBySignature: Map[Signature, BeDefineFunction])

  def parseProject(project: Project): BeExpression = {
    scala.util.Try {
      val phaseOne = buildDefinitions(project)
      // Only keep calls in the program body. Definitions are already attached to
      // each BeFunctionCall; putting BeDefineFunction nodes in the body makes
      // Python persistence noisy and can yield Snap previews with no scripts.
      val phaseTwoExpressions = parsePhaseTwo(project, phaseOne)
      BeStartProgram(BeSequence.optionalBody(phaseTwoExpressions))
    }.getOrElse(BeStartProgram(BeSequence.optionalBody(Nil)))
  }

  def parseXml(xml: String): BeExpression = {
    val primary = parseProject(TurtleStitchXmlLoader.load(xml))
    if hasCallableBlocks(primary) || !xml.contains("<block") then primary
    else
      val recovered = recoverCallableBlocksFromXml(xml)
      if hasCallableBlocks(recovered) then recovered else primary
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
  private def recoverCallableBlocksFromXml(xml: String): BeExpression = {
    val scriptBodies = scriptBodiesPreferringSprite(xml)
    val bodies = if scriptBodies.nonEmpty then scriptBodies else List(xml)
    val calls = bodies.flatMap(topLevelBlocks).flatMap(blockToCall)
    BeStartProgram(BeSequence.optionalBody(calls))
  }

  private def scriptBodiesPreferringSprite(xml: String): List[String] = {
    val fromSprites =
      findTagInnerAnywhere(xml, "sprites").toList.flatMap { sprites =>
        findTagInnerAnywhere(sprites, "sprite").toList.flatMap { sprite =>
          findTagInnerAnywhere(sprite, "scripts").toList
        }
      }
    val sections =
      if fromSprites.exists(_.contains("<block")) then fromSprites.filter(_.contains("<block"))
      else findAllTagInnersAnywhere(xml, "scripts").filter(_.contains("<block"))
    sections.flatMap(section => topLevelTaggedSections(section, "script").map(_._2))
  }

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

  private def parsePhaseTwo(project: Project, phaseOne: PhaseOneResult): List[BeExpression] = {
    project.scenes.toList.flatMap { scene =>
      val stageCalls = parseScripts(scene.stage.scripts, phaseOne)
      val spriteCalls = scene.stage.sprites.toList.flatMap(sprite => parseScripts(sprite.scripts, phaseOne))
      stageCalls ++ spriteCalls
    }
  }

  private def parseScripts(scripts: Vector[Script], phaseOne: PhaseOneResult): List[BeExpression] =
    scripts.toList.flatMap(script => script.blocks.toList.flatMap(block => parseBlock(block, phaseOne)))

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
