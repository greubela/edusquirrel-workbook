package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader.WorkbookInteractionGraderUntyped

import upickle.default.*

import java.time.LocalDateTime

import it.evadid.core.util.io.serializer.DefaultSerializer.given

case class GradingResult[T, A]
(
  grader: WorkbookInteractionGrader[T, A],
  gradedState: T,
  gradingStatus: GradingStatus,
  feedbackInformation: Option[LanguageMap[HumanLanguage]] = None,
  additionalData: Option[A] = None,
  @upickle.implicits.serializeDefaults(true) gradingTime: LocalDateTime = LocalDateTime.now(),
) derives ReadWriter

object GradingResult {

  type GradingResultUntyped = GradingResult[?, ?]





}


