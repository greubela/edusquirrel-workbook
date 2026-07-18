package it.evadid.vm.parsing.abstractions.pipeline

import it.evadid.vm.parsing.abstractions.CodeIdentifierScanResult

trait IdentifierScanner {

  def scanPythonCodeForIdentifier(pythonString: String): CodeIdentifierScanResult

}
