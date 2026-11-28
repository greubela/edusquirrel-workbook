package it.evadid.workbook.abstractions.grading

import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait GradingStatus derives ReadWriter {

  def isCorrect: Boolean = false
  def isError: Boolean = false
  def shouldRetryExecution: Boolean = false

}

object GradingStatus {

  case object Correct extends GradingStatus {
    override def isCorrect: Boolean = true
  }

  case object PartiallyCorrect extends GradingStatus

  case object Incorrect extends GradingStatus

  object TestError {
    /** Saved errors keep only the message (with its causes'), never a stack trace. */
    def apply(error: Throwable): TestError = TestError(SerializedException.messageOf(error))
  }

  case class TestError(message: String) extends GradingStatus derives upickle.default.ReadWriter {
    override def isError: Boolean = true
    override def shouldRetryExecution: Boolean = true
  }

  case object NotFinished extends GradingStatus {

  }

  case object NotRequested extends GradingStatus {

    override def shouldRetryExecution: Boolean = true
  }


}