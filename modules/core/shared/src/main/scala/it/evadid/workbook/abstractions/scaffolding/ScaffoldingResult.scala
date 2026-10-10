package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity

trait ScaffoldingResult[T] {

  def forState: T

  def scaffoldingResult: LanguageMap[HumanLanguage]

  def scaffoldingEntity: FeedbackEntity
}
