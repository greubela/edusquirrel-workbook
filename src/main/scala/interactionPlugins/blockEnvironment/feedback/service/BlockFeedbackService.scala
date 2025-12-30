package interactionPlugins.blockEnvironment.feedback

import scala.concurrent.{ExecutionContext, Future}

/**
 * Central orchestration of the Python feedback pipeline.
 *
 * Responsibilities:
 *  - normalize the submitted Python source
 *  - derive a test plan from configuration and submission metadata
 *  - delegate runtime execution and build the user-facing feedback
 */
object BlockFeedbackService:

  def generateFeedback(
    request: BlockFeedbackRequest
  )(using ExecutionContext): Future[BlockFeedbackResult] =
    val testPlan = BlockFeedbackTestFactory.deriveTestPlan(request)
    val runtimeOutcomeFuture =
      if request.config.enableUnitTests then
        BlockFeedbackTestRunner.execute(request, testPlan)
      else
        Future.successful(PythonRuntimeOutcome.empty)

    runtimeOutcomeFuture.map { outcome =>
      BlockFeedbackFeedbackBuilder.buildFeedback(request, testPlan, outcome)
    }
