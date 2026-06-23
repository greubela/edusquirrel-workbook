package it.evadid.workbook.vm.naming

import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}
import it.evadid.workbook.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.workbook.vm.types.BeDataType

object VmDefsStore {


  private val knownClasses: Map[String, BeDefineClass] = Map()

  private val knownFunctions: Map[String, BeDefineFunction] = Map()




  private val turtleClass: BeDefineClass = {

    /*
        inputs: List[BeDefineVariable],
        outputs: Option[BeDefineVariable],
        body: BeSequence,
        functionTypeInfo: BeFunctionTypeInfo,
        indentWidth: Int = 4
     */
/*
    val forward = BeDefineFunction()
    val backward = BeDefineFunction
    val left = BeDefineFunction
    val right = BeDefineFunction
*/

    ???
  }

  def getLanguageMapByName(name: String): LanguageMapContentId = {
    ???
  }

  def getClassByName(name: String): Option[Any] = {
    ???
  }

  def getFunctionByName(name: String): Option[Any] = {
    ???
  }


}

