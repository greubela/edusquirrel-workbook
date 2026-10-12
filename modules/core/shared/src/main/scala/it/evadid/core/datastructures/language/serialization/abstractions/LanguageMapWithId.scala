package it.evadid.core.datastructures.language.serialization.abstractions

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMap, LanguageMapContentId}

import upickle.default.*



case class LanguageMapWithId(contentId: LanguageMapContentId, languageMap: LanguageMap[HumanLanguage]) derives ReadWriter
