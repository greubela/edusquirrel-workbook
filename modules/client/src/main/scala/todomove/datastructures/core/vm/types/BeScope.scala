package todomove.datastructures.core.vm.types

import todomove.datastructures.core.vm.code.controlStructures.BeSequence
import todomove.datastructures.core.vm.code.defining.{BeDefineClass, BeDefineFunction}

sealed trait BeScope {

  def parentScopes: List[BeScope]

  def isSubScope(other: BeScope): Boolean = other.parentScopes.contains(other) 
}

object BeScope {

  case class GlobalScope() extends BeScope {
    def parentScopes: List[BeScope] = List()
  }

  case class InFunctionScope(funcDef: BeDefineFunction, parentScope: BeScope) extends BeScope {
    def parentScopes: List[BeScope] = parentScope :: parentScope.parentScopes
  }

  case class InClassScope(classDef: BeDefineClass, parentScope: BeScope) extends BeScope {
    def parentScopes: List[BeScope] = parentScope :: parentScope.parentScopes
  }

  case class InSequenceScope(seq: BeSequence, parentScope: BeScope) extends BeScope {
    def parentScopes: List[BeScope] = parentScope :: parentScope.parentScopes
  }


}


