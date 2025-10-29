package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, LanguageMap, ProgrammingLanguage}
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeInfo}
import interactionPlugins.blockEnvironment.config.BeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blocks.BeBlock

case class BeDefineClass(name: LanguageMap[HumanLanguage], attributes: List[BeDefineVariable], methods: List[BeDefineFunction]) extends BeDefineStructure {

  override def definedClasses: List[BeDefineClass] = List(this)

  override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ???

  override def getSyntaxErrors: Seq[BeInfo] = {

    methods.flatMap(curMethod => {
      val inClass = curMethod.functionTypeInfo.isMethodInClass
      if (inClass.isEmpty)
        Some(BeInfo(LanguageMap.universalMap("Method must have an object it´s called on!"), BeInfo.SyntaxError.StructureMismatch))
      else if (inClass.get != this)
        Some(BeInfo(LanguageMap.universalMap("Method must live in the class its defined in!"), BeInfo.SyntaxError.StructureMismatch))
      else None
    })
  }

  override def createBlock(config: BeDisplayConfig, parentPos: BeChildPosition): BeBlock = ???

  override def getChildren: List[(BeChildRole, BeExpression)] = List()

}
