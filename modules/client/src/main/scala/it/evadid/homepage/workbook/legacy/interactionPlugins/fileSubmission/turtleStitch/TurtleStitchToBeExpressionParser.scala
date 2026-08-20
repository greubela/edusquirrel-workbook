package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import TurtleStitchProgramModel.*
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.workbook.elements.interactionElements.programming.{SnapCanvasLayout, SnapCanvasScript, SnapControlFlow, SnapTurtlePythonBridge}

import scala.collection.mutable.ListBuffer

object TurtleStitchToBeExpressionParser {

  final case class ParseWithLayout(expression: BeExpression, canvasLayout: SnapCanvasLayout)

  private val OperatorSymbols = Set(
    "+", "-", "*", "/", "//", "%", "**",
    "==", "!=", "<", "<=", ">", ">=",
    "and", "or", "not", "is", "is not", "&", "|", "^", "<<", ">>", "~"
  )

  private sealed trait BlockInput
  private final case class InputLiteral(value: String) extends BlockInput
  private final case class InputScript(body: String) extends BlockInput
  private final case class InputBlock(attrs: String, body: String) extends BlockInput
  private final case class InputList(items: List[BlockInput]) extends BlockInput

  private case class Signature(name: String, arity: Int, isOperator: Boolean)
  private case class PhaseOneResult(orderedDefinitions: List[BeDefineFunction], definitionBySignature: Map[Signature, BeDefineFunction])
  private case class ScriptParse(statements: List[BeExpression], layout: SnapCanvasScript)

  private var currentVars = new SnapControlFlow.VariableInterner

  private def withVars[T](interner: SnapControlFlow.VariableInterner)(body: => T): T = {
    val previous = currentVars
    currentVars = interner
    try body
    finally currentVars = previous
  }

  def parseProject(project: Project): BeExpression =
    parseProjectWithLayout(project).expression

  def parseProjectWithLayout(project: Project): ParseWithLayout = {
    scala.util.Try {
      val interner = SnapControlFlow.VariableInterner.fromNames(collectVariableNamesFromProject(project))
      withVars(interner) {
        val phaseOne = buildDefinitions(project)
        val scriptParses = parsePhaseTwoScripts(project, phaseOne)
        val statements = scriptParses.flatMap(_.statements)
        val layout = SnapCanvasLayout(scriptParses.map(_.layout).filter(_.callCount > 0))
        ParseWithLayout(BeStartProgram(BeSequence.optionalBody(statements)), layout)
      }
    }.getOrElse(ParseWithLayout(BeStartProgram(BeSequence.optionalBody(Nil)), SnapCanvasLayout.empty))
  }

  def parseXml(xml: String): BeExpression =
    parseXmlWithLayout(xml).expression

  def parseXmlWithLayout(xml: String): ParseWithLayout = {
    val primary = parseProjectWithLayout(TurtleStitchXmlLoader.load(xml))
    if hasSupportedStatements(primary.expression) || !xml.contains("<block") then primary
    else
      val recovered = recoverStatementsFromXmlWithLayout(xml)
      if hasSupportedStatements(recovered.expression) then recovered else primary
  }

  def hasSupportedStatements(expression: BeExpression): Boolean =
    SnapTurtlePythonBridge.hasSupportedStatements(expression)

  private def recoverStatementsFromXmlWithLayout(xml: String): ParseWithLayout = {
    val interner = SnapControlFlow.VariableInterner.fromNames(collectVariableNamesFromXml(xml))
    withVars(interner) {
      val scripts = topLevelScriptsPreferringSprite(xml)
      if scripts.isEmpty then
        val statements = topLevelBlocks(xml).flatMap(blockToStatement)
        ParseWithLayout(
          BeStartProgram(BeSequence.optionalBody(statements)),
          if statements.isEmpty then SnapCanvasLayout.empty
          else SnapCanvasLayout.single(callCount = statements.size)
        )
      else
        val scriptParses = scripts.map { case (attrs, body) =>
          val statements = topLevelBlocks(body).flatMap(blockToStatement)
          ScriptParse(statements, layoutFromAttrs(attrs, statements.size))
        }
        val statements = scriptParses.flatMap(_.statements)
        ParseWithLayout(
          BeStartProgram(BeSequence.optionalBody(statements)),
          SnapCanvasLayout(scriptParses.map(_.layout).filter(_.callCount > 0))
        )
    }
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

  private def layoutFromAttrs(attrs: String, statementCount: Int): SnapCanvasScript = {
    val x = attrDouble(attrs, "x").map(_.round.toInt).getOrElse(156)
    val y = attrDouble(attrs, "y").map(_.round.toInt).getOrElse(66)
    SnapCanvasScript(x, y, statementCount)
  }

  private def attrDouble(attrs: String, name: String): Option[Double] =
    raw"""\b$name="([^"]*)"""".r.findFirstMatchIn(attrs).flatMap(m => scala.util.Try(m.group(1).toDouble).toOption)

  private def topLevelBlocks(scriptBody: String): List[(String, String)] =
    topLevelTaggedSections(scriptBody, "block")

  private def blockToStatement(block: (String, String)): Option[BeExpression] = {
    val (attrs, body) = block
    val selector = blockSelector(attrs)
    selector match {
      case Some("doRepeat") => Some(parseDoRepeatFromXml(body))
      case Some("doIfElse") => Some(parseDoIfElseFromXml(body))
      case Some("doIf") => Some(parseDoIfFromXml(body))
      case Some("doUntil") => Some(parseDoUntilFromXml(body))
      case Some("doSetVar") => Some(parseDoSetVarFromXml(body))
      case Some("doChangeVar") => Some(parseDoChangeVarFromXml(body))
      case Some("reportGetVar") =>
        Some(SnapControlFlow.useVariable(currentVars.intern(variableReporterName(attrs, body))))
      case Some(name) if SnapControlFlow.isSnapConditionReporter(name) =>
        Some(reporterExpressionFromXml(name, body))
      case Some(_) => blockToGenericCall(block)
      case None =>
        variableAttr(attrs).map(name => SnapControlFlow.useVariable(currentVars.intern(name)))
    }
  }

  private def variableReporterName(attrs: String, body: String): String =
    variableAttr(attrs)
      .orElse(parseOrderedInputs(body).collectFirst { case InputLiteral(value) if value.trim.nonEmpty => value.trim })
      .getOrElse("x")

  private def variableNameFromReporter(variable: Option[String], inputs: Vector[InputValue]): String =
    variable.filter(_.trim.nonEmpty)
      .orElse(inputs.collectFirst { case Literal(value) if value.trim.nonEmpty => value.trim })
      .getOrElse("x")

  private def blockToGenericCall(block: (String, String)): Option[BeExpression] = {
    val (attrs, body) = block
    val selector = blockSelector(attrs)
    selector.map { name =>
      val inputs = parseOrderedInputs(body)
      val literals = inputs.collect { case InputLiteral(value) => value }
      val nestedBlocks = inputs.collect { case InputBlock(a, b) => (a, b) }
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
        inputs.map {
          case InputLiteral(value) => BeUseValue(BeDataValueLiteral(value), None)
          case InputScript(scriptBody) =>
            BeSequence.optionalBody(topLevelBlocks(scriptBody).flatMap(blockToStatement))
          case InputBlock(nestedAttrs, nestedBody) =>
            blockToStatement((nestedAttrs, nestedBody)).getOrElse(BeExpression.pass)
          case InputList(items) =>
            val flattened = items.collect { case InputLiteral(value) => value }.mkString("[", ",", "]")
            BeUseValue(BeDataValueLiteral(flattened), None)
        }
      val mapped = params.zipAll(values, null, BeExpression.pass).filter(_._1 != null).map {
        case (parameter, value) => parameter -> value
      }.toMap
      BeFunctionCall(define, mapped)
    }
  }

  private def parseDoRepeatFromXml(body: String): BeExpression = {
    val inputs = parseOrderedInputs(body)
    val amount = inputs.collectFirst { case InputLiteral(value) => value }.flatMap(SnapControlFlow.literalInt).getOrElse(0)
    val scriptBody = inputs.collectFirst { case InputScript(value) => value }.getOrElse("")
    BeRepeatNr(amount, scriptBodyFromXml(scriptBody))
  }

  private def parseDoIfElseFromXml(body: String): BeExpression = {
    val inputs = parseOrderedInputs(body)
    val condition = conditionFromInputs(inputs)
    val scripts = inputs.collect { case InputScript(value) => value }
    val thenBody = scripts.headOption.map(scriptBodyFromXml).getOrElse(BeSequence.optionalBody(Nil))
    val elseBody = scripts.drop(1).headOption.map(scriptBodyFromXml).getOrElse(BeSequence.optionalBody(Nil))
    BeIfElse(BeSequence.conditionalBody(List(condition)), thenBody, elseBody)
  }

  private def parseDoIfFromXml(body: String): BeExpression = {
    val inputs = parseOrderedInputs(body)
    val condition = conditionFromInputs(inputs)
    val thenBody = inputs.collectFirst { case InputScript(value) => value }.map(scriptBodyFromXml).getOrElse(BeSequence.optionalBody(Nil))
    BeIfElse(BeSequence.conditionalBody(List(condition)), thenBody, BeSequence.optionalBody(Nil))
  }

  private def parseDoUntilFromXml(body: String): BeExpression = {
    val inputs = parseOrderedInputs(body)
    val condition = SnapControlFlow.invertCondition(conditionFromInputs(inputs))
    val scriptBody = inputs.collectFirst { case InputScript(value) => value }.getOrElse("")
    BeWhile(BeSequence.conditionalBody(List(condition)), scriptBodyFromXml(scriptBody))
  }

  private def parseDoSetVarFromXml(body: String): BeExpression = {
    val inputs = parseOrderedInputs(body)
    val name = literalNameFromInputs(inputs).getOrElse("x")
    val value = valueFromXmlInputs(dropNameInput(inputs)).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
    SnapControlFlow.assignVariable(currentVars.intern(name), value)
  }

  private def parseDoChangeVarFromXml(body: String): BeExpression = {
    val inputs = parseOrderedInputs(body)
    val name = literalNameFromInputs(inputs).getOrElse("x")
    val amount = valueFromXmlInputs(dropNameInput(inputs)).getOrElse(BeUseValue(BeDataValueLiteral("1"), None))
    SnapControlFlow.changeVariable(currentVars.intern(name), amount)
  }

  private def literalNameFromInputs(inputs: List[BlockInput]): Option[String] =
    inputs.collectFirst { case InputLiteral(value) if value.trim.nonEmpty => value.trim }

  private def dropNameInput(inputs: List[BlockInput]): List[BlockInput] =
    inputs match
      case InputLiteral(_) :: rest => rest
      case other => other

  private def valueFromXmlInputs(inputs: List[BlockInput]): Option[BeExpression] =
    inputs.headOption.flatMap {
      case InputLiteral(value) => Some(BeUseValue(BeDataValueLiteral(value), None))
      case InputBlock(attrs, nestedBody) => blockToStatement((attrs, nestedBody))
      case InputList(items) => valueFromXmlInputs(items)
      case InputScript(_) => None
    }

  private def scriptBodyFromXml(scriptBody: String): BeSequence =
    BeSequence.optionalBody(topLevelBlocks(scriptBody).flatMap(blockToStatement))

  private def conditionFromInputs(inputs: List[BlockInput]): BeExpression =
    inputs.collectFirst { case InputBlock(attrs, nestedBody) =>
      blockToStatement((attrs, nestedBody)).getOrElse(BeUseValue(BeDataValueLiteral("True"), None))
    }.getOrElse(BeUseValue(BeDataValueLiteral("True"), None))

  private def reporterExpressionFromXml(selector: String, body: String): BeExpression =
    SnapControlFlow.conditionFromSnapReporter(reporterCallFromXml(selector, body))

  private def reporterExpressionFromModel(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val selector = blockSelector(block).getOrElse("reportTrue")
    SnapControlFlow.conditionFromSnapReporter(reporterCallFromModel(selector, inputValuesOf(block), phaseOne))
  }

  private def reporterCallFromXml(selector: String, body: String): BeFunctionCall = {
    val args = expandXmlInputsToExpressions(parseOrderedInputs(body))
    reporterCall(selector, args)
  }

  private def reporterCallFromModel(selector: String, inputs: List[InputValue], phaseOne: PhaseOneResult): BeFunctionCall = {
    val args = expandModelInputsToExpressions(inputs, phaseOne)
    reporterCall(selector, args)
  }

  private def reporterCall(selector: String, args: List[BeExpression]): BeFunctionCall = {
    val params = args.indices.toList.map { idx =>
      BeDefineVariable(BeEntityName.fromCodeString(s"arg$idx"), BeDataType.AnyType)
    }
    val define = BeDefineFunction(
      params,
      None,
      BeExpression.pass,
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(selector))
    )
    BeFunctionCall(define, params.zip(args).toMap)
  }

  private def expandXmlInputsToExpressions(inputs: List[BlockInput]): List[BeExpression] =
    inputs.flatMap {
      case InputLiteral(value) =>
        if value.trim.isEmpty then Nil else List(expressionFromLiteral(value))
      case InputBlock(attrs, nestedBody) =>
        blockToStatement((attrs, nestedBody)).toList
      case InputList(items) => expandXmlInputsToExpressions(items)
      case InputScript(scriptBody) =>
        List(BeSequence.optionalBody(topLevelBlocks(scriptBody).flatMap(blockToStatement)))
    }

  private def expandModelInputsToExpressions(inputs: List[InputValue], phaseOne: PhaseOneResult): List[BeExpression] =
    inputs.flatMap {
      case Literal(value) =>
        if value.trim.isEmpty then Nil else List(expressionFromLiteral(value))
      case BoolLiteral(value) => List(BeUseValue(BeDataValueLiteral(if value then "True" else "False"), None))
      case ListLiteral(items) => expandModelInputsToExpressions(items.toList, phaseOne)
      case NestedBlock(block) => parseBlock(block, phaseOne).toList
      case NestedScript(script) => List(parseScriptBody(script, phaseOne))
      case ColorLiteral(value) =>
        List(BeUseValue(BeDataValueLiteral(s"${value.r},${value.g},${value.b},${value.a}"), None))
    }

  private def expressionFromLiteral(value: String): BeExpression = {
    val trimmed = value.trim
    if isVariableNameLiteral(trimmed) then SnapControlFlow.useVariable(currentVars.intern(trimmed))
    else BeUseValue(BeDataValueLiteral(value), None)
  }

  private def isVariableNameLiteral(value: String): Boolean =
    value.nonEmpty &&
      value != "True" &&
      value != "False" &&
      (value.head.isLetter || value.head == '_') &&
      value.forall(ch => ch.isLetterOrDigit || ch == '_')

  private def parseOrderedInputs(xml: String): List[BlockInput] = {
    val out = ListBuffer.empty[BlockInput]
    var i = 0
    while i < xml.length do
      val nextTag = findNextTopLevelTag(xml, i)
      nextTag match
        case None => return out.toList
        case Some((start, tag, open, close)) =>
          val afterTag = start + open.length
          val gt = xml.indexOf('>', afterTag)
          if gt < 0 then return out.toList
          val attrs = xml.substring(afterTag, gt).trim
          if attrs.endsWith("/") then
            tag match
              case "l" => out += InputLiteral("")
              case "block" => out += InputBlock(attrs.stripSuffix("/").trim, "")
              case _ => ()
            i = gt + 1
          else
            val innerStart = gt + 1
            val innerEnd = findMatchingClose(xml, innerStart, open, close)
            if innerEnd < 0 then return out.toList
            val inner = xml.substring(innerStart, innerEnd)
            tag match
              case "l" => out += InputLiteral(inner.trim)
              case "bool" => out += InputLiteral(if inner.trim.equalsIgnoreCase("true") then "True" else "False")
              case "script" => out += InputScript(inner)
              case "block" => out += InputBlock(attrs, inner)
              case "list" => out += InputList(parseOrderedInputs(inner))
              case _ => ()
            i = innerEnd + close.length
    out.toList
  }

  private def findNextTopLevelTag(xml: String, from: Int): Option[(Int, String, String, String)] = {
    val candidates = List("l", "bool", "script", "block", "list").flatMap { tag =>
      val open = s"<$tag"
      val idx = xml.indexOf(open, from)
      if idx < 0 then None
      else
        val afterTag = idx + open.length
        if afterTag < xml.length && !isTagNameEnd(xml.charAt(afterTag)) then None
        else Some((idx, tag, open, s"</$tag>"))
    }
    candidates.minByOption(_._1)
  }

  private def blockSelector(attrs: String): Option[String] =
    """\bs="([^"]*)"""".r.findFirstMatchIn(attrs).map(_.group(1)).filter(_.nonEmpty)

  private def variableAttr(attrs: String): Option[String] =
    """\bvar="([^"]*)"""".r.findFirstMatchIn(attrs).map(_.group(1)).filter(_.nonEmpty)

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
        if after < xml.length && isTagNameEnd(xml.charAt(after)) then
          val gt = xml.indexOf('>', after)
          val selfClosing = gt >= 0 && xml.substring(after, gt + 1).contains("/")
          if !selfClosing then depth += 1
          i = if gt >= 0 then gt + 1 else math.max(after, nextOpen + 1)
        else
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
      val statements = script.blocks.toList.flatMap(block => parseBlock(block, phaseOne))
      val x = script.x.map(_.round.toInt).getOrElse(156)
      val y = script.y.map(_.round.toInt).getOrElse(66)
      ScriptParse(statements, SnapCanvasScript(x, y, statements.size))
    }

  private def parseBlock(block: BlockLike, phaseOne: PhaseOneResult): Option[BeExpression] =
    block match
      case PrimitiveBlock(None, Some(name), _, _) =>
        Some(SnapControlFlow.useVariable(currentVars.intern(name)))
      case PrimitiveBlock(Some("reportGetVar"), variable, inputs, _) =>
        Some(SnapControlFlow.useVariable(currentVars.intern(variableNameFromReporter(variable, inputs))))
      case _ =>
        blockSelector(block).flatMap {
          case "doRepeat" => Some(parseDoRepeat(block, phaseOne))
          case "doIfElse" => Some(parseDoIfElse(block, phaseOne))
          case "doIf" => Some(parseDoIf(block, phaseOne))
          case "doUntil" => Some(parseDoUntil(block, phaseOne))
          case "doSetVar" => Some(parseDoSetVar(block, phaseOne))
          case "doChangeVar" => Some(parseDoChangeVar(block, phaseOne))
          case name if SnapControlFlow.isSnapConditionReporter(name) =>
            Some(reporterExpressionFromModel(block, phaseOne))
          case _ => parseGenericBlock(block, phaseOne)
        }

  private def blockSelector(block: BlockLike): Option[String] = block match
    case PrimitiveBlock(selector, _, _, _) => selector
    case CustomBlockCall(spec, _, _, _, _) => Some(spec)

  private def parseDoRepeat(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = inputValuesOf(block)
    val amount = inputs.collectFirst { case Literal(value) => value }.flatMap(SnapControlFlow.literalInt).getOrElse(0)
    val body = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeRepeatNr(amount, body)
  }

  private def parseDoIfElse(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = inputValuesOf(block)
    val condition = conditionFromModelInputs(inputs, phaseOne)
    val scripts = inputs.collect { case NestedScript(script) => script }
    val thenBody = scripts.headOption.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    val elseBody = scripts.drop(1).headOption.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeIfElse(BeSequence.conditionalBody(List(condition)), thenBody, elseBody)
  }

  private def parseDoIf(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = inputValuesOf(block)
    val condition = conditionFromModelInputs(inputs, phaseOne)
    val thenBody = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeIfElse(BeSequence.conditionalBody(List(condition)), thenBody, BeSequence.optionalBody(Nil))
  }

  private def parseDoUntil(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = inputValuesOf(block)
    val condition = SnapControlFlow.invertCondition(conditionFromModelInputs(inputs, phaseOne))
    val body = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeWhile(BeSequence.conditionalBody(List(condition)), body)
  }

  private def parseDoSetVar(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = inputValuesOf(block)
    val name = literalNameFromModelInputs(inputs).getOrElse("x")
    val value = valueFromModelInputs(dropNameModelInput(inputs), phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
    SnapControlFlow.assignVariable(currentVars.intern(name), value)
  }

  private def parseDoChangeVar(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = inputValuesOf(block)
    val name = literalNameFromModelInputs(inputs).getOrElse("x")
    val amount = valueFromModelInputs(dropNameModelInput(inputs), phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("1"), None))
    SnapControlFlow.changeVariable(currentVars.intern(name), amount)
  }

  private def literalNameFromModelInputs(inputs: List[InputValue]): Option[String] =
    inputs.collectFirst { case Literal(value) if value.trim.nonEmpty => value.trim }

  private def dropNameModelInput(inputs: List[InputValue]): List[InputValue] =
    inputs match
      case Literal(_) :: rest => rest
      case other => other

  private def valueFromModelInputs(inputs: List[InputValue], phaseOne: PhaseOneResult): Option[BeExpression] =
    inputs.headOption.map {
      case Literal(value) => BeUseValue(BeDataValueLiteral(value), None)
      case BoolLiteral(value) => BeUseValue(BeDataValueLiteral(value.toString), None)
      case NestedBlock(nested) => parseBlock(nested, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
      case ListLiteral(items) => valueFromModelInputs(items.toList, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
      case other => parseInputValue(other, None, phaseOne)
    }

  private def conditionFromModelInputs(inputs: List[InputValue], phaseOne: PhaseOneResult): BeExpression =
    inputs.collectFirst { case NestedBlock(nested) =>
      parseBlock(nested, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("True"), None))
    }.getOrElse(BeUseValue(BeDataValueLiteral("True"), None))

  private def parseScriptBody(script: Script, phaseOne: PhaseOneResult): BeSequence =
    BeSequence.optionalBody(script.blocks.toList.flatMap(block => parseBlock(block, phaseOne)))

  private def parseGenericBlock(block: BlockLike, phaseOne: PhaseOneResult): Option[BeExpression] = {
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
    case NestedScript(script) => parseScriptBody(script, phaseOne)
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

  private def collectVariableNamesFromProject(project: Project): List[String] =
    project.scenes.toList.flatMap { scene =>
      scene.variables.map(_.name) ++
        scene.stage.variables.map(_.name) ++
        scene.stage.sprites.toList.flatMap(_.variables.map(_.name))
    }.map(_.trim).filter(_.nonEmpty)

  private def collectVariableNamesFromXml(xml: String): List[String] =
    """<variable\b[^>]*\bname="([^"]+)"""".r.findAllMatchIn(xml).map(_.group(1).trim).filter(_.nonEmpty).toList

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
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.operatorInfo(signature.name, if signature.arity <= 1 then 0 else 1))
    else
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(signature.name)))
  }
}
