package it.evadid.workbook.abstractions.scaffolding

import it.evadid.workbook.abstractions.grading.GradingResult
import it.evadid.workbook.history.InteractionState
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionFormat}
import it.evadid.workbook.abstractions.ExecutionContextInformation

import scala.concurrent.{ExecutionContext, Future}

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
/** What the history recorded for an answer that a hint is about: the latest answer and the results graded for it. */
final case class ScaffoldingEvidence[T, GA](latestAnswer: Option[InteractionState[T]], gradings: Seq[GradingResult[T, GA]])

object ScaffoldingEvidence {
  /** Reads the evidence of the current history; scaffolders read it again to notice changes during preparation. */
  type Reader[T, GA] = () => ScaffoldingEvidence[T, GA]

  def none[T, GA]: Reader[T, GA] = () => ScaffoldingEvidence(None, Nil)
}

trait WorkbookInteractionScaffolder[T, GA, SA] {

  private def failWith(state: T, err: Either[String, Throwable]): Future[ScaffoldingResult[T, SA]] = Future.successful(ScaffoldingResult.forError(state, err, scaffoldingEntity))

  def generateFeedbackSafe(state: T, evidence: ScaffoldingEvidence.Reader[T, GA], contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]] =
    try {
      handleFeedbackGeneration(state, evidence, contextInformation).recoverWith {
        case err: Throwable => failWith(state, Right(err))
      }(using contextInformation.futureExecutionContext)
    } catch {
      case (err: Throwable) => failWith(state, Right(err))
      case any: Any => failWith(state, Left(s"Unknown grading error (${any.toString})"))
    }


  def scaffoldingEntity: FeedbackEntity

  def handleFeedbackGeneration(currentState: T, evidence: ScaffoldingEvidence.Reader[T, GA], contextInformation: ExecutionContextInformation): Future[ScaffoldingResult[T, SA]]


}
