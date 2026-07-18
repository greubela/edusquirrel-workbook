package it.evadid.vm.parsing.generic.abstractions.pipeline

import it.evadid.vm.parsing.generic.abstractions.{CodeIdentifierScanResult, GenericAST}

trait IdentifierScanner[T <: GenericAST, RI <: GenericAST, RC <: GenericAST, RF <: GenericAST, RV <: GenericAST] {

  def scanPythonCodeForIdentifier(ast: T): CodeIdentifierScanResult[RI, RC, RF, RV]

}
