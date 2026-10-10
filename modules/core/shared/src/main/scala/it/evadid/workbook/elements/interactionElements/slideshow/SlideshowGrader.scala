package it.evadid.workbook.elements.interactionElements.slideshow

import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import upickle.default.*

import scala.concurrent.Future

case class SlideshowGrader() extends WorkbookInteractionGrader[SlideshowState, SlideshowGradingResult] {

  override def gradeState(state: SlideshowState): Future[SlideshowGradingResult] = Future.successful(SlideshowGradingResult(state))

}
