package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui

private[feedback] final class FeedbackSubmissionTracker:
  private var revision = 0L
  private var nextId = 0L
  private var active = Option.empty[FeedbackSubmissionTracker.Submission]

  def begin(): Option[FeedbackSubmissionTracker.Submission] =
    if active.nonEmpty then None
    else
      nextId += 1
      val submission = FeedbackSubmissionTracker.Submission(nextId, revision)
      active = Some(submission)
      active

  def invalidate(): Unit = revision += 1

  def finish(submission: FeedbackSubmissionTracker.Submission): Option[Boolean] =
    if !active.contains(submission) then None
    else
      active = None
      Some(submission.revision == revision)

  def dispose(): Unit =
    invalidate()
    active = None

private[feedback] object FeedbackSubmissionTracker:
  final case class Submission private[ui] (id: Long, revision: Long)
