package it.evadid.vm.parsing.generic.abstractions.pipeline

import it.evadid.vm.parsing.generic.abstractions.{CodeIdentifierScanResult, GenericAST}

trait CodeIdentifierScanner[T <: GenericAST, RI <: GenericAST, RC <: GenericAST, RF <: GenericAST, RV <: GenericAST] {

  def scanForIdentifier(ast: T): CodeIdentifierScanResult[RI, RC, RF, RV]

}
