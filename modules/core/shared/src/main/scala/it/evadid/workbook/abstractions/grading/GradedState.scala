package it.evadid.workbook.abstractions.grading

import java.time.LocalDateTime
import upickle.default.*
case class GradedState[T, A]
(
  state: T,
  testsWithResults: Set[GradingResult[T, A]],
) derives ReadWriter {


  val timestampLatestTest: LocalDateTime = testsWithResults.map(_.gradingTime).max

}

object GradedState {


}
