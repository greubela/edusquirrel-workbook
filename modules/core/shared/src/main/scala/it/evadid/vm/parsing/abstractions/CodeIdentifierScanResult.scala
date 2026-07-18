package it.evadid.vm.parsing.abstractions

import it.evadid.vm.parsing.abstractions.CodeIdentifierScanResult.{RecognizedClass, RecognizedFunction, RecognizedImport, RecognizedVariables}

case class CodeIdentifierScanResult(
                               identifiedImports: List[RecognizedImport],
                               identifiedClasses: List[RecognizedClass],
                               identifiedFunction: List[RecognizedFunction],
                               identifiedVariables: List[RecognizedVariables],
                             ) {

}


object CodeIdentifierScanResult {

  case class RecognizedClass(classIdentifier: String, methodIdentifier: List[RecognizedFunction], attributeIdentifier: List[String])

  case class RecognizedImport(importIdentifier: String)

  case class RecognizedFunction(functionIdentifier: String, parameterIdentifier: List[RecognizedVariables], returnTypeString: Option[String])

  case class RecognizedVariables(functionIdentifier: String, variableTypeString: Option[String], variableValueString: Option[String])

}

