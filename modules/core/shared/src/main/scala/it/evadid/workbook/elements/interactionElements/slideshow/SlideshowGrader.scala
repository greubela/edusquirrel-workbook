package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingImportance.{OPTIONAL_TEST, REQUIRED_TEST}
import it.evadid.workbook.abstractions.grading.{GraderRunningBehavior, GradingImportance, WorkbookInteractionGrader}
import upickle.default.*

import scala.concurrent.Future

case class SlideshowGrader(isRequired: Boolean) extends WorkbookInteractionGrader[SlideshowState, SlideshowGradingResult] derives ReadWriter{

  override def gradeState(state: SlideshowState): Future[SlideshowGradingResult] = Future.successful(SlideshowGradingResult(state))

  override def runningBehavior: GraderRunningBehavior = RUN_ALWAYS

  override def importance: GradingImportance = if(isRequired) REQUIRED_TEST else OPTIONAL_TEST
}
