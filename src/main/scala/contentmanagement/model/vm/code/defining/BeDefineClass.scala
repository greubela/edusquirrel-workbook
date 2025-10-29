package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeDefineClass(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction]) extends BeDefineStructure{

  override def definedClasses: List[BeDefineClass] = List(this)

  override def definedFunctions: List[BeDefineFunction] = methods

  override def definedVariables: List[BeDefineVariable] = attributes
  
  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  override def getSyntaxErrors: Seq[BeInfo] =
    if(methods.exists(_.signature.methodOnObject.isEmpty)) List(
      BeInfo(LanguageMap.universalMap("Method must have an object it´s called on!"), BeInfo.SyntaxError.StructureMismatch)
    )
    else List()

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = List()
  
}
