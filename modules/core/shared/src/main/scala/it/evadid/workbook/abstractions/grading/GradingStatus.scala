package it.evadid.workbook.abstractions.grading

import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait GradingStatus derives ReadWriter {

  def isCorrect: Boolean = false
  def isError: Boolean = false
  def shouldRetryExecution: Boolean = false

}

object GradingStatus {

  case class Correct() extends GradingStatus derives upickle.default.ReadWriter {
    override def isCorrect: Boolean = true
  }

  case class PartiallyCorrect() extends GradingStatus derives upickle.default.ReadWriter

  case class Incorrect() extends GradingStatus derives upickle.default.ReadWriter

  case class TestError(err: SerializedException) extends GradingStatus derives upickle.default.ReadWriter {
    override def isError: Boolean = true
    override def shouldRetryExecution: Boolean = true
  }

  case class NotFinished() extends GradingStatus derives upickle.default.ReadWriter {

  }

  case class NotRequested() extends GradingStatus {

    override def shouldRetryExecution: Boolean = true
  }


}