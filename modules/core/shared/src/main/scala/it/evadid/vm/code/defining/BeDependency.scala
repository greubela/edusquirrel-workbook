package it.evadid.vm.code.defining

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.naming.BeEntityName

/** A library a program relies on, with the source each language needs so that a printed program runs
  * unchanged outside the workbook: an import, a helper function or a helper class. A language without
  * an entry provides the library itself, as Snap does for its turtle.
  *
  * Printers add the definitions a program needs; parsers remove definitions that match exactly, so a
  * dependency never becomes a user definition. The known dependencies are listed in
  * [[it.evadid.vm.conversion.dependencies.KnownDependencies]].
  */
final case class BeDependency(structureName: BeEntityName, addDefinitions: Map[ProgrammingLanguage, String]) derives upickle.default.ReadWriter {
  def definition(language: ProgrammingLanguage): Option[String] = addDefinitions.get(language)
}
