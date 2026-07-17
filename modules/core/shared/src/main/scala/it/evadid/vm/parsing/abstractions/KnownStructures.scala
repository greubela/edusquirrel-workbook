package it.evadid.vm.parsing.abstractions

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction}

object KnownStructures {

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
