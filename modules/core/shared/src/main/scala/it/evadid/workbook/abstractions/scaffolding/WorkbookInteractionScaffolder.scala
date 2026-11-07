package it.evadid.workbook.abstractions.scaffolding

import cats.mtl.syntax.state
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.meta.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}

trait WorkbookInteractionScaffolder[T, A] {

  private def failWith(state: T, err: Either[String, Throwable]): Future[ScaffoldingResult[T, A]] = Future.successful(ScaffoldingResult.forError(state, err, scaffoldingEntity))

  def generateFeedbackSafe(state: T, contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, A]] =
    try {
      handleFeedbackGeneration(state, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }(using contextInformation.futureExecutionContext)
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }


  def scaffoldingEntity: FeedbackEntity

  def handleFeedbackGeneration(currentState: T, contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, A]]


}
