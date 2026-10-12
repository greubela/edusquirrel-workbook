package it.evadid.workbook.abstractions.grading

import upickle.default.*

import java.time.LocalDateTime

case class GradedState[T, A]
(
  state: T,
  testsWithResults: Set[GradingResult[T, A]],
) derives ReadWriter {


  val timestampLatestTest: LocalDateTime = testsWithResults.map(_.gradingTime).max


}

object GradedState {


}
