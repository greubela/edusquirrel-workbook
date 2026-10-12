package it.evadid.vm.parsing.generic.abstractions

import it.evadid.vm.parsing.generic.abstractions.CodeIdentifierScanResult.{RecognizedClass, RecognizedFunction, RecognizedImport, RecognizedVariables}

case class CodeIdentifierScanResult[RI <: GenericAST, RC <: GenericAST, RF <: GenericAST, RV <: GenericAST](
                                                                                                             identifiedImports: List[RecognizedImport[RI]],
                                                                                                             identifiedClasses: List[RecognizedClass[RC, RF, RV]],
                                                                                                             identifiedFunction: List[RecognizedFunction[RF, RV]],
                                                                                                             identifiedVariables: List[RecognizedVariables[RV]],
                                                                                                           ) derives upickle.default.ReadWriter {
}

object CodeIdentifierScanResult {

  case class RecognizedImport[RI <: GenericAST](importIdentifier: String, ast: RI) derives upickle.default.ReadWriter

  case class RecognizedClass[RC <: GenericAST, RF <: GenericAST, RV <: GenericAST](classIdentifier: String, methodIdentifier: List[RecognizedFunction[RF, RV]], attributeIdentifier: List[RecognizedVariables[RV]], ast: RC) derives upickle.default.ReadWriter

  case class RecognizedFunction[RF <: GenericAST, RV <: GenericAST](functionIdentifier: String, parameterIdentifier: List[RecognizedVariables[RV]], returnTypeString: Option[String], ast: RF) derives upickle.default.ReadWriter

  case class RecognizedVariables[RV <: GenericAST](functionIdentifier: String, variableTypeString: Option[String], variableValueString: Option[String], ast: RV) derives upickle.default.ReadWriter

}

