package it.evadid.workbook.abstractions.scaffolding


import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat, StateReference}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.*
import upickle.default.*

import java.time.Instant
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.ScaffoldingError

case class ScaffoldingResult[T, SA](
                                    forState: T,
                                    scaffoldingStatus: ScaffoldingStatus,
                                    scaffoldingEntity: FeedbackEntity,
                                    additionalInformation: Option[SA],
                                    scaffoldingTime: Instant = Instant.MIN
                                  ) {
  /** This hint as saved in a history, with its answer's `state` reference. */
  def record(state: StateReference): ScaffoldingRecord[SA] =
    ScaffoldingRecord(state, scaffoldingStatus, scaffoldingEntity, additionalInformation, scaffoldingTime)

  def merge(other: ScaffoldingResult[T, SA]): ScaffoldingResult[T, SA] = {
    require(forState == other.forState, "Only scaffolding for the same answer can be merged.")
    ScaffoldingResult.merged(this, other)(_.scaffoldingStatus, _.scaffoldingTime, (hint, status) => hint.copy(scaffoldingStatus = status))
  }
}

/** A hint as saved in a history. It refers to its answer instead of copying it; a reader resolves it with the
  * answer it asks about.
  */
case class ScaffoldingRecord[SA](
  state: StateReference,
  scaffoldingStatus: ScaffoldingStatus,
  scaffoldingEntity: FeedbackEntity,
  additionalInformation: Option[SA],
  scaffoldingTime: Instant
) derives ReadWriter {
  def resolve[T](forState: T): ScaffoldingResult[T, SA] =
    ScaffoldingResult(forState, scaffoldingStatus, scaffoldingEntity, additionalInformation, scaffoldingTime)

  def merge(other: ScaffoldingRecord[SA]): ScaffoldingRecord[SA] = {
    require(state == other.state, "Only scaffolding for the same answer can be merged.")
    ScaffoldingResult.merged(this, other)(_.scaffoldingStatus, _.scaffoldingTime, (record, status) => record.copy(scaffoldingStatus = status))
  }
}


object ScaffoldingResult {
  /** Two hints for one answer: feedback beats an error, then the later one wins; it keeps the other's feedback in
    * languages it lacks.
    */
  private[scaffolding] def merged[R](first: R, second: R)(status: R => ScaffoldingStatus, time: R => Instant,
      withStatus: (R, ScaffoldingStatus) => R): R = {
    val preferred = (status(first), status(second)) match {
      case (_: ScaffoldingFeedback, _: ScaffoldingFeedback) => if time(second).isAfter(time(first)) then second else first
      case (_: ScaffoldingFeedback, _) => first
      case (_, _: ScaffoldingFeedback) => second
      case _ => if time(second).isAfter(time(first)) then second else first
    }
    val remaining = if preferred == first then second else first
    (status(remaining), status(preferred)) match {
      case (ScaffoldingFeedback(previous), ScaffoldingFeedback(current)) =>
        val languages = it.evadid.core.datastructures.language.AppLanguage.humanLanguages
        if languages.forall(language => previous.tryGetInLanguage(language).isEmpty || current.tryGetInLanguage(language).nonEmpty) then preferred
        else withStatus(preferred, ScaffoldingFeedback(it.evadid.core.datastructures.language.LanguageMap.mapBasedLanguageMap(
          languages.flatMap(language => current.tryGetInLanguage(language).orElse(previous.tryGetInLanguage(language)).map(language -> _)).toMap)))
      case _ => preferred
    }
  }

  def apply[T, SA](forState: T,
                  scaffoldingStatus: ScaffoldingStatus,
                  scaffoldingEntity: FeedbackEntity,
                  additionalInformation: Option[SA]): ScaffoldingResult[T, SA] = ScaffoldingResult[T, SA](forState, scaffoldingStatus, scaffoldingEntity, additionalInformation, Instant.now())

  def forError[T, SA](state: T, errEither: Either[String, Throwable], entity: FeedbackEntity): ScaffoldingResult[T, SA] = {
    val error = errEither.fold(SerializedException(_), identity)
    ScaffoldingResult(state, ScaffoldingError(error), entity, None)
  }

}
