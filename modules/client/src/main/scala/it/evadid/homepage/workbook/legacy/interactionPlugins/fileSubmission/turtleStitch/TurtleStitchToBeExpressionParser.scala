package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import it.evadid.workbook.vm.naming.BeEntityName
import TurtleStitchProgramModel.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.controlStructures.BeSequence
import it.evadid.workbook.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.workbook.vm.code.others.BeStartProgram
import it.evadid.workbook.vm.code.usage.{BeFunctionCall, BeUseValue}
import it.evadid.workbook.vm.types.{BeDataType, BeDataValueLiteral}

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
      val phaseTwoExpressions = parsePhaseTwo(project, phaseOne)
      val completeBody = phaseOne.orderedDefinitions ++ phaseTwoExpressions
      BeStartProgram(BeSequence.optionalBody(completeBody))
    }.getOrElse(BeStartProgram(BeSequence.optionalBody(Nil)))
  }

  def parseXml(xml: String): BeExpression = parseProject(TurtleStitchXmlLoader.load(xml))

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
      BeDefineVariable(LanguageMap.universalMap[HumanLanguage](s"arg$idx"), BeDataType.AnyType)
    }

    if (signature.isOperator)
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.operatorInfo(signature.name, if (signature.arity <= 1) 0 else 1))
    else
      BeDefineFunction(params, None, BeExpression.pass, BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts(signature.name)))
  }
}
