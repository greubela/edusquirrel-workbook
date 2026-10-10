package it.evadid.workbook.abstractions.grading

import scala.concurrent.Future

trait WorkbookInteractionGrader[T, G <: GradingResult[T]] {

  def gradeState(state: T): Future[G]

  def importance: GradingImportance

  def runningBehavior: GraderRunningBehavior

}
