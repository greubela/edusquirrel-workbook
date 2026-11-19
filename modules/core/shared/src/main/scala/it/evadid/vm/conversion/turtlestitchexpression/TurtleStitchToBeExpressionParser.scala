package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.conversion.snapexpression.SnapControlFlow
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.snapexpression.SnapInputCodec
import it.evadid.vm.conversion.snapxml.SnapXmlParser

import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchProgramModel.{BlockLike, BoolLiteral, ColorLiteral, CustomBlockCall, CustomBlockDefinition, InputValue, ListLiteral, Literal, NestedBlock, NestedScript, PrimitiveBlock, Project, Script}
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.*
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.*

import scala.collection.mutable.ListBuffer

object TurtleStitchToBeExpressionParser {
  final case class ParseWithLayout(expression: BeExpression, canvasLayout: SnapCanvasLayout) derives upickle.default.ReadWriter

  def parseProject(project: Project): BeExpression = parseProjectWithLayout(project).expression
  def parseProjectWithLayout(project: Project): ParseWithLayout = Reader().parseProjectWithLayout(project)
  def parseXml(xml: String): BeExpression = parseXmlWithLayout(xml).expression
  def parseXmlWithLayout(xml: String): ParseWithLayout = Reader().parseXmlWithLayout(xml)

  private final class Reader {
  private val OperatorSymbols = Set(
    "+", "-", "*", "/", "//", "%", "**",
    "==", "!=", "<", "<=", ">", ">=",
    "and", "or", "not", "is", "is not", "&", "|", "^", "<<", ">>", "~"
  )

  private case class Signature(name: String, arity: Int, isOperator: Boolean, isCustom: Boolean = false) derives upickle.default.ReadWriter
  private case class PhaseOneResult(
      orderedDefinitions: List[BeDefineFunction],
      definitionBySignature: Map[Signature, BeDefineFunction],
      userDefinitions: List[BeDefineFunction]
  ) derives upickle.default.ReadWriter
  private case class ScriptParse(statements: List[BeExpression], layout: SnapCanvasScript) derives upickle.default.ReadWriter

  private var currentVars = new SnapControlFlow.VariableInterner

  private def withVars[T](interner: SnapControlFlow.VariableInterner)(body: => T): T = {
    val previous = currentVars
    currentVars = interner
    try body
    finally currentVars = previous
  }

  def parseProject(project: Project): BeExpression =
    parseProjectWithLayout(project).expression

  /** Failures propagate: a program that cannot be read is never replaced by an empty one. */
  def parseProjectWithLayout(project: Project): ParseWithLayout = {
    val interner = SnapControlFlow.VariableInterner.fromNames(project.variables.map(_.name.trim).filter(_.nonEmpty))
    withVars(interner) {
      val phaseOne = buildDefinitions(project)
      val scriptParses = parsePhaseTwoScripts(project, phaseOne)
      val statements = phaseOne.userDefinitions ++ scriptParses.flatMap(_.statements)
      val layout = SnapCanvasLayout(scriptParses.map(_.layout).filter(_.callCount > 0))
      ParseWithLayout(BeStartProgram(BeSequence.optionalBody(statements)), layout)
    }
  }

  def parseXml(xml: String): BeExpression =
    parseXmlWithLayout(xml).expression

  /** Snap XML through the single lossless reader; malformed XML is unparsable. */
  def parseXmlWithLayout(xml: String): ParseWithLayout = parseProjectWithLayout(TurtleStitchXmlLoader.load(xml))


  private def slotDataType(slot: String): BeDataType = slot match {
    case "%n" => BeDataType.Numeric
    case "%b" => BeDataType.Boolean
    case _ => BeDataType.AnyType
  }

  private def variableNameFromReporter(variable: Option[String], inputs: Vector[InputValue]): String =
    variable.filter(_.trim.nonEmpty)
      .orElse(inputs.collectFirst { case Literal(value) if value.trim.nonEmpty => value.trim })
      .getOrElse("x")

  /** The first slot of set/change is the variable, either `<l>name</l>` or `<block var="name"/>`. */
  /** A variable reporter is a value, not a command. Snap does not keep it in a script stack. */
  private def commandStatements(statements: List[BeExpression]): List[BeExpression] =
    statements.filterNot(_.isInstanceOf[BeUseValue])

  private def reporterExpressionFromModel(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val selector = blockSelector(block).getOrElse("reportTrue")
    SnapControlFlow.conditionFromSnapReporter(reporterCallFromModel(selector, block.inputs.toList, phaseOne))
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
      params.toList,
      None,
      BeExpression.pass,
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(selector))
    )
    BeFunctionCall.fromDefinition(define, params.zip(args).toMap)
  }

  private def expandModelInputsToExpressions(inputs: List[InputValue], phaseOne: PhaseOneResult): List[BeExpression] =
    inputs.flatMap {
      case Literal(value) =>
        if value.trim.isEmpty then Nil else List(expressionFromLiteral(value))
      case BoolLiteral(value) => List(BeUseValue(BeDataValueLiteral(booleanRaw(value)), None))
      case ListLiteral(items) => expandModelInputsToExpressions(items.toList, phaseOne)
      case NestedBlock(block) => parseBlock(block, phaseOne).toList
      case NestedScript(script) => List(parseScriptBody(script, phaseOne))
      case ColorLiteral(value) =>
        List(BeUseValue(BeDataValueLiteral(colorLiteralToPython(value)), None))
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

  /** Snap-native body is a direct `<script>` child; `<scripts>` holds extras / legacy bodies. */
  private def buildDefinitions(project: Project): PhaseOneResult = {
    val customDefs = project.customDefinitions.toList
    val fromCalls = collectSignatures(project)
    val fromCustoms = customDefs.map { defn =>
      val arity = math.max(defn.inputs.size, SnapTurtleCatalog.arityFromSpec(defn.spec))
      Signature(customSignatureName(defn.spec), arity, false, isCustom = true)
    }
    val signatures = (fromCalls ++ fromCustoms).distinct
    val stubs = signatures.map(createDefinition)
    val stubMap = signatures.zip(stubs).toMap
    val phaseOneStubs = PhaseOneResult(stubs, stubMap, Nil)
    val userDefinitions = customDefs.flatMap { defn =>
      val arity = math.max(defn.inputs.size, SnapTurtleCatalog.arityFromSpec(defn.spec))
      val sig = Signature(customSignatureName(defn.spec), arity, false, isCustom = true)
      stubMap.get(sig).map { stub =>
        val specNames = SnapTurtleCatalog.inputNamesFromSpec(defn.spec)
        val params =
          if specNames.nonEmpty then
            specNames.zipWithIndex.map((name, index) => BeDefineVariable(BeEntityName.fromLiteral(name), slotDataType(defn.inputs.lift(index).map(_.inputType).getOrElse("%s"))))
          else if defn.inputs.nonEmpty then
            defn.inputs.toList.zipWithIndex.map { (inp, idx) =>
              val name =
                if inp.name.trim.nonEmpty then inp.name.trim
                else s"arg${idx + 1}"
              BeDefineVariable(BeEntityName.fromLiteral(name), BeDataType.AnyType)
            }
          else stub.inputs
        val body = defn.body.headOption.map(parseScriptBody(_, phaseOneStubs)).getOrElse(BeSequence.optionalBody(Nil))
        val named = BeDefineFunction(
          params.toList,
          stub.outputs,
          body,
          BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(SnapTurtleCatalog.pythonNameFromCustomSpec(defn.spec)))
        )
        named
      }
    }
    val userBySpec = customDefs.zip(userDefinitions).map { (defn, fn) =>
      val arity = math.max(defn.inputs.size, SnapTurtleCatalog.arityFromSpec(defn.spec))
      Signature(customSignatureName(defn.spec), arity, false, isCustom = true) -> fn
    }.toMap
    val merged = stubMap ++ userBySpec
    PhaseOneResult(merged.values.toList, merged, userDefinitions)
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
      val statements = commandStatements(script.blocks.toList.flatMap(block => parseBlock(block, phaseOne)))
      val x = script.x.map(_.round.toInt).getOrElse(156)
      val y = script.y.map(_.round.toInt).getOrElse(66)
      val hasReceiveGo = script.blocks.headOption.exists(block => blockSelector(block).contains("receiveGo"))
      ScriptParse(
        statements,
        SnapCanvasScript(x, y, SnapExpressionSupport.scriptStatementCount(statements), hasReceiveGo)
      )
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
          case "doFor" => Some(parseDoFor(block, phaseOne))
          case "doIfElse" => Some(parseDoIfElse(block, phaseOne))
          case "doIf" => Some(parseDoIf(block, phaseOne))
          case "doUntil" => Some(parseDoUntil(block, phaseOne))
          case "doSetVar" => Some(parseDoSetVar(block, phaseOne))
          case "doChangeVar" => Some(parseDoChangeVar(block, phaseOne))
          case name if SnapControlFlow.isSnapValueReporter(name) =>
            Some(reporterExpressionFromModel(block, phaseOne))
          case _ => parseGenericBlock(block, phaseOne)
        }

  private def blockSelector(block: BlockLike): Option[String] = block match
    case PrimitiveBlock(selector, _, _, _) => selector
    case CustomBlockCall(spec, _, _, _, _) => Some(spec)

  private def parseDoRepeat(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = block.inputs.toList
    val amount = inputs.collectFirst { case Literal(value) => value }.flatMap(SnapControlFlow.literalInt).getOrElse(0)
    val body = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeRepeatNr(amount, body)
  }

  private def parseDoFor(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = block.inputs.toList
    val name = literalNameFromModelInputs(inputs).getOrElse("i")
    val rest = dropNameModelInput(inputs)
    val start = valueFromModelInputs(rest, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("1"), None))
    val endInputs = rest match
      case _ :: tail => tail
      case Nil => Nil
    val end = valueFromModelInputs(endInputs, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("1"), None))
    val body = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeFor(currentVars.intern(name), start, end, body)
  }

  private def parseDoIfElse(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = block.inputs.toList
    val condition = conditionFromModelInputs(inputs, phaseOne)
    val scripts = inputs.collect { case NestedScript(script) => script }
    val thenBody = scripts.headOption.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    val elseBody = scripts.drop(1).headOption.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeIfElse(BeSequence.conditionalBody(List(condition)), thenBody, elseBody)
  }

  private def parseDoIf(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = block.inputs.toList
    val condition = conditionFromModelInputs(inputs, phaseOne)
    val thenBody = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeIfElse(BeSequence.conditionalBody(List(condition)), thenBody, BeSequence.optionalBody(Nil))
  }

  private def parseDoUntil(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val inputs = block.inputs.toList
    val condition = SnapControlFlow.invertCondition(conditionFromModelInputs(inputs, phaseOne))
    val body = inputs.collectFirst { case NestedScript(script) => script }.map(parseScriptBody(_, phaseOne)).getOrElse(BeSequence.optionalBody(Nil))
    BeWhile(BeSequence.conditionalBody(List(condition)), body)
  }

  private def parseDoSetVar(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val (name, valueInputs) = assignmentNameAndRestModel(block.inputs.toList)
    val value = valueFromModelInputs(valueInputs, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
    SnapControlFlow.assignVariable(currentVars.intern(name), value)
  }

  private def parseDoChangeVar(block: BlockLike, phaseOne: PhaseOneResult): BeExpression = {
    val (name, amountInputs) = assignmentNameAndRestModel(block.inputs.toList)
    val amount = valueFromModelInputs(amountInputs, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("1"), None))
    SnapControlFlow.changeVariable(currentVars.intern(name), amount)
  }

  private def assignmentNameAndRestModel(inputs: List[InputValue]): (String, List[InputValue]) =
    inputs match
      case Literal(value) :: rest if value.trim.nonEmpty => (value.trim, rest)
      case NestedBlock(block) :: rest =>
        reporterVariableName(block) match
          case Some(name) => (name, rest)
          case None => (literalNameFromModelInputs(inputs).getOrElse("x"), dropNameModelInput(inputs))
      case other => (literalNameFromModelInputs(other).getOrElse("x"), dropNameModelInput(other))

  private def reporterVariableName(block: BlockLike): Option[String] = block match
    case PrimitiveBlock(None, Some(name), _, _) if name.nonEmpty => Some(name)
    case PrimitiveBlock(Some("reportGetVar"), variable, inputs, _) => Some(variableNameFromReporter(variable, inputs))
    case _ => None

  private def literalNameFromModelInputs(inputs: List[InputValue]): Option[String] =
    inputs.collectFirst { case Literal(value) if value.trim.nonEmpty => value.trim }

  private def dropNameModelInput(inputs: List[InputValue]): List[InputValue] =
    inputs match
      case Literal(_) :: rest => rest
      case other => other

  private def valueFromModelInputs(inputs: List[InputValue], phaseOne: PhaseOneResult): Option[BeExpression] =
    inputs.headOption.map {
      case Literal(value) => BeUseValue(BeDataValueLiteral(value), None)
      case BoolLiteral(value) => BeUseValue(BeDataValueLiteral(booleanRaw(value)), None)
      case NestedBlock(nested) => parseBlock(nested, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
      case ListLiteral(items) => valueFromModelInputs(items.toList, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("0"), None))
      case other => parseInputValue(other, None, phaseOne)
    }

  private def conditionFromModelInputs(inputs: List[InputValue], phaseOne: PhaseOneResult): BeExpression =
    inputs.collectFirst { case NestedBlock(nested) =>
      parseBlock(nested, phaseOne).getOrElse(BeUseValue(BeDataValueLiteral("True"), None))
    }.getOrElse(BeUseValue(BeDataValueLiteral("True"), None))

  private def parseScriptBody(script: Script, phaseOne: PhaseOneResult): BeSequence =
    BeSequence.optionalBody(commandStatements(script.blocks.toList.flatMap(block => parseBlock(block, phaseOne))))

  private def parseGenericBlock(block: BlockLike, phaseOne: PhaseOneResult): Option[BeExpression] = {
    val signature = signatureOf(block)
    val definition = phaseOne.definitionBySignature.get(signature)
    definition.map { defn =>
      val params = defn.inputs
      val values = block.inputs.toList
      val mapped = params.zipAll(values, null, Literal(""))
        .filter(_._1 != null)
        .map { case (parameter, input) =>
          parameter -> parseInputValue(input, Some(parameter), phaseOne)
        }.toMap
      BeFunctionCall.fromDefinition(defn, mapped)
    }
  }

  /** Snap booleans as the shared tree spells literals (`True`/`False`, as in the other front ends). */
  private def booleanRaw(value: Boolean): String = if value then "True" else "False"

  private def parseInputValue(input: InputValue, context: Option[BeDefineVariable], phaseOne: PhaseOneResult): BeExpression = input match {
    case Literal(value) => BeUseValue(BeDataValueLiteral(value), context)
    case BoolLiteral(value) => BeUseValue(BeDataValueLiteral(booleanRaw(value)), context)
    case ColorLiteral(value) => BeUseValue(BeDataValueLiteral(colorLiteralToPython(value)), context)
    case ListLiteral(items) =>
      val flattened = items.map {
        case Literal(v) => v
        case BoolLiteral(v) => booleanRaw(v)
        case _ => "item"
      }.mkString("[", ",", "]")
      BeUseValue(BeDataValueLiteral(flattened), context)
    case NestedScript(script) => parseScriptBody(script, phaseOne)
    case NestedBlock(block) => parseBlock(block, phaseOne).getOrElse(BeExpression.pass)
  }

  private def collectSignatures(project: Project): List[Signature] = project.allBlocks.toList.map(signatureOf).distinct


  /** A block plus every block nested in its slots, scripts and lists. */
  private def signatureOf(block: BlockLike): Signature = block match {
    case PrimitiveBlock(selector, variable, inputs, _) =>
      val name = selector.orElse(variable).getOrElse("block")
      Signature(name, inputs.size.max(0), OperatorSymbols.contains(name))
    case CustomBlockCall(spec, _, inputs, _, _) =>
      Signature(customSignatureName(spec), inputs.size.max(0), false, isCustom = true)
  }

  private def customSignatureName(spec: String): String =
    SnapTurtleCatalog.pythonNameFromCustomSpec(spec)

  private def colorLiteralToPython(value: TurtleStitchProgramModel.Rgba): String = {
    val raw = s"${value.r},${value.g},${value.b},${value.a}"
    SnapInputCodec.pythonQuotedColorFromRaw(raw).getOrElse(SnapInputCodec.quotePython(raw))
  }

  private def createDefinition(signature: Signature): BeDefineFunction = {
    val params = (1 to signature.arity).toList.map { idx =>
      BeDefineVariable(BeEntityName.fromCodeString(s"arg$idx"), BeDataType.AnyType)
    }
    val displayName = SnapTurtleCatalog.canonicalPythonName(signature.name)
    val entityName = if signature.isCustom then BeEntityName.fromUniversalNameInParts(displayName)
      else it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.resolveCanonicalNames(displayName)
        .getOrElse(BeEntityName.fromUniversalNameInParts(displayName))

    if (signature.isOperator)
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.operatorInfo(signature.name, if signature.arity <= 1 then 0 else 1))
    else if !signature.isCustom then
      it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand.resolveTurtleMethod(displayName)
        .map(_.copy(inputs = params))
        // Library blocks bind to the same built-in definitions as Python and Java calls.
        .orElse(it.evadid.vm.conversion.parsed.DefaultDefinitions.builtinFunctionDefinitions.collectFirst {
          case (`displayName`, builtin) if builtin.inputs.size == params.size => builtin
        })
        .getOrElse(BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.functionInfo(entityName)))
    else
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.functionInfo(entityName))
  }
}

}
