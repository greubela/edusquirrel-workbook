package it.evadid.vm.naming

import upickle.default.*

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, ProgrammingLanguage}

case class CodeRepresentationConfig(
    programmingLanguage: ProgrammingLanguage,
    humanLanguage: HumanLanguage,
    namingStyle: NamingStyle = NamingStyle.SnakeCase,
    skipUnparsable: Boolean = false
) derives ReadWriter {

}
