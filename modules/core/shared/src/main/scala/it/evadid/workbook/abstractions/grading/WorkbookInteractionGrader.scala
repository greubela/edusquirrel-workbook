package it.evadid.workbook.abstractions.grading

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.util.io.Serializer
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingStatus.{NotExecuted, TestError}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateShapeGrader
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import it.evadid.workbook.elements.interactionElements.gpt.TextInputLlmGrader
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait WorkbookInteractionGrader[T, A] {
  given ec: ExecutionContext = ExecutionContext.global

  def gradeStateSafe(state: T, forceGrading: Boolean, availableExecutionClients: List[ExecutionClient]): Future[GradingResult[T, A]] =
    safely(state, forceGrading)(gradeState(state, availableExecutionClients))

  def gradeStateSafe(state: T, forceGrading: Boolean, availableExecutionClients: List[ExecutionClient], resolver: LanguageMapIdResolver): Future[GradingResult[T, A]] =
    safely(state, forceGrading)(gradeState(state, availableExecutionClients, resolver))

  private def safely(state: T, forceGrading: Boolean)(run: => Future[GradingResult[T, A]]): Future[GradingResult[T, A]] =
    if (forceGrading || runningBehavior == RUN_ALWAYS) {
      Try(run).fold(Future.failed, identity).recover {
        case (err: Throwable) => GradingResult[T, A](this, state, TestError(SerializedException(err)))
      }
    } else {
      Future.successful(new GradingResult[T, A](this, state, NotExecuted()))
    }

  def gradeState(state: T, availableExecutionClients: List[ExecutionClient]): Future[GradingResult[T, A]]

  /** Localized graders resolve authored content at request time, never in their saved configuration. */
  def gradeState(state: T, availableExecutionClients: List[ExecutionClient], resolver: LanguageMapIdResolver): Future[GradingResult[T, A]] =
    gradeState(state, availableExecutionClients)

  def importance: GradingImportance

  def runningBehavior: GraderRunningBehavior

  def gradingEntity: FeedbackEntity

}


object WorkbookInteractionGrader {

  type WorkbookInteractionGraderUntyped = WorkbookInteractionGrader[?, ?]

  private val knownGraderReadWrites: Map[String, Serializer[WorkbookInteractionGraderUntyped]] = Map(
    Serializer.serializerForSubclass(classOf[SlideshowGrader]),
    Serializer.serializerForSubclass(classOf[RecreateShapeGrader]),
    Serializer.serializerForSubclass(classOf[TextInputLlmGrader])
  )

  val universalSerializer: Serializer[WorkbookInteractionGraderUntyped] = Serializer.universalTraitSerializer(knownGraderReadWrites)

  given[T: ReadWriter, A: ReadWriter]: ReadWriter[WorkbookInteractionGrader[T, A]] = WorkbookInteractionGrader.universalSerializer.deriveForSubclass[WorkbookInteractionGrader[T, A]].uPickleReadWrite


}
