package it.evadid.workbook.abstractions.grading

import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait GradingStatus derives ReadWriter {

}

object GradingStatus {

  case class Correct() extends GradingStatus

  case class PartiallyCorrect() extends GradingStatus

  case class Incorrect() extends GradingStatus

  case class TestError(err: SerializedException) extends GradingStatus

  case class NotExecuted() extends GradingStatus
}