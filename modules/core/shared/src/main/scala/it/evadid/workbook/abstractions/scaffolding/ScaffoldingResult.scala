package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import upickle.default.*
import java.time.LocalDateTime


case class ScaffoldingResult[T, A](
                                 forState: T,
                                 scaffoldingResult: LanguageMap[HumanLanguage],
                                 scaffoldingEntity: FeedbackEntity,
                                 additionalInformation: Option[A],
                                 scaffoldingTime: LocalDateTime = LocalDateTime.now()
                               )

object ScaffoldingResult {
  private case class Saved[T, A](forState: T, scaffoldingResult: LanguageMap[HumanLanguage],
      scaffoldingEntity: FeedbackEntity, additionalInformation: Option[A],
      scaffoldingTime: LocalDateTime = LocalDateTime.MIN) derives ReadWriter

  // Old hints must not acquire a fresh timestamp each time their history is read.
  given [T: ReadWriter, A: ReadWriter]: ReadWriter[ScaffoldingResult[T, A]] = readwriter[Saved[T, A]].bimap(
    value => Saved(value.forState, value.scaffoldingResult, value.scaffoldingEntity, value.additionalInformation, value.scaffoldingTime),
    value => ScaffoldingResult(value.forState, value.scaffoldingResult, value.scaffoldingEntity, value.additionalInformation, value.scaffoldingTime))
}
