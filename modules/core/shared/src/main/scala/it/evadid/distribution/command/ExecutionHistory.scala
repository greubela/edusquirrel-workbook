package it.evadid.distribution.command

import java.time.LocalDateTime

case class ExecutionHistory(
                             timestampCommandRequested: LocalDateTime,
                             timestampCommandReceived: LocalDateTime,
                             timestampExecutionStarted: LocalDateTime,
                             timestampExecutionFinished: LocalDateTime,
                           ) {


  override def toString: String = s"ExecutionHistory(req=$timestampCommandRequested, rec=$timestampCommandReceived, sta=$timestampExecutionStarted, fin=$timestampExecutionFinished)"

}
