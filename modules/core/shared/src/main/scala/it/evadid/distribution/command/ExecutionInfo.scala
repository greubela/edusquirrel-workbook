package it.evadid.distribution.command

import it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess

import java.time.LocalDateTime

sealed trait ExecutionInfo[T] {

  val command: ExecutionCommand
  val resultOp: Option[T]
  val historyOp: Option[ExecutionHistory]

  def withFixedTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionInfo[T]

  def successOrThrow(): ExecutionSuccess[T]
}

object ExecutionInfo {

  case class ExecutionSuccess[T](command: ExecutionCommand, history: ExecutionHistory, result: T) extends ExecutionInfo[T] {
    override val resultOp: Option[T] = Some(result)
    override val historyOp: Option[ExecutionHistory] = Some(history)

    override def withFixedTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionInfo[T] = this.copy(history = history.withFixedTime(timeRequested, timeReceived))

    override def successOrThrow(): ExecutionSuccess[T] = this
  }

  case class ExecutionFailure[T](command: ExecutionCommand, historyOp: Option[ExecutionHistory], error: SerializedException) extends ExecutionInfo[T] {
    override val toString: String = s"ExecutionFailure(command=${command.name}, error=${error.msg}, history=${historyOp.map(_.toString).getOrElse("None")}"
    override val resultOp: Option[T] = None

    override def withFixedTime(timeRequested: LocalDateTime, timeReceived: LocalDateTime): ExecutionInfo[T] = this.copy(historyOp = historyOp.map(curHistory => curHistory.withFixedTime(timeRequested, timeReceived)))

    override def successOrThrow(): ExecutionSuccess[T] = throw error
  }

}
