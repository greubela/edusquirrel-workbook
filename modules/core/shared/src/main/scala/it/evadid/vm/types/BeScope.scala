package it.evadid.vm.types

import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction}
import upickle.default.*
sealed trait BeScope derives ReadWriter{

  def parentScopes: List[BeScope]

  def isSubScope(other: BeScope): Boolean = parentScopes.contains(other)
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
