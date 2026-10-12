package it.evadid.workbook.abstractions.grading

import it.evadid.distribution.command.SerializedException
import upickle.default.*

sealed trait GradingStatus derives ReadWriter {

}

object GradingStatus {

  case class Correct() extends GradingStatus derives upickle.default.ReadWriter

  case class PartiallyCorrect() extends GradingStatus derives upickle.default.ReadWriter

  case class Incorrect() extends GradingStatus derives upickle.default.ReadWriter

  case class TestError(err: SerializedException) extends GradingStatus derives upickle.default.ReadWriter

  case class NotExecuted() extends GradingStatus derives upickle.default.ReadWriter
}