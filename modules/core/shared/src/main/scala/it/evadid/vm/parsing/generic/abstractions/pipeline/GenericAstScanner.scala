package it.evadid.vm.parsing.generic.abstractions.pipeline

import it.evadid.vm.parsing.generic.abstractions.GenericAST

trait GenericAstScanner[T <: GenericAST] {

  def parseASTFromProgramString(programString: String): Either[Throwable, T]

}
