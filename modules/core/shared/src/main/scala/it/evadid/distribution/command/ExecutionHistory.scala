package it.evadid.distribution.command

import it.evadid.core.util.io.serializer.DefaultSerializer.given

import java.time.Instant

case class ExecutionHistory(
                             timestampCommandRequested: Instant,
                             timestampCommandReceived: Instant,
                             timestampExecutionStarted: Instant,
                             timestampExecutionFinished: Instant,
                           ) {

  def withFixedTime(timeRequested: Instant, timeReceived: Instant): ExecutionHistory = this.copy(
    timestampCommandRequested = timeRequested,
    timestampCommandReceived = timeReceived
  )

  override def toString: String = s"ExecutionHistory(req=$timestampCommandRequested, rec=$timestampCommandReceived, sta=$timestampExecutionStarted, fin=$timestampExecutionFinished)"

}
