package contentmanagement.model.vm.code.defining

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.code.{BeDefineStructure, BeExpression}
import contentmanagement.model.vm.types.{BeChildPosition, BeChildRole, BeInfo, BeScope}
import interactionPlugins.blockEnvironment.config.BeTreeDisplayConfig
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

case class BeManualDefinition(
                               override val definedClasses: List[BeDefineClass],
                               override val definedFunctions: List[BeDefineFunction],
                               override val definedVariables: List[BeDefineVariable]) extends BeDefineStructure {

  /*override def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String =
    allDefinedStructures.map(_.expressionIO.getInLanguage(programmingLanguage, humanLanguage)).mkString("\n")


  override def createBlock(): BeBlock =
    throw new NotImplementedError("Block rendering is not implemented for BeManualDefinition")

  override def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression = this*/

  def getChildren(withExtensions: Boolean, parentScope: BeScope): List[BeExpressionNode] = allDefinedStructures.flatMap(curStruc => curStruc.getChildren(withExtensions, parentScope))

}
