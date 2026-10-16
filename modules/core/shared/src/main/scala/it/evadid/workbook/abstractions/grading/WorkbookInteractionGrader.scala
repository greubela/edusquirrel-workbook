package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionBoundComponent}
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingStatus.{NotExecuted, TestError}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateShapeGrader
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

trait WorkbookInteractionGrader[T, A] extends InteractionBoundComponent[T] {
  given ec: ExecutionContext = ExecutionContext.global

  def gradeStateSafe(state: T, forceGrading: Boolean): Future[GradingResult[T, A]] =
    if (forceGrading || runningBehavior == RUN_ALWAYS) {
      gradeState(state).recover {
        case (err: Throwable) => GradingResult[T, A](this, state, TestError(SerializedException(err)))
      }
    } else {
      Future.successful(new GradingResult[T, A](this, state, NotExecuted()))
    }

  def gradeState(state: T): Future[GradingResult[T, A]]

  def importance: GradingImportance

  def runningBehavior: GraderRunningBehavior

  def gradingEntity: FeedbackEntity

}


object WorkbookInteractionGrader {

  type WorkbookInteractionGraderUntyped = WorkbookInteractionGrader[?, ?]

  private val knownGraderReadWrites: Map[String, Serializer[WorkbookInteractionGraderUntyped]] = Map(
    Serializer.serializerForSubclass(classOf[SlideshowGrader]),
    Serializer.serializerForSubclass(classOf[RecreateShapeGrader])
  )

  val universalSerializer: Serializer[WorkbookInteractionGraderUntyped] = Serializer.universalTraitSerializer(knownGraderReadWrites)

  given[T: ReadWriter, A: ReadWriter]: ReadWriter[WorkbookInteractionGrader[T, A]] = WorkbookInteractionGrader.universalSerializer.deriveForSubclass[WorkbookInteractionGrader[T, A]].uPickleReadWrite


}
