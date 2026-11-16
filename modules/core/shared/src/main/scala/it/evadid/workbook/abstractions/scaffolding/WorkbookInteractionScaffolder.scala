package it.evadid.workbook.abstractions.scaffolding

import cats.mtl.syntax.state
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
trait WorkbookInteractionScaffolder[F <: InteractionFormat] {

  private def failWith(state: F#InteractionStateType, err: Either[String, Throwable]): Future[ScaffoldingResult[F]] = Future.successful(ScaffoldingResult.forError(state, err, scaffoldingEntity))

  def generateFeedbackSafe(state: F#InteractionStateType, contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[F]] =
    try {
      handleFeedbackGeneration(state, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }(using contextInformation.futureExecutionContext)
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }


  def scaffoldingEntity: FeedbackEntity

  def handleFeedbackGeneration(currentState: F#InteractionStateType, contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[F]]


}
