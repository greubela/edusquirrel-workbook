package it.evadid.workbook.abstractions.scaffolding

import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.*
import upickle.default.*

import java.time.LocalDateTime
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.ScaffoldingError

case class ScaffoldingResult[T, A](
                                    forState: T,
                                    scaffoldingStatus: ScaffoldingStatus,
                                    scaffoldingEntity: FeedbackEntity,
                                    additionalInformation: Option[A],
                                    scaffoldingTime: LocalDateTime = LocalDateTime.MIN
                                  ) derives ReadWriter {
  def merge(other: ScaffoldingResult[T, A]): ScaffoldingResult[T, A] = {
    require(forState == other.forState, "Only scaffolding for the same answer can be merged.")
    val preferred = (scaffoldingStatus, other.scaffoldingStatus) match {
      case (_: ScaffoldingFeedback, _: ScaffoldingFeedback) => if other.scaffoldingTime.isAfter(scaffoldingTime) then other else this
      case (_: ScaffoldingFeedback, _) => this
      case (_, _: ScaffoldingFeedback) => other
      case _ => if other.scaffoldingTime.isAfter(scaffoldingTime) then other else this
    }
    val remaining = if preferred eq this then other else this
    (remaining.scaffoldingStatus, preferred.scaffoldingStatus) match {
      case (ScaffoldingFeedback(previous), ScaffoldingFeedback(current)) =>
        val languages = it.evadid.core.datastructures.language.AppLanguage.humanLanguages
        if languages.forall(language => previous.tryGetInLanguage(language).isEmpty || current.tryGetInLanguage(language).nonEmpty) then preferred
        else preferred.copy(scaffoldingStatus = ScaffoldingFeedback(it.evadid.core.datastructures.language.LanguageMap.mapBasedLanguageMap(
          languages.flatMap(language => current.tryGetInLanguage(language).orElse(previous.tryGetInLanguage(language)).map(language -> _)).toMap)))
      case _ => preferred
    }
  }
}


object ScaffoldingResult {

  DefaultSerializer
  def apply[T, A](forState: T,
                  scaffoldingStatus: ScaffoldingStatus,
                  scaffoldingEntity: FeedbackEntity,
                  additionalInformation: Option[A]): ScaffoldingResult[T, A] = ScaffoldingResult[T, A](forState, scaffoldingStatus, scaffoldingEntity, additionalInformation, LocalDateTime.now())

  def forError[T, A](state: T, errEither: Either[String, Throwable], entity: FeedbackEntity): ScaffoldingResult[T, A] = {
    val err = errEither.match {
      case Left(str) => SerializedException(str)
      case Right(err) => SerializedException(err)
    }
    ScaffoldingResult(state, ScaffoldingError(err), entity, None)
  }

}
