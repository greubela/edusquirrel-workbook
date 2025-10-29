package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.code.usage.{BeUseUnitValue, BeUseValue}
import contentmanagement.model.vm.simulation.{BeSimulatorConfig, BeSimulatorState}
import contentmanagement.model.vm.types.*
import contentmanagement.model.vm.types.BeChildRole.BodySequence
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock
import interactionPlugins.blockEnvironment.programming.blocks.parents.BeBlockDefineSingleReturnFunction

case class BeDefineFunction(signature: BeFunctionSignature, body: BeExpression) extends BeDefineStructure {

  override def getSyntaxErrors: Seq[BeInfo] = List()
  /*
    if (!body.canEvaluateTo.exists(curPossibleReturnValue => BeDataType.validForType(body.canEvaluateTo, curPossibleReturnValue))) {
      List(BeInfo(LanguageMap.universalMap("Function Signature Requires [" + canEvaluateTo.mkString(", ") + "] but body returns one of [" + body.canEvaluateTo + "]"), BeInfo.SyntaxError.TypeMismatch))
    } else {
      List()
    }
  }*/


  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???
  
  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = BeBlockDefineSingleReturnFunction(this, parentPos)

  override protected def changedScopeForChildren(parentScope: BeScope): BeScope = BeScope.InFunctionScope(this)

  override def getChildren: List[(BeChildRole, BeExpression)] = {
    List((BodySequence(0), body))
  }

  override val toString: String = {
    s"""BeDefineStaticFunction(
       |  ${signature.parameter.map(_.canEvaluateTo.toString).mkString("(", ", ", ")")} => ${signature.returnValue.map(_.toString).getOrElse("()")},
       |  $body
       |)""".stripMargin
  }

  
}

