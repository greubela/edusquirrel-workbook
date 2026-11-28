package it.evadid.workbook.abstractions.scaffolding

import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
trait WorkbookInteractionScaffolder[T, SA] {

  private def failWith(state: T, err: Either[String, Throwable]): Future[ScaffoldingResult[T, SA]] = Future.successful(ScaffoldingResult.forError(state, err, scaffoldingEntity))

  def generateFeedbackSafe(state: T, contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]] =
    try {
      handleFeedbackGeneration(state, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }(using contextInformation.futureExecutionContext)
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }


  def scaffoldingEntity: FeedbackEntity

  def handleFeedbackGeneration(currentState: T, contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]]


}
