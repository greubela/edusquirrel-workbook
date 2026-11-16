package it.evadid.workbook.abstractions.grading

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.grading.GraderRunningBehavior.RUN_ALWAYS
import it.evadid.workbook.abstractions.grading.GradingStatus.*
import it.evadid.workbook.elements.interactionElements.gpt.TextInputLlmGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingUnitTestGrader
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{RecreateShapeGrader, TurtleCommandUsageGrader}
import it.evadid.workbook.elements.interactionElements.slideshow.SlideshowGrader
import it.evadid.workbook.meta.ExecutionContextInformation
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}

trait WorkbookInteractionGrader[F <: InteractionFormat] {
  protected def identityContent: ujson.Value = it.evadid.core.util.crypto.ContentIdentity.json(this)
  private lazy val contentKey: String = it.evadid.core.util.crypto.ContentIdentity.key(identityContent)
  def key: String = contentKey

  given ec: ExecutionContext = ExecutionContext.global

  private def failWith(state: F#InteractionStateType, err: Either[String, Throwable]): Future[GradingResult[F]] = err.match {
    case Left(str) => Future.successful(GradingResult[F](this, state, TestError(SerializedException(str))))
    case Right(err) => Future.successful(GradingResult[F](this, state, TestError(SerializedException(err))))
  }

  def gradeStateSafe(state: F#InteractionStateType, forceGrading: Boolean, contextInformation: ExecutionContextInformation): Future[GradingResult[F]] =
    try {
      if (runningBehavior == RUN_ALWAYS || forceGrading) handleGrading(state, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }
      else Future.successful(GradingResult[F](this, state, NotRequested()))
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }

  protected def handleGrading(state: F#InteractionStateType, contextInformation: ExecutionContextInformation): Future[GradingResult[F]]

  def importance: GradingImportance

  def runningBehavior: GraderRunningBehavior

  def gradingEntity: FeedbackEntity

}


object WorkbookInteractionGrader {
/*
  private val knownGraderReadWrites: Map[String, Serializer[WorkbookInteractionGrader[InteractionFormat]]] = Map(
    Serializer.serializerForSubclass(classOf[SlideshowGrader]),
    Serializer.serializerForSubclass(classOf[RecreateShapeGrader]),
    Serializer.serializerForSubclass(classOf[TurtleCommandUsageGrader]),
    Serializer.serializerForSubclass(classOf[TextInputLlmGrader]),
    Serializer.serializerForSubclass(classOf[ProgrammingUnitTestGrader])
  )

  val universalSerializer: Serializer[WorkbookInteractionGrader[InteractionFormat]] = Serializer.universalTraitSerializer(knownGraderReadWrites)

  given [F <: InteractionFormat]: ReadWriter[WorkbookInteractionGrader[F]] = WorkbookInteractionGrader.universalSerializer.deriveForSubclass[WorkbookInteractionGrader[F]].uPickleReadWrite
*/

}
