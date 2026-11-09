package it.evadid.workbook.abstractions.grading

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.elements.interactionElements.gpt.TextInputLlmGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateShapeGrader
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

trait WorkbookInteractionGrader[T, A] {
  protected def identityContent: ujson.Value = it.evadid.core.util.crypto.ContentIdentity.json(this)
  private lazy val contentKey: String = it.evadid.core.util.crypto.ContentIdentity.key(identityContent)
  def key: String = contentKey

  given ec: ExecutionContext = ExecutionContext.global

  private def failWith(state: T, err: Either[String, Throwable]): Future[GradingResult[T, A]] = err.match {
    case Left(str) => Future.successful(GradingResult[T, A](this, state, TestError(SerializedException(str))))
    case Right(err) => Future.successful(GradingResult[T, A](this, state, TestError(SerializedException(err))))
  }

  def gradeStateSafe(state: T, forceGrading: Boolean, contextInformation: ExecutionContextInformation): Future[GradingResult[T, A]] =
    try {
      if (runningBehavior == RUN_ALWAYS || forceGrading) handleGrading(state, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }
      else Future.successful(GradingResult[T, A](this, state, NotRequested()))
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }


  protected def handleGrading(state: T, contextInformation: ExecutionContextInformation): Future[GradingResult[T, A]]

  def importance: GradingImportance

  def runningBehavior: GraderRunningBehavior

  def gradingEntity: FeedbackEntity

}


object WorkbookInteractionGrader {

  type WorkbookInteractionGraderUntyped = WorkbookInteractionGrader[?, ?]

  private val knownGraderReadWrites: Map[String, Serializer[WorkbookInteractionGraderUntyped]] = Map(
    Serializer.serializerForSubclass(classOf[SlideshowGrader]),
    Serializer.serializerForSubclass(classOf[RecreateShapeGrader]),
    Serializer.serializerForSubclass(classOf[TextInputLlmGrader]),
    Serializer.serializerForSubclass(classOf[ProgrammingUnitTestGrader])
  )

  val universalSerializer: Serializer[WorkbookInteractionGraderUntyped] = Serializer.universalTraitSerializer(knownGraderReadWrites)

  given [T: ReadWriter, A: ReadWriter]: ReadWriter[WorkbookInteractionGrader[T, A]] = WorkbookInteractionGrader.universalSerializer.deriveForSubclass[WorkbookInteractionGrader[T, A]].uPickleReadWrite


}
