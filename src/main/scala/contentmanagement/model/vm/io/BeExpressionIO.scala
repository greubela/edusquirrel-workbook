package contentmanagement.model.vm.io

import contentmanagement.model.language.{HumanLanguage, ProgrammingLanguage}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.tree.BeExpressionNode
import contentmanagement.model.vm.types.{BeChildRole, BeScope}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

trait BeExpressionIO {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

  def createBlock(): BeBlock = ???
}
