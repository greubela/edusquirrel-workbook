package it.evadid.vm.parsing.generic.abstractions

import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.naming.BeEntityName

trait EntityDefinitionStore {

  private def definedClasses: Map[BeEntityName, BeDefineClass] = Map()

  private def definedFunctions: Map[BeEntityName, BeDefineFunction] = Map()

  private def definedVariables: Map[BeEntityName, BeDefineVariable] = Map()


}
