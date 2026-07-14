package it.evadid.vm.naming

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, ProgrammingLanguage}

case class CodeRepresentationConfig(
    programmingLanguage: ProgrammingLanguage,
    humanLanguage: HumanLanguage,
    namingStyle: NamingStyle = NamingStyle.SnakeCase,
    skipUnparsable: Boolean = false
) {

}
