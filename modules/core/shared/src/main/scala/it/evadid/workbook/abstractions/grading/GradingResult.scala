package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity

object GradingResult {


}

trait GradingResult[T] {

  val stateToGrade: T
  val gradingEntity: FeedbackEntity
  val gradingGrade: GradingGrade
  val additionalInformation: LanguageMap[HumanLanguage]

}
