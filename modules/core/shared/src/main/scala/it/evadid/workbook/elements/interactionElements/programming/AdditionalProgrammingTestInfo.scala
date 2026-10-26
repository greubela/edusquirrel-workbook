package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import upickle.default.ReadWriter

case class AdditionalProgrammingTestInfo(
    humanReadableTestExecutionDescription: LanguageMap[HumanLanguage]
) derives ReadWriter
