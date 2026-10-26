package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.LanguageMap
import scala.util.Try
import upickle.default.ReadWriter

case class AdditionalProgrammingTestInfo(
    humanReadableTestExecutionDescription: LanguageMap[HumanLanguage]
) derives ReadWriter

object AdditionalProgrammingTestInfo {
  private[evadid] def descriptions(value: LanguageMap[HumanLanguage]): Map[HumanLanguage, String] =
    Option(value).toList.flatMap { translations =>
      AppLanguage.humanLanguages.flatMap { language =>
        Try(translations.tryGetInLanguage(language)).toOption.flatten.flatMap(Option(_)).map(_.trim)
          .filter(text => text.nonEmpty && text.length <= 1024).map(language -> _)
      }
    }.toMap

  private[evadid] def fromPublicDescription(value: LanguageMap[HumanLanguage]): Option[AdditionalProgrammingTestInfo] = {
    val texts = descriptions(value)
    Option.when(texts.nonEmpty)(AdditionalProgrammingTestInfo(LanguageMap.mapBasedLanguageMap(texts)))
  }
}
