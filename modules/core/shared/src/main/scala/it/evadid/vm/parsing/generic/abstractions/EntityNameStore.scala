package it.evadid.vm.parsing.generic.abstractions

import it.evadid.vm.code.BeDefineStructure
import it.evadid.vm.naming.BeEntityName

trait EntityNameStore {

  def isEntityName(identifier: String): Option[BeEntityName]

}
