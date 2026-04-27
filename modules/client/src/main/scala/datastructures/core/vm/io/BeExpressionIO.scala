package datastructures.core.vm.io

import interactionPlugins.blockEnvironment.programming.blockdisplay.BeBlock

import it.evadid.core.datastructures.language.*
import it.evadid.core.datastructures.language.AppLanguage.*
trait BeExpressionIO {

  def getInLanguage(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage): String = ""

  def createBlock(): BeBlock = ???
}
