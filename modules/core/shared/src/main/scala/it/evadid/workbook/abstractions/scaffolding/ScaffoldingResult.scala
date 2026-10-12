package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import upickle.default.*


case class ScaffoldingResult[T, A](
                                 forState: T,
                                 scaffoldingResult: LanguageMap[HumanLanguage],
                                 scaffoldingEntity: FeedbackEntity,
                                 additionalInformation: Option[A]
                               ) derives ReadWriter
