package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer.given

import java.time.LocalDateTime

case class ExecutionHistory(
                             timestampCommandRequested: LocalDateTime,
                             timestampCommandReceived: LocalDateTime,
                             timestampExecutionStarted: LocalDateTime,
                             timestampExecutionFinished: LocalDateTime,
                           ) {

  def withFixedTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionHistory = this.copy(
    timestampCommandRequested = timeRequested,
    timestampCommandReceived = timeReceived
  )

  override def toString: String = s"ExecutionHistory(req=$timestampCommandRequested, rec=$timestampCommandReceived, sta=$timestampExecutionStarted, fin=$timestampExecutionFinished)"

}
