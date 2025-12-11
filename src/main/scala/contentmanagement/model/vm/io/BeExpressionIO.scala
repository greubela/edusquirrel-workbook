package contentmanagement.model.vm.io

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.types.BeChildRole
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

trait BeExpressionIO {
  
  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String
  
  def withReplacedChildren(newChildren: List[(BeChildRole, BeExpression)]): BeExpression

  def createBlock(): BeBlock
}
