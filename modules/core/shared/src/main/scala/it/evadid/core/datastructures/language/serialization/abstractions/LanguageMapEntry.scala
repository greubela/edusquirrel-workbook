package it.evadid.core.datastructures.language.serialization.abstractions

import it.evadid.core.datastructures.language.AppLanguage.{HumanLanguage, SpecialLanguage}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}


case class LanguageMapEntry[T <: AppLanguage](contentId: LanguageMapContentId, language: T, value: String)

