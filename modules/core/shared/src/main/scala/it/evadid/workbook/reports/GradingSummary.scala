package it.evadid.workbook.reports

import it.evadid.workbook.abstractions.grading.*
import GradingImportance.HIDDEN_TEST
import GradingStatus.*

/** Shared counts for current-answer feedback; only authored graders are displayed. */
object GradingSummary {
  enum Group {
    case Correct, Incorrect, Pending, Error, NotRequested
  }

  final case class Entry(index: Int, grader: WorkbookInteractionGrader[?, ?], result: Option[GradingResult[?, ?]]) {
    lazy val hidden: Boolean = grader.importance == HIDDEN_TEST
    lazy val group: Group = result.map(_.gradingStatus).getOrElse(NotRequested) match {
      case GradingStatus.Correct => Group.Correct
      case GradingStatus.Incorrect | PartiallyCorrect => Group.Incorrect
      case NotFinished => Group.Pending
      case TestError(_) => Group.Error
      case GradingStatus.NotRequested => Group.NotRequested
    }
    def executed: Boolean = group != Group.Pending && group != Group.NotRequested
  }

  def apply(graders: Seq[WorkbookInteractionGrader[?, ?]], state: GradedState[?, ?]): GradingSummary = {
    val results = state.graderResultMap.values.filter(_.gradedState == state.state).map(result => result.grader.key -> result).toMap
    GradingSummary(graders.distinctBy(_.key).zipWithIndex.map { (grader, index) =>
      Entry(index + 1, grader, results.get(grader.key))
    })
  }
}

final case class GradingSummary(entries: Seq[GradingSummary.Entry]) {
  import GradingSummary.Group
  private lazy val grouped = entries.groupBy(_.group)
  private lazy val visible = grouped.view.mapValues(_.filterNot(_.hidden)).toMap
  def in(group: Group): Seq[GradingSummary.Entry] = grouped.getOrElse(group, Seq.empty)
  def visibleIn(group: Group): Seq[GradingSummary.Entry] = visible.getOrElse(group, Seq.empty)
  def visibleCount(group: Group): Int = visibleIn(group).size
  def hiddenCount(group: Group): Int = in(group).size - visibleCount(group)
  lazy val correct: Int = in(Group.Correct).size
  lazy val total: Int = entries.size
  lazy val unavailable: Int = entries.count(entry => Set(Group.Pending, Group.Error, Group.NotRequested)(entry.group))
  lazy val pending: Boolean = in(Group.Pending).nonEmpty
  lazy val errors: Int = in(Group.Error).size
  lazy val notRequested: Int = in(Group.NotRequested).size
  lazy val unfinished: Int = in(Group.Pending).size
  lazy val retries: Int = entries.count(_.result.exists(_.gradingStatus.shouldRetryExecution))
  lazy val reliesOnLlm: Boolean = entries.exists(_.result.exists(_.grader.gradingEntity == it.evadid.workbook.abstractions.FeedbackEntity.LlmEntity))

  /** Authored tests, including missing results, determine the aggregate status. */
  lazy val status: GradingStatus =
    if total == 0 || notRequested > 0 then GradingStatus.NotRequested
    else if pending then NotFinished
    else if errors > 0 then TestError(it.evadid.distribution.command.SerializedException(s"There are $errors tests with error ($retries should be retries)"))
    else if correct == total then GradingStatus.Correct
    else if correct == 0 then GradingStatus.Incorrect
    else PartiallyCorrect

  def score: String = s"$correct / $total"
}
