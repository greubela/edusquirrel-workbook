package datastructures.core.vm.io

import datastructures.core.language.{HumanLanguage, ProgrammingLanguage}
import datastructures.core.vm.code.BeExpression
import datastructures.core.vm.code.tree.BeExpressionNode
import datastructures.core.vm.types.{BeChildRole, BeScope}
import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

trait BeExpressionIO {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

  def createBlock(): BeBlock = ???
}
