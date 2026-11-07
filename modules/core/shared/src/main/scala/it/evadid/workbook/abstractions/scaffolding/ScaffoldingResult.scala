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
                                  ) derives ReadWriter


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
