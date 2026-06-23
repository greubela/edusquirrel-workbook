package it.evadid.workbook.vm.naming

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, ProgrammingLanguage}

case class CodeRepresentationConfig(programmingLanguage: ProgrammingLanguage, humanLanguage: HumanLanguage, namingStyle: NamingStyle) {

}
