package it.evadid.workbook.meta

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus}
import ScaffoldingStatus.*

/** Current-answer hint status, shared by every feedback view. */
final case class ScaffoldingReport(saved: Option[ScaffoldingResult[?, ?]]) {
  import ScaffoldingReport.Status
  private val result = saved.map(_.scaffoldingStatus)
  val status: Status = result match {
    case None => Status.NotRequested
    case Some(_: ScaffoldingRequestedButNotFinished) => Status.Pending
    case Some(_: ScaffoldingError) => Status.Error
    case Some(_: ScaffoldingFeedback) => Status.Ready
  }
  val feedback: Option[LanguageMap[HumanLanguage]] = result.collect { case ScaffoldingFeedback(content) => content }
  val isAiHint: Boolean = feedback.nonEmpty && saved.exists(_.scaffoldingEntity == FeedbackEntity.LlmEntity)
}

object ScaffoldingReport {
  enum Status {
    case NotRequested, Pending, Error, Ready
  }
}
